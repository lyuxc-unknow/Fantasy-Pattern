package cn.lyxc.fantasytechnology.deviceaccess;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.config.DeviceAccessMode;
import cn.lyxc.fantasytechnology.config.FTConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/// Decides whether a recipe may be encoded, given what the network's device access blocks hold.
///
/// The same answer is needed on both sides - the client greys out the transfer button with it, the server re-checks
/// it before applying a transfer - so it lives here rather than in either one:
///
/// 1. the config says {@link DeviceAccessMode#UNRESTRICTED}: everything passes, which is the behaviour from before
///    device access blocks existed;
/// 2. a datapack rule covers this recipe: that rule alone decides, so a pack can demand several machines, offer
///    alternatives, or waive the requirement entirely;
/// 3. no rule covers it: four of the recipe category's own catalysts, the default that needs no data to work.
public final class DeviceAccessCheck {

    /// How many alternatives of one requirement are named in a message before it is cut short. A tag can accept
    /// dozens of items and a tooltip listing all of them is worse than one that lists none.
    private static final int MAX_LISTED_ALTERNATIVES = 4;

    /// The outcome, with enough detail to tell the player exactly what to go and put in the block.
    public record Result(boolean allowed, @Nullable Component reason) {

        private static final Result PASS = new Result(true, null);

        public static Result pass() {
            return PASS;
        }

        public static Result deny(Component reason) {
            return new Result(false, reason);
        }
    }

    private DeviceAccessCheck() {
    }

    public static boolean unrestricted() {
        return FTConfig.DEVICE_ACCESS_MODE.get() == DeviceAccessMode.UNRESTRICTED;
    }

    /// @param recipeId  the recipe's own id, when it has one
    /// @param categoryId the recipe viewer category it came from, when known
    /// @param outputs   the item ids this recipe produces, used to match item-scoped category rules
    /// @param catalysts that category's catalysts, used only by the fallback
    /// @param supply    how many of an item the network's device access blocks hold
    public static Result check(@Nullable ResourceLocation recipeId, @Nullable ResourceLocation categoryId,
            Set<ResourceLocation> outputs, @Nullable Collection<Item> catalysts, ToIntFunction<Item> supply) {
        if (unrestricted()) {
            return Result.pass();
        }

        DeviceRequirement rule = DeviceRequirements.find(recipeId, categoryId, outputs);
        if (rule != null) {
            List<DeviceRequirement.DeviceEntry> unmet = rule.unmetEntries(supply);
            return unmet.isEmpty() ? Result.pass() : Result.deny(describeUnmet(unmet, supply));
        }

        // No rule: the category's own catalysts stand in for "the machine this is made on".
        if (catalysts == null || catalysts.isEmpty()) {
            return Result.deny(Component.translatable("gui.fantasy_technology.transfer_no_catalyst"));
        }
        int have = 0;
        for (Item item : catalysts) {
            have += Math.max(0, supply.applyAsInt(item));
        }
        if (have < DeviceCatalystSnapshot.REQUIRED_CATALYSTS) {
            return Result.deny(Component.translatable("gui.fantasy_technology.transfer_missing_devices",
                    describeRequirement(DeviceCatalystSnapshot.REQUIRED_CATALYSTS, catalysts, have)));
        }
        return Result.pass();
    }

    /// Convenience for the client, which reads the availability out of the synced snapshot.
    public static Result checkOnClient(@Nullable ResourceLocation recipeId, @Nullable ResourceLocation categoryId,
            Set<ResourceLocation> outputs, @Nullable List<Item> catalysts) {
        var snapshot = DeviceCatalystSnapshot.clientSnapshot();
        return check(recipeId, categoryId, outputs, catalysts, item -> snapshot.getOrDefault(item, 0));
    }

    /// The item ids a recipe produces, which is what an item-scoped category rule is matched against. Fluid and
    /// chemical results are skipped: a rule names items.
    public static Set<ResourceLocation> outputItemIds(List<GenericStack> outputs) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (GenericStack stack : outputs) {
            if (stack != null && stack.what() instanceof AEItemKey key) {
                ids.add(BuiltInRegistries.ITEM.getKey(key.getItem()));
            }
        }
        return ids;
    }

    /// "4x Furnace / Blast Furnace (have 2), 1x Chest (have 0)" - every requirement the network is short of, so the
    /// player can read the whole shopping list off one tooltip instead of fixing one and discovering the next.
    private static Component describeUnmet(List<DeviceRequirement.DeviceEntry> unmet, ToIntFunction<Item> supply) {
        MutableComponent list = Component.empty();
        for (int i = 0; i < unmet.size(); i++) {
            if (i > 0) {
                list.append(", ");
            }
            DeviceRequirement.DeviceEntry entry = unmet.get(i);
            list.append(describeRequirement(entry.count(), entry.acceptedItems(), entry.available(supply)));
        }
        return Component.translatable("gui.fantasy_technology.transfer_missing_devices", list);
    }

    /// One requirement as "4x Furnace / Blast Furnace (have 2)".
    private static Component describeRequirement(int count, Collection<Item> alternatives, int have) {
        MutableComponent names = Component.empty();
        int listed = 0;
        for (Item item : alternatives) {
            if (listed == MAX_LISTED_ALTERNATIVES) {
                names.append(Component.translatable("gui.fantasy_technology.transfer_device_more",
                        alternatives.size() - listed));
                break;
            }
            if (listed > 0) {
                names.append(" / ");
            }
            names.append(item.getDescription());
            listed++;
        }
        if (listed == 0) {
            names.append(Component.translatable("gui.fantasy_technology.transfer_device_unknown"));
        }
        return Component.translatable("gui.fantasy_technology.transfer_device_entry", count, names, have);
    }
}
