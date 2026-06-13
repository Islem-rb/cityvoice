package tn.cityvoice.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.userservice.entity.PointTransaction;
import tn.cityvoice.userservice.service.PointService;
import tn.cityvoice.userservice.service.UserService;

import tn.cityvoice.userservice.entity.User;
import tn.cityvoice.userservice.entity.enums.PointReason;
import tn.cityvoice.userservice.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class PointController {

    final PointService pointService;
    final UserRepository userRepo;
    final UserService userService;
    @GetMapping("/{id}/points")
    public ResponseEntity<List<PointTransaction>> getPoints(
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(pointService.getTransactions(id));
    }

    @PostMapping("/{id}/points")
    public ResponseEntity<PointTransaction> addPoints(
            @PathVariable("id") UUID id,
            @RequestParam(name = "points", required = false) Integer pointsParam,
            @RequestParam(name = "reason", required = false) String reasonParam,
            @RequestBody(required = false) Map<String, Object> body) {

        User user = userService.findById(id);

        int points;
        String description = "";
        PointReason reason = PointReason.DON;

        // Case 1: points sent as request param
        if (pointsParam != null) {
            points = pointsParam;

            if (reasonParam != null && !reasonParam.isBlank()) {
                reason = PointReason.valueOf(reasonParam.toUpperCase());
            }
        }

        // Case 2: points sent in JSON body
        else if (body != null && body.containsKey("points")) {
            points = Integer.parseInt(body.get("points").toString());

            description = body.getOrDefault("description", "").toString();

            if (body.containsKey("reason")) {
                reason = PointReason.valueOf(body.get("reason").toString().toUpperCase());
            }
        }

        // No points provided
        else {
            return ResponseEntity.badRequest().build();
        }

        PointTransaction tx = pointService.addPoints(user, points, reason, description);

        return ResponseEntity.ok(tx);
    }

    @PostMapping("/by-email/points")
    public ResponseEntity<?> addPointsByEmail(
            @RequestParam("email") String email,
            @RequestBody Map<String, Object> body) {

        // Decode URL-encoded email (convert %40 back to @)
        String decodedEmail;
        try {
            decodedEmail = URLDecoder.decode(email, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decode email: {}", email, e);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid email format"));
        }

        log.info("Adding points by email: {}", decodedEmail);
        int points = Integer.parseInt(body.get("points").toString());
        String description = body.getOrDefault("description", "Don projet").toString();

        User user = userRepo.findByEmail(decodedEmail).orElse(null);
        if (user == null) {
            log.warn("User not found by email: {}", decodedEmail);
            return ResponseEntity.notFound().build();
        }

        PointTransaction tx = pointService.addPoints(user, points, PointReason.DON, description);
        log.info("Points added successfully. New total: {}", user.getPoints());
        return ResponseEntity.ok(Map.of(
                "points", tx.getPoints(),
                "totalPoints", user.getPoints()
        ));
    }
}