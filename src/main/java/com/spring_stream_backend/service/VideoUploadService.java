package com.spring_stream_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class VideoUploadService {

    @Value("${video.storage.location}")
    private String videoStorageLocation;

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("video/mp4", "video/mkv");

    public String saveVideoToLocal(MultipartFile file) {
        validateFile(file);

        try {
            // Construct the path relative to the project root
            Path videoDirPath = Paths.get(videoStorageLocation);
            System.out.println("video dir path :"+videoDirPath);
            // Ensure the directory exists
            if (!Files.exists(videoDirPath)) {
                Files.createDirectories(videoDirPath);
            }

            // Generate a unique filename
            String originalFileName = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFileName != null && originalFileName.contains(".")) {
                fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }
            String uniqueFileName = UUID.randomUUID().toString() + fileExtension;

            // Create the file path with unique filename
            Path videoFilePath = videoDirPath.resolve(uniqueFileName);
            System.out.println("video file path :"+videoFilePath);
            // Copy the file
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, videoFilePath, StandardCopyOption.REPLACE_EXISTING);
            }

            return "File uploaded successfully to: " + videoFilePath.toAbsolutePath();

        } catch (IOException e) {
            throw new RuntimeException("Failed to save file: " + e.getMessage(), e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty.");
        }

        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Unsupported file type: " + file.getContentType());
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds the limit of 50 MB.");
        }
    }
}