package cn.lyxc.fantasytechnology.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.blockentity.FantasyDeviceAccessBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/// Menu of the device access block: its 36 storage slots and the player inventory, nothing else.
///
/// Built unregistered so the type can live in this mod's {@code DeferredRegister} rather than AE2's internal
/// registration queue; the block entity is the menu host and is resolved from the open position on the client.
public class FantasyDeviceAccessMenu extends AEBaseMenu {

    public static final MenuType<FantasyDeviceAccessMenu> TYPE = MenuTypeBuilder
            .create(FantasyDeviceAccessMenu::new, FantasyDeviceAccessBlockEntity.class)
            .buildUnregistered(
                    ResourceLocation.fromNamespaceAndPath(FantasyTechnology.MODID, "fantasy_device_access"));

    public FantasyDeviceAccessMenu(int id, Inventory playerInventory, FantasyDeviceAccessBlockEntity device) {
        super(TYPE, id, playerInventory, device);

        var inventory = device.getInternalInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            // Plain slots: the per-slot ceiling comes from the inventory itself, so shift-clicking and dragging
            // both respect it without any special handling here.
            addSlot(new AppEngSlot(inventory, slot), SlotSemantics.STORAGE);
        }

        createPlayerInventorySlots(playerInventory);
    }
}
