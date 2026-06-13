package tn.cityvoice.signalement.enums;

public enum StatutContrat {
    EN_ATTENTE_SIGNATURE,   // Contrat généré, en attente du chef d'équipe
    ACCEPTE,                // Chef d'équipe a signé et accepté
    REFUSE,                 // Chef d'équipe a refusé → réaffectation déclenchée
    REASSIGNE               // Un refus a conduit à un nouveau contrat pour une autre équipe
}
