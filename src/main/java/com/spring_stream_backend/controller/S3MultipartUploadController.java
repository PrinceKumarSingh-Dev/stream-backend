package com.spring_stream_backend.controller;


import com.spring_stream_backend.service.S3MultipartUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@RestController
@RequestMapping("/api/upload")
public class S3MultipartUploadController {

    private final S3MultipartUploadService s3MultipartUploadService;

    @Autowired
    public S3MultipartUploadController(S3MultipartUploadService s3MultipartUploadService) {
        this.s3MultipartUploadService = s3MultipartUploadService;
    }

    @PostMapping("/video")
    public ResponseEntity<String> uploadVideo(MultipartFile file) {
        try {
            // Save the uploaded file temporarily
            Path tempFile = Files.createTempFile("upload-", file.getOriginalFilename());
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            // Upload file to S3
            String keyName = "videos/" + file.getOriginalFilename();
            String result = s3MultipartUploadService.uploadLargeFile(keyName, tempFile);

            // Delete temporary file
            Files.delete(tempFile);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to upload video: " + e.getMessage());
        }
    }
}
