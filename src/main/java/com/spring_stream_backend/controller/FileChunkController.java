package com.spring_stream_backend.controller;

import com.spring_stream_backend.service.FileChunkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/file/chunk")
public class FileChunkController {

    @Autowired
    private FileChunkService fileChunkService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadAndChunkFile(MultipartFile file) {
        try {
            fileChunkService.processAndChunkFile(file);
            return ResponseEntity.ok("File successfully split into chunks and saved.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error while processing file: " + e.getMessage());
        }
    }
}
