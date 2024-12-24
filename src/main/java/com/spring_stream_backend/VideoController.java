package com.spring_stream_backend;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/videos")
@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.POST})
public class VideoController {

    @Autowired
    private VideoStreamingService videoStreamingService;

    @GetMapping("/stream-io")
    public ResponseEntity<byte[]> streamVideoIo(@RequestParam String fileName, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String rangeHeader = request.getHeader(HttpHeaders.RANGE); // Get the Range header
        System.out.println("Requested File Name: " + fileName);
        System.out.println("Range Header: " + rangeHeader);
        return videoStreamingService.streamVideoIo(fileName, rangeHeader, response); // Pass response to service
    }

    @GetMapping("/stream-nio")
    public ResponseEntity<Resource> streamVideoNio(@RequestParam String fileName, HttpServletRequest request) {
        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        System.out.println("Requested File Name: " + fileName);
        System.out.println("Range Header: " + rangeHeader);
        return videoStreamingService.streamVideoNio(fileName, rangeHeader);
    }

    @GetMapping("/{videoName}")
    public ResponseEntity<ResourceRegion> streamVideo(@PathVariable String videoName, @RequestHeader HttpHeaders headers) {
        System.out.println("headers" + headers.getRange());
        System.out.println("Requested File Name: " + videoName);
        System.out.println("Requested File Name: " + videoName);
        return videoStreamingService.streamVideo(videoName, headers);
    }
}
