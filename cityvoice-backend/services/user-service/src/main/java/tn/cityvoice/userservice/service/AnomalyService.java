package tn.cityvoice.userservice.service;

import java.util.Map;
import java.util.UUID;

public interface AnomalyService {
    Map<String, Object> detect(UUID userId);
}