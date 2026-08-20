package cn.lyxc.fantasytechnology.integration.mekanism;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Soft dependency facade bridging mekanism chemicals into AE2's key system.
///
/// Applied Mekanistics registers every mekanism chemical as {@link me.ramidzkh.mekae2.ae2.MekanismKey}, an {@link appeng.api.stacks.AEKey} subtype under the
/// key type {@code appmek:chemical}, so a chemical fits into a {@link GenericStack}, can be encoded into a fantasy
/// pattern, and is moved by network storage exactly like an item or a fluid.
///
/// Every method checks {@link #isPresent()} before naming a mekanism or applied-mekanistics type, so the mod degrades
/// quietly when either is missing (no chemical slots, no chemical recipe transfer from JEI) instead of crashing.
/// Nothing outside this class should reference their types.
public final class MekanismCompat {

    public static final String MEKANISM_MODID = "mekanism";
    public static final String APPMEK_MODID = "appmek";

    /// Resolved on first use rather than in a static initializer: {@code ModList} does not exist until mod loading has
    /// built it, and there is nothing stopping this class from being touched before that.
    @Nullable
    private static volatile Boolean present;

    private MekanismCompat() {
    }

    /// Whether both mekanism and its AE2 bridge are loaded.
    public static boolean isPresent() {
        Boolean cached = present;
        if (cached != null) {
            return cached;
        }
        ModList modList = ModList.get();
        if (modList == null) {
            // Too early to tell, so do not cache the answer.
            return false;
        }
        boolean loaded = modList.isLoaded(MEKANISM_MODID) && modList.isLoaded(APPMEK_MODID);
        present = loaded;
        return loaded;
    }

    /// Wraps a mekanism chemical stack as an AE2 generic stack; null when the ingredient is not a chemical, is empty,
    /// or the bridging mods are absent.
    ///
    /// Takes {@code Object} on purpose: callers hand over whatever a recipe viewer gave them without having to name -
    /// and therefore load - mekanism's own types.
    @Nullable
    public static GenericStack toGenericStack(Object ingredient) {
        if (!isPresent() || !(ingredient instanceof ChemicalStack stack)) {
            return null;
        }
        // of() returns null for an empty stack.
        MekanismKey key = MekanismKey.of(stack);
        return key == null ? null : new GenericStack(key, stack.getAmount());
    }

    /// Resolves a chemical id for the trusted recipe-provider datapack DSL. Returns null when either bridge mod is
    /// absent, the id is unknown, or it names Mekanism's empty chemical.
    @Nullable
    public static AEKey chemicalKey(ResourceLocation id) {
        if (!isPresent()) {
            return null;
        }
        Holder<Chemical> chemical = MekanismAPI.CHEMICAL_REGISTRY.getHolder(id).orElse(null);
        if (chemical == null) {
            return null;
        }
        ChemicalStack stack = new ChemicalStack(chemical, 1);
        return stack.isEmpty() ? null : MekanismKey.of(stack);
    }

    /// Every chemical in the tag, as AE2 keys; empty when the representative is not a chemical or the bridging mods
    /// are absent.
    ///
    /// Walking a tag needs the registry object itself, which {@code AEKeyType} does not expose - which is why this
    /// has to name mekanism's registry, while the mere "is this key in that tag" question does not (see
    /// {@code PatternIngredient.isInTag}).
    public static List<AEKey> chemicalKeysInTag(AEKey representative, ResourceLocation tagId) {
        List<AEKey> keys = new ArrayList<>();
        if (!isPresent() || !(representative instanceof MekanismKey)) {
            return keys;
        }
        for (Holder<Chemical> holder : MekanismAPI.CHEMICAL_REGISTRY
                .getTagOrEmpty(TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, tagId))) {
            MekanismKey key = MekanismKey.of(new ChemicalStack(holder, 1));
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }
}
