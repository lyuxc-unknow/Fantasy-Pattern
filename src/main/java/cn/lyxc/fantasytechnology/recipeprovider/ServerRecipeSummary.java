package cn.lyxc.fantasytechnology.recipeprovider;

import appeng.api.stacks.GenericStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/// The small, display-only part of a trusted recipe sent to the terminal UI.
public record ServerRecipeSummary(ResourceLocation providerId, ResourceLocation recipeId, GenericStack displayStack,
        boolean available, Optional<Component> unavailableReason) {

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerRecipeSummary> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.cast(), ServerRecipeSummary::providerId,
            ResourceLocation.STREAM_CODEC.cast(), ServerRecipeSummary::recipeId,
            GenericStack.STREAM_CODEC, ServerRecipeSummary::displayStack,
            ByteBufCodecs.BOOL, ServerRecipeSummary::available,
            ComponentSerialization.OPTIONAL_STREAM_CODEC, ServerRecipeSummary::unavailableReason,
            ServerRecipeSummary::new);
}
