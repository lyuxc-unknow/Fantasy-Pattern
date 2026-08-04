package cn.lyxc.fantasytechnology.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;

import cn.lyxc.fantasytechnology.crafting.FantasyCraftingPattern;
import cn.lyxc.fantasytechnology.item.FantasyBlankPatternItem;
import cn.lyxc.fantasytechnology.item.FantasyPatternItem;

/// Keeps fantasy patterns out of AE2's own pattern providers (both the block and the panel variant, which share
/// {@link PatternProviderLogic}):
///
/// - a filter on the pattern inventory rejects them for every insertion path that goes through the inventory
///   (pipes, buses, memory card import, the pattern access terminal's quick-fill, and the provider's own GUI,
///   whose slots ask the inventory for permission);
/// - {@code updatePatterns} refuses to decode them, so any fantasy pattern that somehow survived in the slots
///   (e.g. from a save made before this mod version) is never registered with the crafting service and never
///   executed.
///
/// Fantasy patterns stay usable in the pattern access terminal for the fantasy annihilation block, which has its
/// own inventory and is not affected by this mixin.
@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicMixin {

    /// Neither blank nor encoded fantasy patterns belong in an AE2 pattern provider. Held as a singleton because
    /// {@link PatternProviderLogic} has two constructors and the injection below fires for each of them; setting the
    /// same instance twice costs nothing.
    @Unique
    private static final IAEItemFilter FANTASY_TECHNOLOGY$NO_FANTASY_PATTERNS = new IAEItemFilter() {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return !(stack.getItem() instanceof FantasyPatternItem)
                    && !(stack.getItem() instanceof FantasyBlankPatternItem);
        }
    };

    @Shadow
    @Final
    private AppEngInternalInventory patternInventory;

    @Inject(method = "<init>*", at = @At("TAIL"))
    private void fantasyTechnology$restrictPatternInventory(CallbackInfo ci) {
        // AE2 leaves this inventory unfiltered (it builds it with the two-argument AppEngInternalInventory
        // constructor), so nothing of its own is being replaced here.
        this.patternInventory.setFilter(FANTASY_TECHNOLOGY$NO_FANTASY_PATTERNS);
    }

    @Redirect(
            method = "updatePatterns",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/crafting/PatternDetailsHelper;decodePattern"
                            + "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)"
                            + "Lappeng/api/crafting/IPatternDetails;"))
    private IPatternDetails fantasyTechnology$dropFantasyPatterns(ItemStack stack, Level level) {
        IPatternDetails original = PatternDetailsHelper.decodePattern(stack, level);
        return original instanceof FantasyCraftingPattern ? null : original;
    }
}
