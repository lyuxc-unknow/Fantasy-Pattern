package cn.lyxc.fantasytechnology;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.neoforged.fml.loading.LoadingModList;

import java.util.List;
import java.util.Map;

public final class MinecraftTestBootstrap {
    private static boolean initialized;

    public static synchronized void initialize() {
        if (!initialized) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            // AEKey's codec consults this sentinel even when encoding ordinary vanilla items.
            var items = (MappedRegistry<Item>)
                    net.minecraft.core.registries.BuiltInRegistries.ITEM;
            items.unfreeze();
            Registry.register(items, "ae2:missing_content",
                    new Item(new Item.Properties()));
            items.freeze();
            var keyTypes = new MappedRegistry<AEKeyType>(AEKeyType.REGISTRY_KEY, Lifecycle.stable());
            AEKeyTypesInternal.setRegistry(keyTypes);
            AEKeyTypes.register(AEKeyType.items());
            AEKeyTypes.register(AEKeyType.fluids());
            keyTypes.freeze();
            initialized = true;
        }
    }
}
