package am.yerevanhomes.common;

/** Signed in, but not allowed to do this. Maps to 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
