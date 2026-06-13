package tn.cityvoice.userservice.service;

import java.util.Map;
import java.util.UUID;

public interface SegmentationService {
    Map<String, Object> segment(UUID userId);
}