package tn.cityvoice.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.userservice.entity.Badge;
import tn.cityvoice.userservice.entity.UserBadge;
import tn.cityvoice.userservice.service.BadgeService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class BadgeController {

    final BadgeService badgeService;

    @GetMapping("/{id}/badges")
    public ResponseEntity<List<UserBadge>> getUserBadges(
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(badgeService.getUserBadges(id));
    }

    @GetMapping("/badges")
    public ResponseEntity<List<Badge>> getAllBadges() {
        return ResponseEntity.ok(badgeService.getAllBadges());
    }
}