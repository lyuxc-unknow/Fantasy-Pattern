/*
 * Portions of this file are adapted from OmniSequence-Transfinite
 * (https://github.com/AyaYumi/OmniSequence-Transfinite),
 * Copyright (c) 2025 AyaYumi, licensed under the MIT License.
 * See THIRD_PARTY_NOTICES.md in the project root for the full license text.
 */

package cn.lyxc.fantasytechnology.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.ICraftingInventory;
import net.minecraft.world.level.Level;

import java.util.ArrayList;

public interface OmniCraftingTreeNodeBridge {
    AEKey fantasytechnology$getWhat();

    long fantasytechnology$getAmount();

    IPatternDetails.IInput fantasytechnology$getParentInput();

    Level fantasytechnology$getLevel();

    boolean fantasytechnology$canEmit();

    ArrayList<CraftingTreeProcess> fantasytechnology$getProcesses();

    /// How many nodes AE2 counts for this subtree; only meaningful once the subtree has actually been requested.
    long fantasytechnology$getNodeCount();

    void fantasytechnology$buildChildPatterns();

    Iterable<InputTemplate> fantasytechnology$getValidItemTemplates(ICraftingInventory inventory);

    void fantasytechnology$request(CraftingSimulationState inventory, long requestedAmount,
            KeyCounter containerItems) throws CraftBranchFailure, InterruptedException;
}
