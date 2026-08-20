package cn.lyxc.fantasytechnology.config;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// Server-side configuration for fantasy devices and OmniSequence batch dispatch.
public final class FTConfig {

    private FTConfig() {
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue BATCH_DISPATCH_ENABLED;
    public static final ModConfigSpec.BooleanValue TRUST_SERVER_RECIPE_PARSING;
    public static final ModConfigSpec.EnumValue<DeviceAccessMode> DEVICE_ACCESS_MODE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCKED_JEI_CATEGORY_IDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ANNIHILATION_FUEL_ITEMS;
    public static final ModConfigSpec.BooleanValue CONSUME_FUEL;

    public static final List<String> DEFAULT_BLOCKED_JEI_CATEGORY_IDS = List.of(
            "minecraft:tag_recipes/item",
            "minecraft:tag_recipes/fluid",
            "minecraft:tag_recipes/block",
            "ae2:attunement");
    public static final List<String> DEFAULT_ANNIHILATION_FUEL_ITEMS = List.of("ae2:matter_ball:100000");

    public record AnnihilationFuel(ResourceLocation itemId, int crafts) {
    }

    public static final ModConfigSpec SPEC;

    static {
        BATCH_DISPATCH_ENABLED = BUILDER
                .comment("Allow OmniSequence to dispatch several repetitions of one fantasy pattern in a batch.",
                        "When consume_fuel is disabled, unbounded work is split into finite safe batches.",
                        "Changes require re-entering the world or restarting the game.")
                .translation("fantasy_technology.configuration.batch_dispatch_enabled")
                .define("batch_dispatch_enabled", true);
        TRUST_SERVER_RECIPE_PARSING = BUILDER
                .comment("Use only recipes parsed by trusted providers on the logical server.",
                        "When enabled, JEI recipe transfer is disabled and the encoding terminal exposes its own",
                        "server-backed recipe browser. By default that browser contains crafting-table recipes plus",
                        "entries from data/<namespace>/recipe_provider/*.json.",
                        "WARNING: the two pattern kinds are mutually exclusive, so changing this invalidates every",
                        "fantasy pattern already encoded in the world. Existing patterns keep their items but stop",
                        "crafting and can no longer be edited or inserted into a provider until it is changed back.")
                .translation("fantasy_technology.configuration.trust_server_recipe_parsing")
                .define("trust_server_recipe_parsing", false);
        DEVICE_ACCESS_MODE = BUILDER
                .comment("Whether selecting or filling a recipe in the fantasy encoding terminal requires owning the machine.",
                        "REQUIRE_DEVICES: a recipe may only be selected or transferred when device access blocks",
                        "hold what it needs - a rule from data/<namespace>/device_access/ if one covers",
                        "the recipe, otherwise four of the recipe category's own catalysts.",
                        "UNRESTRICTED: no such check; the blocks stay usable as storage.")
                .translation("fantasy_technology.configuration.device_access_mode")
                .defineEnum("device_access_mode", DeviceAccessMode.REQUIRE_DEVICES);
        BLOCKED_JEI_CATEGORY_IDS = BUILDER
                .comment("JEI recipe category ids that the fantasy encoding terminal must not transfer.",
                        "The defaults are browse-only pages rather than real recipes.",
                        "Changes take effect only after leaving and re-entering the world or restarting the game.")
                .translation("fantasy_technology.configuration.blocked_jei_category_ids")
                .defineListAllowEmpty("blocked_jei_category_ids", DEFAULT_BLOCKED_JEI_CATEGORY_IDS,
                        value -> value instanceof String id && ResourceLocation.tryParse(id) != null);
        ANNIHILATION_FUEL_ITEMS = BUILDER
                .comment("Fuel accepted by the fantasy annihilation block, formatted as <item id>:<crafts>.",
                        "The final colon separates the item id from its positive craft count.",
                        "Changes apply immediately to newly inserted fuel; existing charges are unchanged.",
                        "Default: [\"ae2:matter_ball:100000\"]")
                .translation("fantasy_technology.configuration.annihilation_fuel_items")
                .defineListAllowEmpty("annihilation_fuel_items", DEFAULT_ANNIHILATION_FUEL_ITEMS,
                        value -> value instanceof String entry && parseAnnihilationFuel(entry) != null);
        CONSUME_FUEL = BUILDER
                .comment("Whether the fantasy annihilation block consumes matter-ball fuel (or other configured",
                        "fuel items) on each craft. When false the machine crafts for free: the fuel slot keeps",
                        "storing items and prepaid charges are never touched. Changes take effect immediately.")
                .translation("fantasy_technology.configuration.consume_fuel")
                .define("consume_fuel", true);

        SPEC = BUILDER.build();
    }

    /// Parses {@code namespace:item_path:craft_count}; the final colon is the separator because item ids already
    /// contain one between their namespace and path.
    public static @Nullable AnnihilationFuel parseAnnihilationFuel(String configured) {
        String value = configured.trim();
        int separator = value.lastIndexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            return null;
        }

        ResourceLocation itemId = ResourceLocation.tryParse(value.substring(0, separator).trim());
        if (itemId == null) {
            return null;
        }
        try {
            int crafts = Integer.parseInt(value.substring(separator + 1).trim());
            return crafts > 0 ? new AnnihilationFuel(itemId, crafts) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static @Nullable String normalizeAnnihilationFuel(String configured) {
        AnnihilationFuel fuel = parseAnnihilationFuel(configured);
        return fuel == null ? null : fuel.itemId() + ":" + fuel.crafts();
    }
}
