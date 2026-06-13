package tn.cityvoice.signalement.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "voice.demo")
@Data
public class VoiceDemoProperties {

    private boolean enabled = true;
    private String publicBaseUrl = "";
    private String twimlAppSid = "";
    private String accountSid = "";
    private String apiKeySid = "";
    private String apiKeySecret = "";
    private String authToken = "";
    private int tokenTtlSeconds = 3600;
    private String defaultIdentity = "demo-prof";
    private Double defaultLatitude = 36.8065;
    private Double defaultLongitude = 10.1815;
    private String defaultAddressLabel = "Localisation a confirmer";
    private String whisperUrl = "";
    private String whisperApiKey = "";
    private String whisperModel = "whisper-1";
    private String whisperLanguage = "fr";
}
