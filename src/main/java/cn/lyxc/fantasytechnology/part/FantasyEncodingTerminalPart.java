package cn.lyxc.fantasytechnology.part;

import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractTerminalPart;
import appeng.util.ConfigInventory;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.item.FantasyBlankPatternItem;
import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import cn.lyxc.fantasytechnology.item.FantasyPatternItem;
import cn.lyxc.fantasytechnology.item.PatternIngredient;
import cn.lyxc.fantasytechnology.menu.FantasyEncodingTermMenu;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/// The fantasy encoding terminal ("幻梦编码终端"), a cable part in the style of AE2's own pattern encoding terminal.
///
/// Being a terminal part rather than a standalone block, it is attached to an ME network and its menu shows the
/// network's contents, so recipe ingredients can be dragged straight out of network storage into the crafting grid.
public class FantasyEncodingTerminalPart extends AbstractTerminalPart implements IFantasyEncodingTerminalHost {

    /// Ingredient slots, laid out as 3 columns the UI scrolls through.
    public static final int INPUT_SLOTS = FantasyPatternData.MAX_INPUTS;
    /// Result slots, shown as a fixed 3x3 block.
    public static final int OUTPUT_SLOTS = FantasyPatternData.MAX_OUTPUTS;

    public static final int BLANK_PATTERN_SLOT = 0;
    public static final int ENCODED_PATTERN_SLOT = 1;

    public static final ResourceLocation MODEL_OFF = ResourceLocation
            .fromNamespaceAndPath(FantasyTechnology.MODID, "part/fantasy_encoding_terminal_off");
    public static final ResourceLocation MODEL_ON = ResourceLocation
            .fromNamespaceAndPath(FantasyTechnology.MODID, "part/fantasy_encoding_terminal_on");

    /// MODEL_BASE and the status overlays are AE2's shared display-part models, inherited from AbstractDisplayPart.
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    /// All models this part can display; handed to AE2 during client setup.
    public static List<ResourceLocation> getModels() {
        return List.of(MODEL_OFF, MODEL_ON);
    }

    /// The ingredients being encoded. Config inventories (rather than real ones) so entries can be dropped in from the
    /// ME network view without consuming anything, and so each entry carries an amount. Every registered AE2 key type
    /// is accepted - items and fluids natively, MEK chemicals through applied-mekanistics' {@code appmek:chemical}
    /// type - which is what lets a recipe with a liquid or chemical ingredient be encoded at all.
    private final ConfigInventory encodedInputs = ConfigInventory.configStacks(INPUT_SLOTS)
            .supportedTypes(AEKeyTypes.getAll())
            // Like AE2's own pattern terminal, items may exceed their stack size so recipes with more than a
            // stack of an item (and the double-amounts button) work; without this the amount would be clamped to 64.
            .allowOverstacking(true)
            .changeListener(this::saveChanges)
            .build();

    private final ConfigInventory encodedOutputs = ConfigInventory.configStacks(OUTPUT_SLOTS)
            .supportedTypes(AEKeyTypes.getAll())
            // Same overstacking allowance as the inputs, so results are not clamped to one stack size either.
            .allowOverstacking(true)
            .changeListener(this::saveChanges)
            .build();

    /// Slot 0 takes blank fantasy patterns, slot 1 holds the encoded one - and decodes it back for editing.
    private final AppEngInternalInventory patternInv = new AppEngInternalInventory(this, 2, 64,
            new PatternSlotFilter());

    /// The tag each ingredient slot was filled from, or null where the ingredient is an exact key.
    ///
    /// A config inventory can only hold a key plus an amount, so the tag has to live beside it. Entries are never
    /// cleared explicitly: {@link #getInputTag(int)} discards a tag that no longer covers the slot's current contents,
    /// so replacing a tagged ingredient by hand automatically turns it back into an exact one.
    private final ResourceLocation[] inputTags = new ResourceLocation[INPUT_SLOTS];

    /// Whether each ingredient slot ignores data components when matched (right-click toggled in the terminal).
    private final boolean[] inputIgnore = new boolean[INPUT_SLOTS];

    /// Whether each result slot ignores data components; display and re-editing only, results have no matching step.
    private final boolean[] outputIgnore = new boolean[OUTPUT_SLOTS];

    public FantasyEncodingTerminalPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public ConfigInventory getEncodedInputs() {
        return encodedInputs;
    }

    @Override
    public ConfigInventory getEncodedOutputs() {
        return encodedOutputs;
    }

    @Override
    public InternalInventory getPatternInv() {
        return patternInv;
    }

    @Nullable
    @Override
    public ResourceLocation getInputTag(int slot) {
        ResourceLocation id = inputTags[slot];
        if (id == null) {
            return null;
        }
        // Stale tag: the slot now holds something the tag does not cover, so treat it as an exact ingredient.
        AEKey key = encodedInputs.getKey(slot);
        return key != null && PatternIngredient.isInTag(key, id) ? id : null;
    }

    @Override
    public void setInputTag(int slot, @Nullable ResourceLocation tag) {
        inputTags[slot] = tag;
        saveChanges();
    }

    @Override
    public boolean getInputIgnore(int slot) {
        return inputIgnore[slot];
    }

    @Override
    public void setInputIgnore(int slot, boolean ignore) {
        inputIgnore[slot] = ignore;
        saveChanges();
    }

    @Override
    public boolean getOutputIgnore(int slot) {
        return outputIgnore[slot];
    }

    @Override
    public void setOutputIgnore(int slot, boolean ignore) {
        outputIgnore[slot] = ignore;
        saveChanges();
    }

    @Override
    public MenuType<?> getMenuType(Player player) {
        return FantasyEncodingTermMenu.TYPE;
    }

    @Override
    public IPartModel getStaticModels() {
        return selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        // The ghost ingredients are virtual and must not drop; the two pattern slots hold real items.
        for (int i = 0; i < patternInv.size(); i++) {
            ItemStack stack = patternInv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        patternInv.clear();
        encodedInputs.clear();
        encodedOutputs.clear();
        Arrays.fill(inputTags, null);
        Arrays.fill(inputIgnore, false);
        Arrays.fill(outputIgnore, false);
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries);
        encodedInputs.readFromChildTag(tag, "encodedInputs", registries);
        encodedOutputs.readFromChildTag(tag, "encodedOutputs", registries);
        patternInv.readFromNBT(tag, "patternInv", registries);

        Arrays.fill(inputTags, null);
        CompoundTag tags = tag.getCompound("inputTags");
        for (String key : tags.getAllKeys()) {
            int slot = Integer.parseInt(key);
            if (slot >= 0 && slot < inputTags.length) {
                inputTags[slot] = ResourceLocation.tryParse(tags.getString(key));
            }
        }
        readBooleanArray(tag, "inputIgnore", inputIgnore);
        readBooleanArray(tag, "outputIgnore", outputIgnore);
    }

    /// Fills {@code target} from a boolean array stored as NBT bytes; a missing or shorter entry leaves the rest
    /// false, so a part saved before the flags existed simply reads as all-off.
    private static void readBooleanArray(CompoundTag tag, String key, boolean[] target) {
        byte[] loaded = tag.getByteArray(key);
        for (int i = 0; i < target.length; i++) {
            target[i] = i < loaded.length && loaded[i] != 0;
        }
    }

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);
        encodedInputs.writeToChildTag(tag, "encodedInputs", registries);
        encodedOutputs.writeToChildTag(tag, "encodedOutputs", registries);
        patternInv.writeToNBT(tag, "patternInv", registries);

        CompoundTag tags = new CompoundTag();
        for (int i = 0; i < inputTags.length; i++) {
            if (inputTags[i] != null) {
                tags.putString(Integer.toString(i), inputTags[i].toString());
            }
        }
        if (!tags.isEmpty()) {
            tag.put("inputTags", tags);
        }
        writeBooleanArray(tag, "inputIgnore", inputIgnore);
        writeBooleanArray(tag, "outputIgnore", outputIgnore);
    }

    /// Stores a boolean array as NBT bytes, skipping the key entirely while every flag is still off - which is the
    /// normal case, and matches how the tags above are written.
    private static void writeBooleanArray(CompoundTag tag, String key, boolean[] values) {
        byte[] result = new byte[values.length];
        boolean any = false;
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) (values[i] ? 1 : 0);
            any |= values[i];
        }
        if (any) {
            tag.putByteArray(key, result);
        }
    }

    /// The blank pattern slot only takes blank fantasy patterns; the encoded slot only takes encoded recombination
    /// patterns, which the menu then decodes back into the grid so the recipe can be edited.
    private static class PatternSlotFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return switch (slot) {
                case BLANK_PATTERN_SLOT -> stack.getItem() instanceof FantasyBlankPatternItem;
                case ENCODED_PATTERN_SLOT -> FantasyPatternItem.isEncoded(stack);
                default -> false;
            };
        }

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return true;
        }
    }
}
