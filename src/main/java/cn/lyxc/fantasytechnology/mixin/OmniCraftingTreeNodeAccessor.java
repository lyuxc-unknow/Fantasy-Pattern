/*
 * MIT License
 *
 * Copyright (c) 2026 HibikiShino and OmniSequence: Transfinite contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
