package tn.cityvoice.signalement.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import tn.cityvoice.signalement.dto.AiAnalysisResponse;
import tn.cityvoice.signalement.dto.VoiceStructuringRequest;
import tn.cityvoice.signalement.dto.VoiceStructuringResponse;
import lombok.Builder;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "ai-service",
    url  = "${ai.service.url:http://localhost:8091}"
)
public interface AiServiceClient {

    @PostMapping("/api/v1/analyze")
    AiAnalysisResponse analyze(@RequestBody AiAnalysisRequest request);

    @PostMapping("/api/v1/voice/structure")
    VoiceStructuringResponse structureVoice(@RequestBody VoiceStructuringRequest request);

    /** LLaVA compare photo avant/après et génère un rapport de vérification */
    @PostMapping("/api/v1/verify-resolution")
    VerifyResolutionResponse verifyResolution(@RequestBody VerifyResolutionRequest request);

    @Data @Builder
    class AiAnalysisRequest {
        private String description;
        private Double latitude;
        private Double longitude;

        @JsonProperty("image_base64")
        private String imageBase64;

        /**
         * Type de signalement choisi par le citoyen (ex: "DECHETS_NON_COLLECTES").
         * Transmis à l'IA pour forcer l'affectation d'équipe correcte
         * au lieu de reclassifier depuis zéro.
         */
        @JsonProperty("type_signalement")
        private String typeSignalement;
    }

    @Data @Builder
    class VerifyResolutionRequest {
        @JsonProperty("image_avant")
        private String imageAvant;

        @JsonProperty("image_apres")
        private String imageApres;

        @JsonProperty("type_signalement")
        private String typeSignalement;

        @JsonProperty("description_originale")
        private String descriptionOriginale;
    }

    @Data
    class VerifyResolutionResponse {
        @JsonProperty("resolu")
        private boolean resolu;

        @JsonProperty("score_confiance")
        private double scoreConfiance;

        @JsonProperty("rapport")
        private String rapport;

        @JsonProperty("observations")
        private String observations;
    }
}
