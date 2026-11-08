package am.yerevanhomes.common;

/** The client tried to change a resource without saying which version it saw. Maps to 428. */
public class PreconditionRequiredException extends RuntimeException {

    public PreconditionRequiredException(String message) {
        super(message);
    }
}
