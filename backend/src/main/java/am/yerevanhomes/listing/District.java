package am.yerevanhomes.listing;

public enum District {
    KENTRON("Kentron"),
    ARABKIR("Arabkir"),
    DAVTASHEN("Davtashen"),
    AJAPNYAK("Ajapnyak"),
    MALATIA_SEBASTIA("Malatia-Sebastia"),
    SHENGAVIT("Shengavit"),
    EREBUNI("Erebuni"),
    NOR_NORK("Nor Nork"),
    AVAN("Avan"),
    KANAKER_ZEYTUN("Kanaker-Zeytun"),
    NORK_MARASH("Nork-Marash"),
    NUBARASHEN("Nubarashen");

    private final String displayName;

    District(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
