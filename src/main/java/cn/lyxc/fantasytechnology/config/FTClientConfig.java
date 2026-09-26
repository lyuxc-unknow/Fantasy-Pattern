package cn.lyxc.fantasytechnology.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/// Visual preferences stored on each player's client, including multiplayer clients.
public final class FTClientConfig {
    public static final ModConfigSpec.BooleanValue ANNIHILATION_EFFECTS;
    public static final ModConfigSpec.BooleanValue DEVICE_ACCESS_EFFECTS;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ANNIHILATION_EFFECTS = builder
                .comment("Animate the fantasy annihilation block's tesseract, luminous cells and flowing edges.",
                        "When disabled, the custom frame and a static tesseract remain visible.",
                        "Client-only; changes take effect immediately.")
                .translation("fantasy_technology.configuration.annihilation_effects")
                .define("annihilation_effects", true);
        DEVICE_ACCESS_EFFECTS = builder
                .comment("Draw the device access block's celestial display: a globe of nested shells with two",
                        "counter-rotating star rings around it, turning inside the block's open cage.",
                        "Client-only; changes take effect immediately.")
                .translation("fantasy_technology.configuration.device_access_effects")
                .define("device_access_effects", true);
        SPEC = builder.build();
    }

    private FTClientConfig() {
    }
}
