package com.example.cacheproject.domain.openapi.service;

import com.example.cacheproject.common.exception.BadRequestException;
import com.example.cacheproject.common.exception.DataIntegrityException;
import com.example.cacheproject.common.exception.OpenApiException;
import com.example.cacheproject.common.util.BatchInsertUtil;
import com.example.cacheproject.common.util.DataConsistencyUtil;
import com.example.cacheproject.domain.openapi.entity.OpenApi;
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

import java.util.ArrayList;
import java.util.List;

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
    public void 설정() {
        ReflectionTestUtils.setField(openApiService, "openApiUrl", "http://example.com/api");
    }

    @Test
    public void OpenAPI_데이터_정상_가져오기_및_저장() {
        // 기존 마지막 가져온 데이터 설정
        OpenApiFetchStatus mockLastFetchStatus = new OpenApiFetchStatus();
        mockLastFetchStatus.setLastFetchedRow(0);
        when(openApiFetchStatusRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(mockLastFetchStatus);

        // Mock XML 응답 데이터
        String mockXmlResponse =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<ServiceInternetShopInfo>" +
                        "    <row>" +
                        "        <id>1</id>" +
                        "        <name>Test Shop 1</name>" +
                        "    </row>" +
                        "    <row>" +
                        "        <id>2</id>" +
                        "        <name>Test Shop 2</name>" +
                        "    </row>" +
                        "</ServiceInternetShopInfo>";

        ResponseEntity<String> mockResponseEntity = ResponseEntity.ok(mockXmlResponse);
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(mockResponseEntity);

        // 데이터 일관성 체크용 Mock 데이터
        List<OpenApi> mockOpenApiList = new ArrayList<>();
        OpenApi mockOpenApi1 = new OpenApi();
        mockOpenApi1.setId(1L);
        OpenApi mockOpenApi2 = new OpenApi();
        mockOpenApi2.setId(2L);
        mockOpenApiList.add(mockOpenApi1);
        mockOpenApiList.add(mockOpenApi2);

        when(dataConsistencyUtil.checkOpenApiDataConsistency(any())).thenReturn(true);

        try (MockedStatic<BatchInsertUtil> batchInsertUtilMockedStatic = Mockito.mockStatic(BatchInsertUtil.class)) {
            // 실행
            String result = openApiService.fetchAndSaveOpenApiData();

            // 검증
            assertTrue(result.contains("개의 OpenAPI 데이터가 성공적으로 삽입되었습니다."));
            verify(openApiFetchStatusRepository, atLeastOnce()).save(any(OpenApiFetchStatus.class));
        }
    }

    @Test
    public void OpenAPI_빈_응답_처리() {
        when(openApiFetchStatusRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(null);
        ResponseEntity<String> mockResponseEntity = ResponseEntity.ok("");
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(mockResponseEntity);

        assertThrows(BadRequestException.class, () -> {
            openApiService.fetchAndSaveOpenApiData();
        });
    }

    @Test
    public void OpenAPI_호출_실패_예외_처리() {
        when(openApiFetchStatusRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(null);
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenThrow(new RuntimeException("API Error"));

        assertThrows(OpenApiException.class, () -> {
            openApiService.fetchAndSaveOpenApiData();
        });
    }

    @Test
    public void OpenAPI_데이터_무결성_검사_실패() {
        when(openApiFetchStatusRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(null);

        // Mock XML 응답 response
        String mockXmlResponse =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<ServiceInternetShopInfo>" +
                        "    <row>" +
                        "        <id>1</id>" +
                        "        <name>Test Shop 1</name>" +
                        "    </row>" +
                        "    <row>" +
                        "        <id>2</id>" +
                        "        <name>Test Shop 2</name>" +
                        "    </row>" +
                        "</ServiceInternetShopInfo>";

        ResponseEntity<String> mockResponseEntity = ResponseEntity.ok(mockXmlResponse);
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(mockResponseEntity);
        when(dataConsistencyUtil.checkOpenApiDataConsistency(any())).thenReturn(false);

        assertThrows(DataIntegrityException.class, () -> {
            try (MockedStatic<BatchInsertUtil> batchInsertUtilMockedStatic = Mockito.mockStatic(BatchInsertUtil.class)) {
                openApiService.fetchAndSaveOpenApiData();
            }
        });
    }
}
