package com.example.cacheproject.domain.collection.service;

import com.example.cacheproject.common.exception.DataIntegrityException;
import com.example.cacheproject.common.util.BatchInsertUtil;
import com.example.cacheproject.common.util.CsvReaderUtil;
import com.example.cacheproject.common.util.DataConsistencyUtil;
import com.example.cacheproject.domain.collection.entity.CsvData;
import com.example.cacheproject.domain.collection.fetchstatus.entity.CsvDataFetchStatus;
import com.example.cacheproject.domain.collection.fetchstatus.repository.CsvDataFetchStatusRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CsvDataServiceTest {

    @Mock
    private CsvReaderUtil csvReaderUtil;

    @Mock
    private DataConsistencyUtil dataConsistencyUtil;

    @Mock
    private CsvDataFetchStatusRepository csvDataFetchStatusRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private CsvService csvService;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(csvService, "filePath", "/test/path/data.csv");
    }

    @Test
    public void testReadCsvAndSaveToDatabaseInBatch_SuccessfulImport() {
        // Arrange
        List<CsvData> mockCsvData = createMockCsvData(150);
        when(csvReaderUtil.readCsv(anyString())).thenReturn(mockCsvData);

        // Mock fetch status
        CsvDataFetchStatus mockFetchStatus = new CsvDataFetchStatus();
        mockFetchStatus.setLastFetchedRow(0);
        when(csvDataFetchStatusRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(mockFetchStatus);

        // Mock data consistency check
        when(dataConsistencyUtil.checkCsvDataConsistency(any())).thenReturn(true);

        // Act
        try (MockedStatic<BatchInsertUtil> batchInsertUtilMockedStatic = Mockito.mockStatic(BatchInsertUtil.class)) {
            String result = csvService.readCsvAndSaveToDatabaseInBatch();

            // Assert
            assertTrue(result.contains("개의 CSV 데이터가 성공적으로 삽입되었습니다."));
            verify(csvDataFetchStatusRepository, atLeastOnce()).save(any(CsvDataFetchStatus.class));
        }
    }

    @Test
    public void testReadCsvAndSaveToDatabaseInBatch_DataIntegrityCheckFailed() {
        // Arrange
        List<CsvData> mockCsvData = createMockCsvData(150);
        when(csvReaderUtil.readCsv(anyString())).thenReturn(mockCsvData);

        // Mock fetch status
        CsvDataFetchStatus mockFetchStatus = new CsvDataFetchStatus();
        mockFetchStatus.setLastFetchedRow(0);
        when(csvDataFetchStatusRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(mockFetchStatus);

        // Mock data consistency check to fail
        when(dataConsistencyUtil.checkCsvDataConsistency(any())).thenReturn(false);

        // Act & Assert
        assertThrows(DataIntegrityException.class, () -> {
            try (MockedStatic<BatchInsertUtil> batchInsertUtilMockedStatic = Mockito.mockStatic(BatchInsertUtil.class)) {
                csvService.readCsvAndSaveToDatabaseInBatch();
            }
        });
    }

    // Helper method to create mock CSV data
    private List<CsvData> createMockCsvData(int count) {
        List<CsvData> mockData = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CsvData data = mock(CsvData.class);
            mockData.add(data);
        }
        return mockData;
    }
}