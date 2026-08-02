package cn.lyxc.fantasytechnology.registry;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.menu.FantasyAnnihilationMenu;
import cn.lyxc.fantasytechnology.menu.FantasyEncodingTermMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FTMenus {
    private FTMenus() {
    }

    public static final DeferredRegister<MenuType<?>> REGISTER = DeferredRegister.create(Registries.MENU,
            FantasyTechnology.MODID);

    /// The encoding terminal's menu type is created by AE2's MenuTypeBuilder (which also wires up the opener that
    /// resolves the cable part behind it); we only add the finished type to the registry here.
    public static final DeferredHolder<MenuType<?>, MenuType<FantasyEncodingTermMenu>> FANTASY_ENCODING_TERMINAL = REGISTER
            .register("fantasy_encoding_terminal", () -> FantasyEncodingTermMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<FantasyAnnihilationMenu>> FANTASY_ANNIHILATION = REGISTER
            .register("fantasy_annihilation", () -> FantasyAnnihilationMenu.TYPE);

    /// Forces class loading so the static registrations above run.
    public static void init() {
    }
}
