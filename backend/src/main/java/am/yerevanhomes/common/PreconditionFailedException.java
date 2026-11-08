package am.yerevanhomes.common;

/** The client edited an outdated version of a resource. Maps to 412. */
public class PreconditionFailedException extends RuntimeException {

    public PreconditionFailedException(String message) {
        super(message);
    }
}
