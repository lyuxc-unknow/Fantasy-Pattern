package cn.lyxc.fantasytechnology.item;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.registry.FTComponents;
import cn.lyxc.fantasytechnology.registry.FTItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Map;


/// The fantasy pattern ("幻梦样板"). A single item that can be empty (unencoded) or encoded with a processing recipe
/// stored in its {@link FTComponents#FANTASY_PATTERN_DATA} component.
@ParametersAreNonnullByDefault
public class FantasyPatternItem extends Item {

    public FantasyPatternItem(Properties properties) {
        super(properties);
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

    /// Creates an encoded fantasy pattern from a set of ingredients and results.
    public static ItemStack encode(List<PatternIngredient> inputs, List<GenericStack> outputs) {
        ItemStack pattern = new ItemStack(FTItems.FANTASY_PATTERN.get());
        pattern.set(FTComponents.FANTASY_PATTERN_DATA, new FantasyPatternData(inputs, outputs));
        return pattern;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        FantasyPatternData data = getData(stack);
        if (data == null || !data.isCraftable()) {
            tooltip.add(Component.translatable("item.fantasy_technology.fantasy_pattern.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
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
