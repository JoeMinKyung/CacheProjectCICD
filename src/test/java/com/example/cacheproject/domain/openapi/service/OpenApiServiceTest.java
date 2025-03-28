package com.example.cacheproject.domain.openapi.service;

import com.example.cacheproject.common.exception.BadRequestException;
import com.example.cacheproject.common.exception.DataIntegrityException;
import com.example.cacheproject.common.exception.OpenApiException;
import com.example.cacheproject.common.util.BatchInsertUtil;
import com.example.cacheproject.common.util.DataConsistencyUtil;
import com.example.cacheproject.domain.openapi.fetchstatus.entity.OpenApiFetchStatus;
import com.example.cacheproject.domain.openapi.fetchstatus.repository.OpenApiFetchStatusRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OpenApiServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private OpenApiFetchStatusRepository openApiFetchStatusRepository;

    @Mock
    private DataConsistencyUtil dataConsistencyUtil;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private OpenApiService openApiService;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(openApiService, "openApiUrl", "http://example.com/api");
    }

    @Test
    public void testFetchAndSaveOpenApiData_SuccessfulFetch() {
        // Arrange
        OpenApiFetchStatus mockLastFetchStatus = new OpenApiFetchStatus();
        mockLastFetchStatus.setLastFetchedRow(0);
        when(openApiFetchStatusRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(mockLastFetchStatus);

        // Prepare mock XML response
        String mockXmlResponse = "<response><rows><row>Test Data 1</row><row>Test Data 2</row></rows></response>";
        ResponseEntity<String> mockResponseEntity = ResponseEntity.ok(mockXmlResponse);
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(mockResponseEntity);

        // Mock data consistency check
        when(dataConsistencyUtil.checkOpenApiDataConsistency(any())).thenReturn(true);

        // Mock batch insert
        try (MockedStatic<BatchInsertUtil> batchInsertUtilMockedStatic = Mockito.mockStatic(BatchInsertUtil.class)) {
            // Act
            String result = openApiService.fetchAndSaveOpenApiData();

            // Assert
            assertTrue(result.contains("개의 OpenAPI 데이터가 성공적으로 삽입되었습니다."));
            verify(openApiFetchStatusRepository, atLeastOnce()).save(any(OpenApiFetchStatus.class));
        }
    }

    @Test
    public void testFetchAndSaveOpenApiData_EmptyResponse() {
        // Arrange
        when(openApiFetchStatusRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(null);

        // Prepare empty response
        ResponseEntity<String> mockResponseEntity = ResponseEntity.ok("");
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(mockResponseEntity);

        // Act & Assert
        assertThrows(BadRequestException.class, () -> {
            openApiService.fetchAndSaveOpenApiData();
        });
    }

    @Test
    public void testFetchAndSaveOpenApiData_ApiCallFailed() {
        // Arrange
        when(openApiFetchStatusRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(null);
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenThrow(new RuntimeException("API Error"));

        // Act & Assert
        assertThrows(OpenApiException.class, () -> {
            openApiService.fetchAndSaveOpenApiData();
        });
    }

    @Test
    public void testFetchAndSaveOpenApiData_DataIntegrityCheckFailed() {
        // Arrange
        when(openApiFetchStatusRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(null);

        // Prepare mock XML response
        String mockXmlResponse = "<response><rows><row>Test Data 1</row><row>Test Data 2</row></rows></response>";
        ResponseEntity<String> mockResponseEntity = ResponseEntity.ok(mockXmlResponse);
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(mockResponseEntity);

        // Mock data consistency check to fail
        when(dataConsistencyUtil.checkOpenApiDataConsistency(any())).thenReturn(false);

        // Act & Assert
        assertThrows(DataIntegrityException.class, () -> {
            try (MockedStatic<BatchInsertUtil> batchInsertUtilMockedStatic = Mockito.mockStatic(BatchInsertUtil.class)) {
                openApiService.fetchAndSaveOpenApiData();
            }
        });
    }
}