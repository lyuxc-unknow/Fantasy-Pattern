/*
 * Portions of this file are adapted from OmniSequence-Transfinite
 * (https://github.com/AyaYumi/OmniSequence-Transfinite),
 * Copyright (c) 2025 AyaYumi, licensed under the MIT License.
 * See THIRD_PARTY_NOTICES.md in the project root for the full license text.
 */

package cn.lyxc.fantasytechnology.config;

import cn.lyxc.fantasytechnology.crafting.maxfast.OmniMaxFastMode;
import net.neoforged.neoforge.common.ModConfigSpec;

/// Server-side configuration for the aggregated crafting planner and batch dispatch.
///
/// Both features are inert unless a fantasy annihilation block is on the network, and both fall back to stock AE2
/// behaviour whenever anything cannot be verified - these options exist to turn them off outright.
public final class FTConfig {

    private FTConfig() {
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<OmniMaxFastMode> MAX_FAST_MODE;
    public static final ModConfigSpec.IntValue MAX_FAST_MAX_NODES;
    public static final ModConfigSpec.IntValue MAX_FAST_COMPILE_BUDGET_MS;
    public static final ModConfigSpec.BooleanValue DIAGNOSTICS;
    public static final ModConfigSpec.BooleanValue BATCH_DISPATCH_ENABLED;

    public static final ModConfigSpec SPEC;

    static {
        MAX_FAST_MODE = BUILDER
                .comment("Aggregated crafting-plan optimizer. SAFE deduplicates deterministic recipe trees;",
                        "every branch it cannot prove equivalent is handed back to AE2. OFF disables it entirely.")
                .translation("fantasy_technology.configuration.max_fast_mode")
                .defineEnum("max_fast_mode", OmniMaxFastMode.SAFE);
        MAX_FAST_MAX_NODES = BUILDER
                .comment("Maximum unique recipe-tree nodes compiled by the optimizer before falling back to AE2.")
                .translation("fantasy_technology.configuration.max_fast_max_nodes")
                .defineInRange("max_fast_max_nodes", 8192, 64, 65536);
        MAX_FAST_COMPILE_BUDGET_MS = BUILDER
                .comment("Maximum graph compilation time in milliseconds before the optimizer falls back to AE2.")
                .translation("fantasy_technology.configuration.max_fast_compile_budget_ms")
                .defineInRange("max_fast_compile_budget_ms", 100, 1, 5000);
        BATCH_DISPATCH_ENABLED = BUILDER
                .comment("Dispatch several repetitions of one recipe to the fantasy annihilation block in a single",
                        "push. Throughput is unchanged - the crafting CPU still spends one operation per craft -",
                        "but the per-craft inventory walk is done once instead of N times.")
                .translation("fantasy_technology.configuration.batch_dispatch_enabled")
                .define("batch_dispatch_enabled", true);
        DIAGNOSTICS = BUILDER
                .comment("Log why the optimizer aggregated or fell back on each crafting calculation.")
                .translation("fantasy_technology.configuration.diagnostics")
                .define("diagnostics", false);

        SPEC = BUILDER.build();
    }
}
