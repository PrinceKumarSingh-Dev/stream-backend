package com.spring_stream_backend.controller;

import com.spring_stream_backend.service.FileChunkService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileChunkControllerTest {
    @Mock
    private FileChunkService fileChunkService = mock(FileChunkService.class);
    @InjectMocks
    private FileChunkController fileChunkController = new FileChunkController(fileChunkService);

    @Test
    void uploadAndChunkFile_SuccessfulProcessing_ReturnsOkResponse() throws Exception {
        // Arrange
        MultipartFile mockFile = mock(MultipartFile.class);
        doNothing().when(fileChunkService).processAndChunkFile(mockFile);

        // Act
        ResponseEntity<String> response = fileChunkController.uploadAndChunkFile(mockFile);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("File successfully split into chunks and saved.", response.getBody());
        verify(fileChunkService, times(1)).processAndChunkFile(mockFile);
    }

    @Test
    void uploadAndChunkFile_IllegalArgumentException_ReturnsBadRequestResponse() throws Exception {
        // Arrange
        MultipartFile mockFile = mock(MultipartFile.class);
        String errorMessage = "Invalid file format";
        doThrow(new IllegalArgumentException(errorMessage)).when(fileChunkService).processAndChunkFile(mockFile);

        // Act
        ResponseEntity<String> response = fileChunkController.uploadAndChunkFile(mockFile);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(errorMessage, response.getBody());
        verify(fileChunkService, times(1)).processAndChunkFile(mockFile);
    }

    @Test
    void uploadAndChunkFile_GeneralException_ReturnsInternalServerErrorResponse() throws Exception {
        // Arrange
        MultipartFile mockFile = mock(MultipartFile.class);
        String errorMessage = "Unexpected error occurred";
        doThrow(new RuntimeException(errorMessage)).when(fileChunkService).processAndChunkFile(mockFile);

        // Act
        ResponseEntity<String> response = fileChunkController.uploadAndChunkFile(mockFile);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Error while processing file: " + errorMessage, response.getBody());
        verify(fileChunkService, times(1)).processAndChunkFile(mockFile);
    }

    @Test
    void uploadAndChunkFile_EmptyFile_ReturnsBadRequestResponse() throws Exception {
        // Arrange
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(true);
        doThrow(new IllegalArgumentException("Empty file cannot be processed")).when(fileChunkService).processAndChunkFile(mockFile);

        // Act
        ResponseEntity<String> response = fileChunkController.uploadAndChunkFile(mockFile);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Empty file cannot be processed", response.getBody());
        verify(fileChunkService, times(1)).processAndChunkFile(mockFile);
    }

    @Test
    void uploadAndChunkFile_LargeFileWithinSizeLimit_ReturnsOkResponse() throws Exception {
        // Arrange
        MultipartFile mockFile = mock(MultipartFile.class);
        long fileSizeInBytes = 100 * 1024 * 1024; // 100 MB
        when(mockFile.getSize()).thenReturn(fileSizeInBytes);
        doNothing().when(fileChunkService).processAndChunkFile(mockFile);

        // Act
        ResponseEntity<String> response = fileChunkController.uploadAndChunkFile(mockFile);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("File successfully split into chunks and saved.", response.getBody());
        verify(fileChunkService, times(1)).processAndChunkFile(mockFile);
    }

    @Test
    void uploadAndChunkFile_UnsupportedFileType_ReturnsBadRequestResponse() throws Exception {
        // Arrange
        MultipartFile mockFile = mock(MultipartFile.class);
        String errorMessage = "Unsupported file type";
        doThrow(new IllegalArgumentException(errorMessage)).when(fileChunkService).processAndChunkFile(mockFile);

        // Act
        ResponseEntity<String> response = fileChunkController.uploadAndChunkFile(mockFile);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(errorMessage, response.getBody());
        verify(fileChunkService, times(1)).processAndChunkFile(mockFile);
    }

    @Test
    void uploadAndChunkFile_VerifyServiceMethodCalled() throws Exception {
        // Arrange
        MultipartFile mockFile = mock(MultipartFile.class);

        // Act
        fileChunkController.uploadAndChunkFile(mockFile);

        // Assert
        verify(fileChunkService, times(1)).processAndChunkFile(mockFile);
    }

    @Test
    void uploadAndChunkFile_ConcurrentUploads_HandlesCorrectly() throws Exception {
        // Arrange
        int concurrentUploads = 5;
        CountDownLatch latch = new CountDownLatch(concurrentUploads);
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentUploads);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();

        // Act
        for (int i = 0; i < concurrentUploads; i++) {
            futures.add(executorService.submit(() -> {
                MultipartFile mockFile = mock(MultipartFile.class);
                ResponseEntity<String> response = fileChunkController.uploadAndChunkFile(mockFile);
                latch.countDown();
                return response;
            }));
        }

        latch.await(5, TimeUnit.SECONDS);

        // Assert
        for (Future<ResponseEntity<String>> future : futures) {
            ResponseEntity<String> response = future.get();
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("File successfully split into chunks and saved.", response.getBody());
        }

        verify(fileChunkService, times(concurrentUploads)).processAndChunkFile(any(MultipartFile.class));
    }

    @Test
    void uploadAndChunkFile_NetworkIssue_ReturnsInternalServerErrorResponse() throws Exception {
        // Arrange
        MultipartFile mockFile = mock(MultipartFile.class);
        String errorMessage = "Network connection interrupted";
        doThrow(new IOException(errorMessage)).when(fileChunkService).processAndChunkFile(mockFile);

        // Act
        ResponseEntity<String> response = fileChunkController.uploadAndChunkFile(mockFile);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Error while processing file: " + errorMessage, response.getBody());
        verify(fileChunkService, times(1)).processAndChunkFile(mockFile);
    }
}