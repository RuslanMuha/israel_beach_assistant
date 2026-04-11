package com.beachassistant.domain.flag;

/**
 * Municipal swim-flag semantics (Israel): application knowledge for user-facing copy.
 * Not wired to live flag feeds yet; complements {@link com.beachassistant.common.enums.Recommendation}.
 */
public enum SwimFlagKnowledge {
    BLACK(
            "черный",
            "купание запрещено",
            "не рекомендуется",
            "опасные условия, вход в воду запрещён."
    ),
    RED(
            "красный",
            "опасно для купания",
            "не рекомендуется",
            "существенный риск для купания."
    ),
    YELLOW(
            "жёлтый",
            "осторожно при купании",
            "осторожно",
            "повышенный риск; детям и слабым пловцам — особенно осторожно."
    ),
    GREEN(
            "зелёный",
            "условия для купания благоприятные",
            "можно купаться",
            "типичные спокойные условия при разрешении купания."
    );

    private final String colorNameRu;
    private final String displayLabelRu;
    private final String swimStanceRu;
    private final String shortExplanationRu;

    SwimFlagKnowledge(String colorNameRu, String displayLabelRu, String swimStanceRu, String shortExplanationRu) {
        this.colorNameRu = colorNameRu;
        this.displayLabelRu = displayLabelRu;
        this.swimStanceRu = swimStanceRu;
        this.shortExplanationRu = shortExplanationRu;
    }

    public String colorNameRu() {
        return colorNameRu;
    }

    public String displayLabelRu() {
        return displayLabelRu;
    }

    public String swimStanceRu() {
        return swimStanceRu;
    }

    public String shortExplanationRu() {
        return shortExplanationRu;
    }

    /**
     * One line for Telegram: what each flag means (no live flag value).
     */
    public static String compactLegendRu() {
        return "Флаги (справка): "
                + BLACK.colorNameRu + " — " + BLACK.displayLabelRu + "; "
                + RED.colorNameRu + " — " + RED.displayLabelRu + "; "
                + YELLOW.colorNameRu + " — " + YELLOW.displayLabelRu + "; "
                + GREEN.colorNameRu + " — " + GREEN.displayLabelRu + ".";
    }
}
