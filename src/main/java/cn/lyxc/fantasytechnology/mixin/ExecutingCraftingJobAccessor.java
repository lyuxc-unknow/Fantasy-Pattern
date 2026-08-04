/*
 * Portions of this file are adapted from OmniSequence-Transfinite
 * (https://github.com/AyaYumi/OmniSequence-Transfinite),
 * Copyright (c) 2025 AyaYumi, licensed under the MIT License.
 * See THIRD_PARTY_NOTICES.md in the project root for the full license text.
 */

package cn.lyxc.fantasytechnology.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.execution.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/// Reaches the per-pattern task counters of a running crafting job.
///
/// The value type is `ExecutingCraftingJob$TaskProgress`, which is package-private in AE2 and therefore cannot be
/// named here; erasure makes the field descriptor plain `Map` either way, so the entries are handed out as
/// {@link Object} and read through {@link CraftingTaskProgressAccessor}.
@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface ExecutingCraftingJobAccessor {

    @Accessor("tasks")
    Map<IPatternDetails, Object> fantasyTechnology$getTasks();
}
