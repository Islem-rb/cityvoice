package tn.cityvoice.signalement.exception;

public class SignalementNotFoundException extends RuntimeException {
    public SignalementNotFoundException(String message) {
        super(message);
    }
}
