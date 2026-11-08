package am.yerevanhomes.common;

/** The request clashes with existing state (email taken, limit reached). Maps to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
