package com.spring_stream_backend.controller;

import com.spring_stream_backend.service.VideoUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload/video")
public class VideoUploadController {

    @Autowired
    private VideoUploadService videoUploadService;

    @PostMapping("/simple-upload")
    public ResponseEntity<String> uploadVideoToBackend(MultipartFile file) {
        try {
            String message = videoUploadService.saveVideoToLocal(file);
            return ResponseEntity.ok(message);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload failed: " + e.getMessage());
        }
    }

    @PostMapping("/chunk-upload")
    public ResponseEntity<String> uploadVideoChunk(MultipartFile file, @RequestParam("chunkIndex") int chunkIndex, @RequestParam("totalChunks") int totalChunks, @RequestParam("fileName") String fileName) {
        try {
            String message = videoUploadService.saveVideoChunk(file, chunkIndex, totalChunks, fileName);
            return ResponseEntity.ok(message);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload failed: " + e.getMessage());
        }
    }

    @PostMapping("/complete-chunk-upload")
    public ResponseEntity<String> completeUpload(@RequestParam("fileName") String fileName) {
        try {
            String message = videoUploadService.mergeChunks(fileName);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to complete upload: " + e.getMessage());
        }
    }

}