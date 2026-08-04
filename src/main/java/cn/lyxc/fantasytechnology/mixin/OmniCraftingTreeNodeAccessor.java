/*
 * Portions of this file are adapted from OmniSequence-Transfinite
 * (https://github.com/AyaYumi/OmniSequence-Transfinite),
 * Copyright (c) 2025 AyaYumi, licensed under the MIT License.
 * See THIRD_PARTY_NOTICES.md in the project root for the full license text.
 */

package cn.lyxc.fantasytechnology.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.ICraftingInventory;
import cn.lyxc.fantasytechnology.integration.ae2.OmniCraftingTreeNodeBridge;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.ArrayList;

@Mixin(value = CraftingTreeNode.class, remap = false)
public interface OmniCraftingTreeNodeAccessor extends OmniCraftingTreeNodeBridge {
    @Override
    @Accessor("what")
    AEKey fantasytechnology$getWhat();

    @Override
    @Accessor("amount")
    long fantasytechnology$getAmount();

    @Override
    @Accessor("parentInput")
    IPatternDetails.IInput fantasytechnology$getParentInput();

    @Override
    @Accessor("level")
    Level fantasytechnology$getLevel();

    @Override
    @Accessor("canEmit")
    boolean fantasytechnology$canEmit();

    @Override
    @Accessor("nodes")
    ArrayList<CraftingTreeProcess> fantasytechnology$getProcesses();

    @Override
    @Invoker("getNodeCount")
    long fantasytechnology$getNodeCount();

    @Override
    @Invoker("buildChildPatterns")
    void fantasytechnology$buildChildPatterns();

    @Override
    @Invoker("getValidItemTemplates")
    Iterable<InputTemplate> fantasytechnology$getValidItemTemplates(ICraftingInventory inventory);

    @Override
    @Invoker("request")
    void fantasytechnology$request(CraftingSimulationState inventory, long requestedAmount,
            KeyCounter containerItems) throws CraftBranchFailure, InterruptedException;
}
