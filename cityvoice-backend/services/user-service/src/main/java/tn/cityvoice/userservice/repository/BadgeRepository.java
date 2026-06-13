package tn.cityvoice.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.userservice.entity.Badge;

import java.util.Optional;
import java.util.UUID;

public interface BadgeRepository extends JpaRepository<Badge, UUID> {
    Optional<Badge> findByCode(String code);
}