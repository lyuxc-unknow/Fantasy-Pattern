/*
 * Portions of this file are adapted from OmniSequence-Transfinite
 * (https://github.com/AyaYumi/OmniSequence-Transfinite),
 * Copyright (c) 2025 AyaYumi, licensed under the MIT License.
 * See THIRD_PARTY_NOTICES.md in the project root for the full license text.
 */

package cn.lyxc.fantasytechnology.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import cn.lyxc.fantasytechnology.integration.ae2.OmniCraftingTreeProcessBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = CraftingTreeProcess.class, remap = false)
public interface OmniCraftingTreeProcessAccessor extends OmniCraftingTreeProcessBridge {
    @Override
    @Accessor("details")
    IPatternDetails fantasytechnology$getDetails();

    @Override
    @Accessor("nodes")
    Map<CraftingTreeNode, Long> fantasytechnology$getChildNodes();

    @Override
    @Accessor("containerItems")
    boolean fantasytechnology$hasContainerItems();

    @Override
    @Accessor("limitQty")
    boolean fantasytechnology$limitsQuantity();
}
