package am.yerevanhomes.search;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import am.yerevanhomes.common.BadRequestException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opaque paging token: the sort values of the last hit on a page (for search_after) plus the sort
 * order they belong to, so a cursor from one sort can't be replayed against another.
 */
record SearchCursor(SearchSort sort, List<Object> after) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    String encode(JsonMapper json) {
        return ENCODER.encodeToString(json.writeValueAsBytes(new Payload(sort.name(), after)));
    }

    static SearchCursor decode(String token, SearchSort expectedSort, JsonMapper json) {
        try {
            Payload payload = json.readValue(new String(DECODER.decode(token), StandardCharsets.UTF_8), Payload.class);
            SearchSort sort = SearchSort.valueOf(payload.s());
            if (sort != expectedSort) {
                throw new BadRequestException("cursor belongs to sort " + sort + ", not " + expectedSort);
            }
            if (payload.a() == null || payload.a().size() != sort.clauses().size() || !wellTyped(payload.a())) {
                throw new BadRequestException("cursor is invalid");
            }
            return new SearchCursor(sort, payload.a());
        } catch (IllegalArgumentException | JacksonException e) {
            throw new BadRequestException("cursor is invalid");
        }
    }

    /**
     * Every sort ends with the id (a string); the clauses before it are numbers (score, price, date).
     * Checked here so a tampered cursor is a 400, not an Elasticsearch error and a 500.
     */
    private static boolean wellTyped(List<Object> after) {
        for (int i = 0; i < after.size() - 1; i++) {
            if (!(after.get(i) instanceof Number)) {
                return false;
            }
        }
        return after.getLast() instanceof String;
    }

    private record Payload(String s, List<Object> a) {
    }
}
