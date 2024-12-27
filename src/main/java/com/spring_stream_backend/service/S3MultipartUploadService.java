package com.spring_stream_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service // Marks this class as a Spring service, making it available for dependency injection.
public class S3MultipartUploadService {

    private final S3AsyncClient s3AsyncClient; // Asynchronous S3 client for performing operations.
    private final String bucketName; // Stores the name of the S3 bucket.

    // Constructor to initialize the S3 client and bucket name using Spring's @Value annotation.
    public S3MultipartUploadService(@Value("${aws.s3.bucket-name}") String bucketName,
                                    @Value("${aws.s3.region}") String region) {
        this.bucketName = bucketName; // Assign the bucket name from application properties.

        // Initialize the S3 client with the region and default credentials provider.
        this.s3AsyncClient = S3AsyncClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Uploads a large file to the S3 bucket using multipart upload.
     * @param keyName The key (path) under which the file will be stored in the bucket.
     * @param filePath The path of the file to be uploaded.
     * @return A success message with the S3 key name.
     */
    public String uploadLargeFile(String keyName, Path filePath) throws Exception {
        // Step 1: Initiate the multipart upload request.
        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName) // Specify the bucket name.
                .key(keyName) // Specify the object key (S3 path).
                .build();

        // Step 2: Create the multipart upload session and retrieve the upload ID.
        CreateMultipartUploadResponse createResponse = s3AsyncClient.createMultipartUpload(createRequest).join();
        String uploadId = createResponse.uploadId(); // Store the upload ID for subsequent operations.

        // Step 3: Prepare a list to store information about each uploaded part.
        List<CompletedPart> completedParts = new ArrayList<>();
        long partSize = 5 * 1024 * 1024; // Define the part size (5 MB).
        long fileSize = java.nio.file.Files.size(filePath); // Get the size of the file to upload.

        // Step 4: Loop through the file and upload it in parts.
        for (int partNumber = 1; partSize * (partNumber - 1) < fileSize; partNumber++) {
            long start = partSize * (partNumber - 1); // Calculate the starting byte of the current part.
            long remainingBytes = fileSize - start; // Calculate the remaining bytes to upload.
            long size = Math.min(partSize, remainingBytes); // Determine the size of the current part.

            // Step 5: Read the current part of the file into a byte array.
            byte[] buffer = readPart(filePath, start, size);

            // Step 6: Create the upload request for the current part.
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucketName) // Specify the bucket name.
                    .key(keyName) // Specify the object key.
                    .uploadId(uploadId) // Include the multipart upload ID.
                    .partNumber(partNumber) // Specify the part number.
                    .contentLength(size) // Specify the size of the part.
                    .build();

            // Step 7: Upload the part to S3 and retrieve its response.
            UploadPartResponse uploadPartResponse = s3AsyncClient
                    .uploadPart(uploadPartRequest, AsyncRequestBody.fromBytes(buffer))
                    .join();

            // Step 8: Add the uploaded part's information to the completed parts list.
            completedParts.add(CompletedPart.builder()
                    .partNumber(partNumber) // Specify the part number.
                    .eTag(uploadPartResponse.eTag()) // Add the part's ETag.
                    .build());
        }

        // Step 9: Prepare the completed multipart upload request.
        CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                .parts(completedParts) // Include all the uploaded parts.
                .build();

        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName) // Specify the bucket name.
                .key(keyName) // Specify the object key.
                .uploadId(uploadId) // Include the multipart upload ID.
                .multipartUpload(completedMultipartUpload) // Attach the completed parts.
                .build();

        // Step 10: Complete the multipart upload in S3.
        s3AsyncClient.completeMultipartUpload(completeRequest).join();

        // Step 11: Return a success message with the key name.
        return "File uploaded successfully with key: " + keyName;
    }

    /**
     * Reads a specific part of the file into a byte array.
     * @param filePath The path of the file to read.
     * @param start The starting byte position.
     * @param size The number of bytes to read.
     * @return A byte array containing the part data.
     * @throws IOException If an error occurs while reading the file.
     */
    private byte[] readPart(Path filePath, long start, long size) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r")) {
            byte[] buffer = new byte[(int) size]; // Create a buffer to hold the part data.
            file.seek(start); // Move the file pointer to the start position.
            file.readFully(buffer); // Read the specified number of bytes into the buffer.
            return buffer; // Return the buffer.
        }
    }
}
