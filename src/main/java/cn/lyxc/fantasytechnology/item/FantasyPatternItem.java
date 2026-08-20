package cn.lyxc.fantasytechnology.item;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.items.misc.WrappedGenericStack;
import appeng.util.InteractionUtil;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.registry.FTComponents;
import cn.lyxc.fantasytechnology.registry.FTItems;
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;


/// The encoded fantasy pattern ("幻梦重组样板"). A single item carrying a processing recipe in its
/// {@link FTComponents#FANTASY_PATTERN_DATA} component; it is produced by encoding a blank fantasy pattern
/// ({@link FantasyBlankPatternItem}) in the fantasy encoding terminal, and shift-right-click turns it back into
/// one - the same round trip AE2's own encoded patterns offer.
///
/// A stack without the component can only come from a save made before blank and encoded patterns were separate
/// items. It has no usable recipe, so it reads as invalid; shift-right-click is what hands it back as a blank
/// pattern rather than leaving it stranded.
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FantasyPatternItem extends Item {

    public FantasyPatternItem(Properties properties) {
        super(properties);
    }

    /// Shift-right-click turns the pattern back into the blank fantasy patterns it was made from, mirroring
    /// {@code EncodedPatternItem#use}: the result is reported as a success on both sides either way, so the click
    /// is not passed on to whatever is behind it.
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        clearPattern(player.getItemInHand(hand), player);
        return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide()),
                player.getItemInHand(hand));
    }

    /// Replaces the held stack with the same number of blank patterns. Server side only, and the whole stack goes at
    /// once, which is why it has to find the stack by identity in the player's inventory - exactly how AE2 does it.
    private static void clearPattern(ItemStack stack, Player player) {
        if (!InteractionUtil.isInAlternateUseMode(player) || player.getCommandSenderWorld().isClientSide()) {
            return;
        }
        ItemStack blanks = new ItemStack(FTItems.FANTASY_BLANK_PATTERN.get(), stack.getCount());
        if (blanks.isEmpty()) {
            return;
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i) == stack) {
                inv.setItem(i, blanks);
                return;
            }
        }
    }

    @Nullable
    public static FantasyPatternData getData(ItemStack stack) {
        if (stack.getItem() instanceof FantasyPatternItem) {
            return stack.get(FTComponents.FANTASY_PATTERN_DATA);
        }
        return null;
    }

    public static boolean isEncoded(ItemStack stack) {
        return getData(stack) != null;
    }

    /// Simple client-side caching, mirroring AE2's own encoded pattern item.
    private static final Map<ItemStack, ItemStack> OUTPUT_CACHE = new WeakHashMap<>();

    /// The item stack that should be shown for this pattern when shift is held down or in pattern-related GUIs: the
    /// first result of the encoded recipe. Items come back as a stack of one; fluids and chemicals as a wrapped
    /// generic stack, whose amount is irrelevant because only the icon is ever drawn. Mirrors
    /// {@code EncodedPatternItem#getOutput}.
    public static ItemStack getOutput(ItemStack stack) {
        ItemStack out = OUTPUT_CACHE.get(stack);
        if (out != null) {
            return out;
        }

        FantasyPatternData data = getData(stack);
        out = ItemStack.EMPTY;
        if (data != null && data.isCraftable()) {
            List<GenericStack> outputs = data.outputs();
            if (!outputs.isEmpty()) {
                GenericStack output = outputs.getFirst();
                if (output.what() instanceof AEItemKey itemKey) {
                    out = itemKey.toStack();
                } else {
                    out = WrappedGenericStack.wrap(output.what(), 0);
                }
            }
        }

        OUTPUT_CACHE.put(stack, out);
        return out;
    }

    /// Creates an encoded fantasy pattern from a set of ingredients and results.
    public static ItemStack encode(List<PatternIngredient> inputs, List<GenericStack> outputs) {
        return encode(inputs, outputs, List.of());
    }

    /// Creates an encoded fantasy pattern, including the per-result ignore-data flags.
    public static ItemStack encode(List<PatternIngredient> inputs, List<GenericStack> outputs,
            List<Boolean> outputsIgnore) {
        return encode(inputs, outputs, outputsIgnore, Optional.empty());
    }

    /// Creates an encoded pattern authenticated by a server recipe token. The token is omitted for JEI/client-auth
    /// patterns; only a server-side terminal action can supply a non-empty value.
    public static ItemStack encode(List<PatternIngredient> inputs, List<GenericStack> outputs,
            List<Boolean> outputsIgnore, Optional<Long> serverRecipeToken) {
        ItemStack pattern = new ItemStack(FTItems.FANTASY_PATTERN.get());
        pattern.set(FTComponents.FANTASY_PATTERN_DATA,
                new FantasyPatternData(inputs, outputs, outputsIgnore, serverRecipeToken));
        return pattern;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        FantasyPatternData data = getData(stack);
        if (data == null || !data.isCraftable()) {
            // Since blank and encoded patterns were split into separate items, a recombination pattern without
            // usable recipe data can only come from an old save; treat it as invalid, like AE2 does.
            tooltip.add(Component.translatable("item.fantasy_technology.fantasy_pattern.invalid")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        // A pattern is only valid in the authorization mode it was written in, and flipping that config leaves working
        // patterns in place that silently stop crafting. Say so here rather than letting the player guess.
        if (FTConfig.SPEC.isLoaded()
                && data.serverRecipeToken().isPresent() != FTConfig.TRUST_SERVER_RECIPE_PARSING.get()) {
            tooltip.add(Component.translatable(data.serverRecipeToken().isPresent()
                    ? "item.fantasy_technology.fantasy_pattern.needs_trusted_mode"
                    : "item.fantasy_technology.fantasy_pattern.needs_client_mode")
                    .withStyle(ChatFormatting.RED));
        }

        tooltip.add(Component.translatable("item.fantasy_technology.fantasy_pattern.outputs")
                .withStyle(ChatFormatting.GRAY));
        for (GenericStack output : data.outputs()) {
            tooltip.add(Component.literal("  ")
                    .append(output.what().getDisplayName())
                    .append(" x" + formatAmount(output))
                    .withStyle(ChatFormatting.AQUA));
        }

        Map<FantasyPatternData.IngredientKey, Long> required = data.requiredIngredients();
        tooltip.add(Component.translatable("item.fantasy_technology.fantasy_pattern.ingredients")
                .withStyle(ChatFormatting.GRAY));
        for (var entry : required.entrySet()) {
            FantasyPatternData.IngredientKey key = entry.getKey();
            // Tag-matched ingredients read as "#minecraft:planks" and are highlighted, so it is obvious at a glance
            // which parts of the recipe accept substitutes.
            tooltip.add(Component.literal("  ")
                    .append(key.displayName())
                    .append(" x" + formatAmount(key.representative(), entry.getValue()))
                    .withStyle(key.isTagged() ? ChatFormatting.DARK_AQUA : ChatFormatting.DARK_GRAY));
        }
    }

    private static String formatAmount(GenericStack stack) {
        return formatAmount(stack.what(), stack.amount());
    }

    /// Fluids and chemicals are counted in millibuckets internally; showing raw numbers there would be unreadable.
    private static String formatAmount(appeng.api.stacks.AEKey key, long amount) {
        // Both fluids and applied-mekanistics chemicals report 1 bucket per unit, so a single check covers them.
        if (key.getType().getAmountPerUnit() == AEFluidKey.AMOUNT_BUCKET) {
            if (amount % AEFluidKey.AMOUNT_BUCKET == 0) {
                return (amount / AEFluidKey.AMOUNT_BUCKET) + "B";
            }
            return amount + "mB";
        }
        return Long.toString(amount);
    }
}
