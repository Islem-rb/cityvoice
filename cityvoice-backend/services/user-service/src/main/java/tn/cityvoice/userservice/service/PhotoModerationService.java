package tn.cityvoice.userservice.service;

public interface PhotoModerationService {
    ModerationResult moderate(String base64Image);

    record ModerationResult(boolean safe, String reason) {}
}