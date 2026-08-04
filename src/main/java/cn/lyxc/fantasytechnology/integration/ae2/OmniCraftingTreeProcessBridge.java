/*
 * Portions of this file are adapted from OmniSequence-Transfinite
 * (https://github.com/AyaYumi/OmniSequence-Transfinite),
 * Copyright (c) 2025 AyaYumi, licensed under the MIT License.
 * See THIRD_PARTY_NOTICES.md in the project root for the full license text.
 */

package cn.lyxc.fantasytechnology.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingTreeNode;

import java.util.Map;

public interface OmniCraftingTreeProcessBridge {
    IPatternDetails fantasytechnology$getDetails();

    Map<CraftingTreeNode, Long> fantasytechnology$getChildNodes();

    boolean fantasytechnology$hasContainerItems();

    boolean fantasytechnology$limitsQuantity();
}
