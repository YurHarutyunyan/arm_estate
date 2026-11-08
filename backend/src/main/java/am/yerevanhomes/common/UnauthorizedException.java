package am.yerevanhomes.common;

/** Wrong credentials. Maps to 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
