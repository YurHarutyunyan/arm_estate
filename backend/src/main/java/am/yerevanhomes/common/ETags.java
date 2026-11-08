package am.yerevanhomes.common;

/** Converts between entity versions and strong ETag values like {@code "3"}. */
public final class ETags {

    private ETags() {
    }

    public static String of(long version) {
        return "\"" + version + "\"";
    }

    /**
     * Reads the version from an If-Match header.
     *
     * @throws PreconditionRequiredException if the header is missing
     * @throws PreconditionFailedException if it is not a single strong ETag of this API
     */
    public static long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException("Send the If-Match header with the ETag you received");
        }
        String value = ifMatch.strip();
        if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            throw new PreconditionFailedException("If-Match must be a single ETag such as \"3\"");
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException e) {
            throw new PreconditionFailedException("If-Match must be a single ETag such as \"3\"");
        }
    }
}
