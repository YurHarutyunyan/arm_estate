package am.yerevanhomes.common;

/** The request is understandable but not acceptable (bad cursor, min price above max, ...). Maps to 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
