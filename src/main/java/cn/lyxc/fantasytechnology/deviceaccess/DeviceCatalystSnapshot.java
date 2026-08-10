package cn.lyxc.fantasytechnology.deviceaccess;

import net.minecraft.world.item.Item;

import java.util.Map;

/// The client's view of what the open encoding terminal's network has in its device access blocks.
///
/// Written by {@code DeviceCatalystsPayload} whenever the server's summary changes, read by
/// {@link DeviceAccessCheck} to decide whether a recipe may be transferred. Only one terminal can be
/// open at a time, so a single snapshot is all this needs to be.
///
/// The snapshot is deliberately cleared when the terminal closes: a stale one would let the transfer button stay
/// lit against a network the player is no longer looking at.
public final class DeviceCatalystSnapshot {

    /// How many catalysts a recipe's category needs across the network before it may be encoded. The device access
    /// block's per-slot ceiling is the same number, so one full slot is enough on its own.
    public static final int REQUIRED_CATALYSTS = 4;

    private static volatile Map<Item, Integer> clientSnapshot = Map.of();

    private DeviceCatalystSnapshot() {
    }

    public static void setClientSnapshot(Map<Item, Integer> catalysts) {
        clientSnapshot = Map.copyOf(catalysts);
    }

    public static void clearClientSnapshot() {
        clientSnapshot = Map.of();
    }

    public static Map<Item, Integer> clientSnapshot() {
        return clientSnapshot;
    }
}
