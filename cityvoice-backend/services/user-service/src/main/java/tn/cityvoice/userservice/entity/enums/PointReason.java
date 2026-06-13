package tn.cityvoice.userservice.entity.enums;

public enum PointReason {

    // ── Indépendants des signalements ─────────────────────
    INSCRIPTION,          // +10  créer un compte
    PROFIL_COMPLETE,      // +25  compléter son profil
    PHOTO_AJOUTEE,        // +10  ajouter une photo de profil
    PREMIERE_CONNEXION,   // +5   première connexion

    // ── Liés aux signalements (à implémenter plus tard) ───
    SIGNALEMENT_SOUMIS,   // +10
    SIGNALEMENT_RESOLU,   // +25
    SIGNALEMENT_VALIDE,   // +15
    VOTE_PROJET,          // +5

    // ── Badges / gamification ────────────────────────────
    BADGE_OBTENU,         // +20

    // ── Négatif ──────────────────────────────────────────
    INACTIVITE,           // -5
    SIGNALEMENT_REJETE,   // -10
    DON,
}