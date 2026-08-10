package cn.lyxc.fantasytechnology.deviceaccess;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/// One datapack-defined rule: which recipes it covers, and which devices the network must hold to encode them.
///
/// Files live in {@code data/<namespace>/device_access/<name>.json} and look like:
///
/// ```json
/// {
///   "recipes": ["minecraft:diamond_pickaxe"],
///   "categories": [
///     "minecraft:smelting",
///     { "id": "modernindustrialization:electrolyzer", "items": ["minecraft:iron_ingot"] }
///   ],
///   "devices": [
///     { "count": 4, "matches": [{ "item": "minecraft:furnace" }, { "item": "minecraft:blast_furnace" }] },
///     { "count": 1, "matches": { "tag": "c:chests" } }
///   ]
/// }
/// ```
/// Optional-mod rules may use the standard top-level {@code neoforge:conditions} field and are ignored when their
/// conditions do not match.
///
/// A rule applies to a recipe when {@code recipes} names its id, or {@code categories} covers the category it was
/// shown in. Every entry of {@code devices} must then be met - that is the "this recipe needs several machines"
/// case - while the alternatives inside one entry are pooled, which is the "either A or B" case: four of one, or
/// any mix of the two adding up to four.
///
/// A category entry may be a bare id or an object that also lists {@code items}, which narrows it to the recipes in
/// that category producing one of those items. That is the only way to target an individual recipe in a category
/// whose recipes have no id to name - plenty of machine categories are built from data a recipe viewer shows
/// without ever exposing an id.
public record DeviceRequirement(List<ResourceLocation> recipes, List<DeviceRequirement.CategoryMatch> categories,
        List<DeviceRequirement.DeviceEntry> devices) {

    /// A category this rule covers, optionally narrowed to the recipes in it that produce one of {@code items}.
    /// An empty {@code items} list means the whole category.
    public record CategoryMatch(ResourceLocation id, List<ResourceLocation> items) {

        private static final Codec<CategoryMatch> FULL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(CategoryMatch::id),
                ResourceLocation.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(CategoryMatch::items))
                .apply(instance, CategoryMatch::new));

        /// Either the object form or a bare category id, so the common "the whole category" case stays a string.
        public static final Codec<CategoryMatch> CODEC = Codec.either(ResourceLocation.CODEC, FULL_CODEC)
                .xmap(either -> either.map(id -> new CategoryMatch(id, List.of()), match -> match),
                        match -> match.items().isEmpty()
                                ? com.mojang.datafixers.util.Either.left(match.id())
                                : com.mojang.datafixers.util.Either.right(match));

        /// Whether this entry covers a recipe of category {@code categoryId} producing {@code outputs}.
        public boolean matches(ResourceLocation categoryId, Set<ResourceLocation> outputs) {
            if (!id.equals(categoryId)) {
                return false;
            }
            if (items.isEmpty()) {
                return true;
            }
            for (ResourceLocation item : items) {
                if (outputs.contains(item)) {
                    return true;
                }
            }
            return false;
        }

        /// Whether this entry is scoped to particular products, which makes it more specific than a bare category.
        public boolean isScoped() {
            return !items.isEmpty();
        }
    }

    /// One requirement: {@code count} devices in total, drawn from anything {@code matches} accepts.
    public record DeviceEntry(Ingredient matches, int count) {

        /// Deliberately the permissive {@link Ingredient#CODEC} rather than the non-empty one: a non-empty check
        /// resolves the ingredient while decoding, and a tag that has not been populated yet would be rejected even
        /// though it is perfectly good. A genuinely empty ingredient instead ends up accepting nothing, which fails
        /// the rule at the point it is used.
        public static final Codec<DeviceEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Ingredient.CODEC.fieldOf("matches").forGetter(DeviceEntry::matches),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(DeviceEntry::count))
                .apply(instance, DeviceEntry::new));

        /// The distinct items this entry accepts. Tags are already expanded by {@link Ingredient}; on a client that
        /// is against the tags the server sent, which is what the device blocks are checked with anyway.
        public Set<Item> acceptedItems() {
            Set<Item> items = new LinkedHashSet<>();
            for (var stack : matches.getItems()) {
                if (!stack.isEmpty()) {
                    items.add(stack.getItem());
                }
            }
            return items;
        }

        /// How many of what this entry accepts the network holds.
        public int available(ToIntFunction<Item> supply) {
            int total = 0;
            for (Item item : acceptedItems()) {
                total += Math.max(0, supply.applyAsInt(item));
            }
            return total;
        }
    }

    public static final Codec<DeviceRequirement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("recipes", List.of())
                    .forGetter(DeviceRequirement::recipes),
            CategoryMatch.CODEC.listOf().optionalFieldOf("categories", List.of())
                    .forGetter(DeviceRequirement::categories),
            DeviceEntry.CODEC.listOf().fieldOf("devices").forGetter(DeviceRequirement::devices))
            .apply(instance, DeviceRequirement::new));

    /// The entries this network cannot meet, in declaration order. Empty means the rule is satisfied.
    ///
    /// A rule with no entries is satisfied by anything, which is a legitimate way for a pack to say "this recipe
    /// needs no device at all" and override the four-catalyst default.
    public List<DeviceEntry> unmetEntries(ToIntFunction<Item> supply) {
        return devices.stream().filter(entry -> entry.available(supply) < entry.count()).toList();
    }
}
