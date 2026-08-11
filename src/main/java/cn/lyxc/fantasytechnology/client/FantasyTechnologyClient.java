package cn.lyxc.fantasytechnology.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import appeng.init.client.InitScreens;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.client.render.FantasyAnnihilationRenderer;
import cn.lyxc.fantasytechnology.client.screen.FantasyAnnihilationScreen;
import cn.lyxc.fantasytechnology.client.screen.FantasyDeviceAccessScreen;
import cn.lyxc.fantasytechnology.client.screen.FantasyEncodingTermScreen;
import cn.lyxc.fantasytechnology.client.screen.FantasyConfigScreen;
import cn.lyxc.fantasytechnology.integration.jei.FantasyEncodingTransferHandler;
import cn.lyxc.fantasytechnology.menu.FantasyAnnihilationMenu;
import cn.lyxc.fantasytechnology.menu.FantasyDeviceAccessMenu;
import cn.lyxc.fantasytechnology.menu.FantasyEncodingTermMenu;
import cn.lyxc.fantasytechnology.registry.FTBlockEntities;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = FantasyTechnology.MODID, dist = Dist.CLIENT)
public class FantasyTechnologyClient {

    public FantasyTechnologyClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::registerScreens);
        modEventBus.addListener(this::registerRenderers);
        NeoForge.EVENT_BUS.addListener(this::captureBlockedJeiCategories);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new FantasyConfigScreen(parent));
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        // The encoding terminal is an AE2 terminal: AE2 builds the screen and reads its layout from the style
        // document, so the registration goes through AE2 rather than through the plain vanilla register call.
        InitScreens.register(event, FantasyEncodingTermMenu.TYPE, FantasyEncodingTermScreen::new,
                FantasyEncodingTermScreen.STYLE);

        // The annihilation block is a plain machine menu, also drawn by AE2's screen framework.
        InitScreens.register(event, FantasyAnnihilationMenu.TYPE, FantasyAnnihilationScreen::new,
                FantasyAnnihilationScreen.STYLE);

        // The device access block is a plain 36-slot store, same framework again.
        InitScreens.register(event, FantasyDeviceAccessMenu.TYPE, FantasyDeviceAccessScreen::new,
                FantasyDeviceAccessScreen.STYLE);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FTBlockEntities.FANTASY_ANNIHILATION.get(),
                FantasyAnnihilationRenderer::new);
    }

    /// Freeze the server-configured JEI blocklist for this world session. In particular, a config file watcher may
    /// update the underlying value while the world is open, but transfers keep using this snapshot until the player
    /// leaves and joins again.
    private void captureBlockedJeiCategories(ClientPlayerNetworkEvent.LoggingIn event) {
        FantasyEncodingTransferHandler.captureBlockedCategoryIds();
    }
}
