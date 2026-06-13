package tn.cityvoice.personnelservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.personnelservice.entity.CV;

import java.util.UUID;

public interface CVRepository extends JpaRepository<CV, UUID> {
}
