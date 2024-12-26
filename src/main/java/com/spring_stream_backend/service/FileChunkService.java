package com.spring_stream_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.logging.Logger;

@Service
public class FileChunkService {

    @Value("${file.chunk.storage.location}")
    private String chunkStorageLocation;

    private static final int CHUNK_SIZE = 1024 * 1024; // 1 MB
    private static final Logger LOGGER = Logger.getLogger(FileChunkService.class.getName());

    public void processAndChunkFile(MultipartFile file) throws IOException {
        LOGGER.info("Processing file for chunking: " + file.getOriginalFilename());

        // Ensure the chunk storage directory exists
        ensureDirectoryExists(chunkStorageLocation);

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("Invalid file name.");
        }

        // Save the uploaded file temporarily
        File tempFile = new File(chunkStorageLocation, "temp_" + fileName);
        try {
            file.transferTo(tempFile);
            if (!tempFile.exists()) {
                throw new IOException("Temporary file was not created: " + tempFile.getAbsolutePath());
            }
            LOGGER.info("Temporary file created: " + tempFile.getAbsolutePath());

            // Chunk the file
            chunkFile(tempFile, chunkStorageLocation);
            LOGGER.info("File chunking completed successfully for: " + fileName);
        } catch (IOException e) {
            LOGGER.severe("Error while processing file: " + e.getMessage());
            throw e; // Rethrow for further handling
        } finally {
            // Delete the temporary file
            if (tempFile.exists() && !tempFile.delete()) {
                LOGGER.warning("Failed to delete temporary file: " + tempFile.getAbsolutePath());
            }
        }
    }

    private void chunkFile(File sourceFile, String targetDirectoryPath) throws IOException {
        ensureDirectoryExists(targetDirectoryPath);

        try (FileChannel sourceChannel = new FileInputStream(sourceFile).getChannel()) {
            long fileSize = sourceChannel.size();
            long position = 0;
            int chunkIndex = 1; // Start index at 1

            while (position < fileSize) {
                long remaining = fileSize - position;
                long chunkSize = Math.min(remaining, CHUNK_SIZE);

                // Create chunk file name as "chunk1", "chunk2", etc.
                File chunkFile = new File(targetDirectoryPath, "chunk" + chunkIndex);
                try (FileChannel chunkChannel = new FileOutputStream(chunkFile).getChannel()) {
                    sourceChannel.transferTo(position, chunkSize, chunkChannel);
                    LOGGER.info("Created chunk: " + chunkFile.getAbsolutePath());
                }

                position += chunkSize;
                chunkIndex++;
            }
        }
    }

    private void ensureDirectoryExists(String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            boolean isCreated = directory.mkdirs();
            if (!isCreated) {
                throw new IOException("Failed to create directory: " + directoryPath);
            }
            LOGGER.info("Directory created: " + directoryPath);
        } else {
            LOGGER.info("Directory already exists: " + directoryPath);
        }
    }
}
