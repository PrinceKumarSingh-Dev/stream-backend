package com.spring_stream_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class VideoUploadService {

    @Value("${video.storage.location}")
    private String videoStorageLocation;

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("video/mp4", "video/mkv", "video/x-matroska", "application/octet-stream");
    private static final List<String> ALLOWED_EXTENSIONS = List.of(".mp4", ".mkv", ".avi", ".mov", ".flv", ".wmv", ".webm", "");
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB
    private static final long MAX_CHUNK_SIZE = 20 * 1024 * 1024; // 20 MB per chunk


    public String saveVideoToLocal(MultipartFile file) {
        validateFile(file);

        try {
            // Construct the path relative to the project root
            Path videoDirPath = Paths.get(videoStorageLocation);
            System.out.println("video dir path :" + videoDirPath);
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
            System.out.println("video file path :" + videoFilePath);
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

    //    ------------------------------------------------ chunk upload------------------------------------------------------
    public String saveVideoChunk(MultipartFile file, int chunkIndex, int totalChunks, String fileName) {
        validateFile(file, chunkIndex);

        try {
            // Create the directory if it does not exist
            Path directoryPath = Paths.get(videoStorageLocation, fileName);
            if (Files.notExists(directoryPath)) {
                Files.createDirectories(directoryPath);
            }
            if (chunkIndex > totalChunks) {
                throw new RuntimeException("chunkIndex can not be more then total chunks");
            }

            // Define chunk file path
            Path chunkFilePath = directoryPath.resolve("chunk-" + chunkIndex);
            Files.write(chunkFilePath, file.getBytes(), StandardOpenOption.CREATE);

            return "Chunk " + chunkIndex + " of " + totalChunks + " uploaded successfully.";
        } catch (IOException e) {
            throw new RuntimeException("Failed to save chunk: " + e.getMessage(), e);
        }
    }
//------------------------------------------merge chunks -----------------------------------------------------------

    public String mergeChunks(String fileName) {
        Path directoryPath = Paths.get(videoStorageLocation, fileName);
        if (Files.notExists(directoryPath) || !Files.isDirectory(directoryPath)) {
            throw new RuntimeException("Directory not found for file: " + fileName);
        }

        Path mergedFilePath = Paths.get(videoStorageLocation, fileName + ".mp4");
        try (FileChannel outputChannel = FileChannel.open(mergedFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            // List all chunks in the directory and sort them by chunk index
            File[] chunks = directoryPath.toFile().listFiles();
            if (chunks == null || chunks.length == 0) {
                throw new RuntimeException("No chunks found for file: " + fileName);
            }

            Arrays.sort(chunks, Comparator.comparingInt(chunk -> Integer.parseInt(chunk.getName().split("-")[1])));

            // Write each chunk to the merged file
            for (File chunk : chunks) {
                Path chunkPath = chunk.toPath();
                try (FileChannel inputChannel = FileChannel.open(chunkPath, StandardOpenOption.READ)) {
                    long chunkSize = inputChannel.size();
                    long position = 0;

                    while (position < chunkSize) {
                        position += inputChannel.transferTo(position, chunkSize - position, outputChannel);
                    }
                }

                // Delete the chunk after merging
                Files.delete(chunk.toPath());
            }

            // Delete the directory after merging
            Files.delete(directoryPath);

            return "File upload completed successfully: " + mergedFilePath.toAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("Failed to merge chunks: " + e.getMessage(), e);
        }
    }


    private void validateFile(MultipartFile file, int chunkIndex) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Chunk " + chunkIndex + " is empty.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || ALLOWED_EXTENSIONS.stream().noneMatch(originalFilename.toLowerCase()::endsWith)) {
            throw new IllegalArgumentException("Unsupported file extension: " + originalFilename);
        }

        if (file.getSize() > MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException("Chunk size exceeds the limit of 20 MB.");
        }
    }

}