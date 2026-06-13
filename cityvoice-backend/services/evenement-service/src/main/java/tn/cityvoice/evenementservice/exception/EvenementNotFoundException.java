// exception/EvenementNotFoundException.java
package tn.cityvoice.evenementservice.exception;

public class EvenementNotFoundException extends RuntimeException {
    public EvenementNotFoundException(String message) {
        super(message);
    }
}