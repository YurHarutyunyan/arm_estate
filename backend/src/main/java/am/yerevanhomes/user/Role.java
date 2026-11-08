package am.yerevanhomes.user;

public enum Role {
    /** Searches and saves favorites. */
    USER,
    /** Also publishes listings and manages their own. */
    OWNER,
    /** Manages every listing. Can't be chosen at sign-up. */
    ADMIN
}
