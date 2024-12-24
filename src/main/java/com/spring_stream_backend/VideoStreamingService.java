package com.spring_stream_backend;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class VideoStreamingService {

    private static final String VIDEO_DIRECTORY = "videos"; // Video directory
    private static final int BUFFER_SIZE_IO = 1024 * 8; // 8KB buffer
    // 1MB chunks

    private final ResourceLoader resourceLoader;

    public VideoStreamingService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
//--------------------------------------------------------------streamVideoIo--------------------------------------------------------

    public ResponseEntity<byte[]> streamVideoIo(String fileName, String rangeHeader, HttpServletResponse response) throws IOException {
        // Sanitize and validate the file name
        fileName = StringUtils.cleanPath(fileName);
        ClassPathResource videoResource = new ClassPathResource(VIDEO_DIRECTORY + "/" + fileName);

        if (!videoResource.exists() || !videoResource.isFile()) {
            System.out.println("File not found: " + videoResource.getPath());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
//try to use nio instead
        try (RandomAccessFile videoFile = new RandomAccessFile(videoResource.getFile(), "r")) {
            long fileLength = videoFile.length();
            long[] range = parseRangeHeaderIo(rangeHeader, fileLength);

            long start = range[0];
            long end = range[1];
            long chunkSize = end - start + 1;

            byte[] data = new byte[BUFFER_SIZE_IO];
            videoFile.seek(start);

            // Set response headers for streaming
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("video/x-matroska"));
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength);
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            // Set status for partial content
            response.setHeader(HttpHeaders.CONTENT_TYPE, "video/x-matroska");
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength);
            response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");

            // Stream the video in chunks
            try (ServletOutputStream out = response.getOutputStream()) {
                long bytesRead = 0;
                while (bytesRead < chunkSize) {
                    int bytesToRead = (int) Math.min(BUFFER_SIZE_IO, chunkSize - bytesRead);
                    int read = videoFile.read(data, 0, bytesToRead);
                    if (read == -1) break;
                    out.write(data, 0, read);
                    bytesRead += read;
                }
            }

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).build();

        } catch (IOException e) {
            System.out.println("Error reading video file: " + videoResource.getPath());
            throw new RuntimeException("Error reading video file", e);
        }
    }

    private long[] parseRangeHeaderIo(String rangeHeader, long fileLength) {
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            System.out.println("Range header is null or invalid. Returning full file range.");
            return new long[]{0, fileLength - 1}; // Default range: full file
        }

        try {
            String[] ranges = rangeHeader.substring(6).split("-");
            long start = Long.parseLong(ranges[0]);
            long end = ranges.length > 1 && !ranges[1].isEmpty() ? Long.parseLong(ranges[1]) : fileLength - 1;

            if (start >= fileLength || start < 0 || end < start) {
                throw new IllegalArgumentException("Invalid range values");
            }

            return new long[]{start, Math.min(end, fileLength - 1)};
        } catch (Exception e) {
            System.out.println("Invalid Range Header: " + rangeHeader + ". Returning full file range.");
            return new long[]{0, fileLength - 1}; // Default to full range in case of error
        }
    }
//    ---------------------------------------------------------streamVideoIo end ------------------------------------------------

    //------------------------------------------------NIO----------------------------------------------------------------


    public ResponseEntity<Resource> streamVideoNio(String fileName, String rangeHeader) {
        // Load the video file from the classpath
        ClassPathResource videoResource = new ClassPathResource(VIDEO_DIRECTORY + "/" + fileName);
        if (!videoResource.exists()) {
            System.err.println("File not found: " + videoResource.getPath());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        long fileSize;
        try {
            fileSize = videoResource.contentLength();
            System.out.println("File Size: " + fileSize);
        } catch (IOException e) {
            // Log the error with proper message
            System.err.println("Error getting file size: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }

        // Parse Range header if present, or set the entire file as default range
        List<HttpRange> ranges = (rangeHeader != null) ? parseHttpRanges(rangeHeader) : null;
        HttpRange range = (ranges != null && !ranges.isEmpty()) ? ranges.get(0) : HttpRange.createByteRange(0, fileSize - 1);
        System.out.println("Requested Range: " + range);

        long start = range.getRangeStart(fileSize);
        long end = range.getRangeEnd(fileSize);
        long chunkSize = Math.min(end - start + 1, 1024 * 1024); // Read in 1 MB chunks
        System.out.println("Start Byte: " + start + " End Byte: " + end + " Chunk Size: " + chunkSize);

        try (FileChannel fileChannel = FileChannel.open(videoResource.getFile().toPath(), StandardOpenOption.READ)) {
            // Ensure the fileChannel is positioned at the start byte
            ByteBuffer buffer = ByteBuffer.allocate((int) chunkSize);
            fileChannel.position(start);
            int bytesRead = fileChannel.read(buffer);
            if (bytesRead == -1) {
                System.err.println("End of file reached unexpectedly");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
            buffer.flip();

            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            System.out.println("Date" + data);
            // Return the video chunk as a Resource
            Resource resource = new ByteArrayResource(data);
            System.out.println("resource" + resource);

            HttpHeaders headers = new HttpHeaders();
            String mimeType = getMimeType(videoResource.getFile().toPath());
            System.out.println("MIME Type: " + mimeType);
            headers.set(HttpHeaders.CONTENT_TYPE, mimeType); // Set dynamic MIME type
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize);
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            headers.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(chunkSize)); // Set Content-Length
            Resource resource1 = resource;
            System.out.println("resource1 :" + resource1);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(resource);
        } catch (IOException e) {
            // Log the error with a message
            System.err.println("Error reading video chunk: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // Utility method to parse the Range header
    private List<HttpRange> parseHttpRanges(String rangeHeader) {
        return Arrays.stream(rangeHeader.split(",")).map(this::parseHttpRange).collect(Collectors.toList());
    }

    // Convert a single range string to HttpRange
    private HttpRange parseHttpRange(String range) {
        if (range.startsWith("bytes=")) {
            String[] parts = range.substring(6).split("-");
            long start = Long.parseLong(parts[0]);
            long end = (parts.length > 1) ? Long.parseLong(parts[1]) : start;
            return HttpRange.createByteRange(start, end);
        }
        throw new IllegalArgumentException("Invalid range format");
    }

    // Utility method to get MIME type based on file extension
    private String getMimeType(Path filePath) {
        String mimeType = null;
        try {
            mimeType = Files.probeContentType(filePath); // Using java.nio.file.Files to get MIME type
            if (mimeType == null) {
                // If no MIME type is found, you can manually set based on file extension or use a library
                String fileName = filePath.getFileName().toString().toLowerCase();
                if (fileName.endsWith(".mp4")) {
                    mimeType = MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE; // video/mp4
                } else if (fileName.endsWith(".mkv")) {
                    mimeType = "video/x-matroska"; // video/mkv
                } else if (fileName.endsWith(".avi")) {
                    mimeType = "video/x-msvideo"; // video/avi
                } else if (fileName.endsWith(".mov")) {
                    mimeType = "video/quicktime"; // video/quicktime
                } else if (fileName.endsWith(".webm")) {
                    mimeType = "video/webm"; // video/webm
                } else {
                    mimeType = "application/octet-stream"; // Default if unknown format
                }
            }
        } catch (IOException e) {
            // Handle MIME type detection failure gracefully
            mimeType = "application/octet-stream";
        }
        return mimeType;
    }
//    -----------------------------------------------------nio end----------------------------------------------------------------------
//    ------------------------------Resource Region --------best to use in current time ------------------------------------

    /**
     * Streams the requested video file by calculating the appropriate resource region.
     *
     * @param videoName The name of the video file.
     * @param headers   HTTP headers containing range requests.
     * @return ResponseEntity with the requested video region.
     */
    public ResponseEntity<ResourceRegion> streamVideo(String videoName, HttpHeaders headers) {
        // Validate the video name
        if (videoName == null || videoName.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video name cannot be empty.");
        }

        // Load the video resource
        Resource videoResource = loadVideoResource(videoName);
        System.out.println("video resource :" + videoResource);
        // Determine content length
        long contentLength = getResourceContentLength(videoResource);
        System.out.println("content length :" + contentLength);
        // Calculate the region to stream
        ResourceRegion region = calculateResourceRegion(videoResource, headers, contentLength);
        System.out.println("region :" + region);
        // Build and return the response
        MediaType mediaType = MediaTypeFactory.getMediaType(videoResource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        System.out.println("media type :" + mediaType);
        System.out.println("Returning video file");
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).contentType(mediaType).body(region);
    }

    /**
     * Loads a video resource by name.
     *
     * @param videoName The name of the video file.
     * @return The video resource.
     */
    private Resource loadVideoResource(String videoName) {
        String videoPath = "classpath:videos/" + videoName; // Videos are placed in `resources/videos`
        System.out.println("video path :" + videoPath);
        Resource resource = resourceLoader.getResource(videoPath);
        System.out.println("resource :" + resource);
        if (!resource.exists() || !resource.isReadable()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found: " + videoName);
        }
        return resource;
    }

    /**
     * Retrieves the content length of the resource.
     *
     * @param resource The video resource.
     * @return The content length.
     */
    private long getResourceContentLength(Resource resource) {
        try {
            return resource.contentLength();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading video content.");
        }
    }

    /**
     * Calculates the ResourceRegion for the given video resource based on HTTP range headers.
     *
     * @param resource      The video resource.
     * @param headers       The HTTP headers containing range requests.
     * @param contentLength The total length of the resource.
     * @return The ResourceRegion to be streamed.
     */
    private ResourceRegion calculateResourceRegion(Resource resource, HttpHeaders headers, long contentLength) {
        List<HttpRange> ranges = headers.getRange();

        if (ranges.isEmpty()) {
            // Default to the first 1MB if no range is specified
            long defaultChunkSize = Math.min(1024 * 1024, contentLength);
            return new ResourceRegion(resource, 0, defaultChunkSize);
        }

        // Handle the first range only (most clients use one range)
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(contentLength);
        long end = range.getRangeEnd(contentLength);
        long chunkSize = Math.min(1024 * 1024, end - start + 1);

        return new ResourceRegion(resource, start, chunkSize);
    }
//    ---------------------------------------------------------------------------------------------------
}

//nio
//chunk
//compress when upload n decompress