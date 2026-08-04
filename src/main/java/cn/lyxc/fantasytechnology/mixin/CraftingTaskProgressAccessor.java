/*
 * Portions of this file are adapted from OmniSequence-Transfinite
 * (https://github.com/AyaYumi/OmniSequence-Transfinite),
 * Copyright (c) 2025 AyaYumi, licensed under the MIT License.
 * See THIRD_PARTY_NOTICES.md in the project root for the full license text.
 */

package cn.lyxc.fantasytechnology.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// How many repetitions of one pattern a crafting job still owes. AE2 decrements it by one per successful push, so a
/// batch of N has to subtract the other N-1 itself.
@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", remap = false)
public interface CraftingTaskProgressAccessor {

    @Accessor("value")
    long fantasyTechnology$getValue();

    @Accessor("value")
    void fantasyTechnology$setValue(long value);
}
