package tn.cityvoice.userservice.service;

public interface ChurnService {
    ChurnPrediction predict(java.util.UUID userId);

    record ChurnPrediction(
            String userId,
            double churnProbability,
            String riskLevel,        // LOW | MEDIUM | HIGH | CRITICAL
            Integer daysUntilChurn,
            java.util.List<String> riskFactors,
            java.util.List<RetentionAction> retentionActions,
            double modelConfidence
    ) {}

    record RetentionAction(
            String action,
            String priority,
            String expectedImpact
    ) {}
}