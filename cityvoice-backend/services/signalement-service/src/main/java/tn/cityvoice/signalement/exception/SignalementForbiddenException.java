package tn.cityvoice.signalement.exception;

/**
 * Levée quand un utilisateur tente une opération non autorisée
 * sur un signalement (ex. : modification après soumission, suppression sans rôle ADMIN).
 */
public class SignalementForbiddenException extends RuntimeException {

    public SignalementForbiddenException(String message) {
        super(message);
    }
}
