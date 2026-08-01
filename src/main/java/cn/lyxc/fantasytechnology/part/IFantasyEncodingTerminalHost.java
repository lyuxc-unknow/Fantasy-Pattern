package cn.lyxc.fantasytechnology.part;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

import appeng.api.inventories.InternalInventory;
import appeng.api.storage.ITerminalHost;
import appeng.util.ConfigInventory;

/**
 * The host of a fantasy encoding terminal menu. Implemented by {@link FantasyEncodingTerminalPart}; kept as a separate
 * interface because AE2's {@code MenuTypeBuilder} keys menus on a host interface rather than a concrete class.
 */
public interface IFantasyEncodingTerminalHost extends ITerminalHost {

    /** The ingredients being encoded, up to 81 distinct entries, items or fluids. */
    ConfigInventory getEncodedInputs();

    /** The results being encoded, up to 6 distinct entries, items or fluids. */
    ConfigInventory getEncodedOutputs();

    /** Two real slots: {@code 0} takes blank fantasy patterns, {@code 1} holds the encoded one. */
    InternalInventory getPatternInv();

    /**
     * The id of the tag ingredient slot {@code slot} should match, or null if it requires its exact key. The id is
     * untyped; which registry it refers to follows from the key sitting in the slot.
     *
     * Implementations must return null once the slot no longer holds something covered by the stored tag, so that
     * editing a tagged ingredient turns it back into an exact one.
     */
    @Nullable
    ResourceLocation getInputTag(int slot);

    /** Records the tag ingredient slot {@code slot} was filled from; null makes it an exact ingredient again. */
    void setInputTag(int slot, @Nullable ResourceLocation tag);
}
