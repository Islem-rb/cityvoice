package tn.cityvoice.actualiteservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.cityvoice.actualiteservice.service.FileUploadService;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final FileUploadService fileUploadService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file) {
        try {
            String url = fileUploadService.saveFile(file);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Upload failed"));
        }
    }

    @PostMapping("/upload-multiple")
    public ResponseEntity<Map<String, List<String>>> uploadMultiple(
            @RequestParam("files") List<MultipartFile> files) {
        try {
            List<String> urls = new ArrayList<>();
            for (MultipartFile file : files) {
                urls.add(fileUploadService.saveFile(file));
            }
            return ResponseEntity.ok(Map.of("urls", urls));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", List.of("Upload failed")));
        }
    }
}