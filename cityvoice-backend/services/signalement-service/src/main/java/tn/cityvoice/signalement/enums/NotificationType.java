package tn.cityvoice.signalement.enums;

public enum NotificationType {
    SIGNALEMENT_CREE,       // citoyen crée un signalement
    SIGNALEMENT_EN_COURS,   // statut → EN_COURS
    SIGNALEMENT_RESOLU,     // statut → RESOLU
    SIGNALEMENT_REJETE,     // statut → REJETE
    CONTRAT_GENERE,         // contrat généré pour chef d'équipe
    CONTRAT_ACCEPTE,        // contrat signé par chef
    CONTRAT_REFUSE,         // contrat refusé par chef
    COMMENTAIRE,            // nouveau commentaire sur un article/signalement
    INFO,                    // notification générique
    BADGE
}
