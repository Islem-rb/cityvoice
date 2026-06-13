package tn.cityvoice.personnelservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.personnelservice.entity.CV;
import tn.cityvoice.personnelservice.service.iCV;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/personnel/cv")
@RequiredArgsConstructor
public class CvController {
    private final iCV cvService;

    // 🔹 Get all CV
    @GetMapping("/get")
    public ResponseEntity<List<CV>> getAllCV() {
        return ResponseEntity.ok(cvService.getAllCV());
    }

    // 🔹 Add CV
    @PostMapping
    public ResponseEntity<CV> addCV(@RequestBody CV cv) {
        cvService.addCV(cv);
        return ResponseEntity.status(HttpStatus.CREATED).body(cv);
    }

    // 🔹 Update CV
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateCV(@PathVariable("id") UUID id,
                                         @RequestBody CV cv) {
        cvService.updateCV(id, cv);
        return ResponseEntity.ok().build();
    }

    // 🔹 Delete CV
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCV(@PathVariable ("id")UUID id) {
        cvService.deleteCV(id);
        return ResponseEntity.noContent().build();
    }

}
