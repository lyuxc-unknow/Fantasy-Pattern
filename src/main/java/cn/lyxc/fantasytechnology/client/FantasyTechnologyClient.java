package cn.lyxc.fantasytechnology.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import appeng.init.client.InitScreens;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.client.render.FantasyAnnihilationRenderer;
import cn.lyxc.fantasytechnology.client.screen.FantasyAnnihilationScreen;
import cn.lyxc.fantasytechnology.client.screen.FantasyEncodingTermScreen;
import cn.lyxc.fantasytechnology.menu.FantasyAnnihilationMenu;
import cn.lyxc.fantasytechnology.menu.FantasyEncodingTermMenu;
import cn.lyxc.fantasytechnology.registry.FTBlockEntities;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = FantasyTechnology.MODID, dist = Dist.CLIENT)
public class FantasyTechnologyClient {

    public FantasyTechnologyClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::registerScreens);
        modEventBus.addListener(this::registerRenderers);
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        // The encoding terminal is an AE2 terminal: AE2 builds the screen and reads its layout from the style
        // document, so the registration goes through AE2 rather than through the plain vanilla register call.
        InitScreens.register(event, FantasyEncodingTermMenu.TYPE, FantasyEncodingTermScreen::new,
                FantasyEncodingTermScreen.STYLE);

        // The annihilation block is a plain machine menu, also drawn by AE2's screen framework.
        InitScreens.register(event, FantasyAnnihilationMenu.TYPE, FantasyAnnihilationScreen::new,
                FantasyAnnihilationScreen.STYLE);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FTBlockEntities.FANTASY_ANNIHILATION.get(),
                FantasyAnnihilationRenderer::new);
    }
}
