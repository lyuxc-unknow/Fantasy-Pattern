package cn.lyxc.fantasytechnology.menu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;

/**
 * Menu of the fantasy annihilation block, in the style of AE2's own machine menus (see {@code SkyChestMenu}).
 *
 * Built unregistered so the type can live in this mod's {@code DeferredRegister} rather than AE2's internal
 * registration queue; the block entity is the menu host and is resolved from the open position on the client.
 */
public class FantasyAnnihilationMenu extends AEBaseMenu {

    public static final MenuType<FantasyAnnihilationMenu> TYPE = MenuTypeBuilder
            .create(FantasyAnnihilationMenu::new, FantasyAnnihilationBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(FantasyTechnology.MODID, "fantasy_annihilation"));

    public FantasyAnnihilationMenu(int id, Inventory playerInventory, FantasyAnnihilationBlockEntity annihilation) {
        super(TYPE, id, playerInventory, annihilation);

        var patternInv = annihilation.getPatternInv();
        for (int i = 0; i < patternInv.size(); i++) {
            addSlot(new AppEngSlot(patternInv, i), SlotSemantics.ENCODED_PATTERN);
        }

        createPlayerInventorySlots(playerInventory);
    }
}
