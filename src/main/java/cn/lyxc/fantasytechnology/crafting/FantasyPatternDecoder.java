package cn.lyxc.fantasytechnology.crafting;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.stacks.AEItemKey;

import cn.lyxc.fantasytechnology.item.FantasyPatternItem;

/**
 * Decoder that lets AE2 reconstruct {@link FantasyCraftingPattern} from fantasy pattern item stacks. Required so
 * crafting CPUs can persist and resume jobs across world reloads.
 */
public class FantasyPatternDecoder implements IPatternDetailsDecoder {

    public static final FantasyPatternDecoder INSTANCE = new FantasyPatternDecoder();

    private FantasyPatternDecoder() {
    }

    @Override
    public boolean isEncodedPattern(ItemStack stack) {
        return FantasyPatternItem.getData(stack) != null;
    }

    @Nullable
    @Override
    public IPatternDetails decodePattern(AEItemKey what, Level level) {
        return FantasyCraftingPattern.decode(what, level);
    }
}
