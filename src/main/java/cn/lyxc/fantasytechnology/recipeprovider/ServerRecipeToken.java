package cn.lyxc.fantasytechnology.recipeprovider;

import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.item.PatternIngredient;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/// Opaque numeric identity for a server-owned recipe.
///
/// This is a fingerprint of the complete resolved recipe rather than a client-readable id, so a datapack reload or a
/// recipe change invalidates old server-authenticated patterns instead of silently executing the previous inputs.
///
/// It deliberately is not a secret, and does not need to be one. A client that invents a token either matches no
/// recipe - in which case the pattern is refused outright - or matches a real one, in which case
/// {@link cn.lyxc.fantasytechnology.crafting.FantasyCraftingPattern#decode} replaces the pattern's inputs and outputs
/// with that recipe's own. Forging a token therefore buys nothing beyond selecting a recipe the player could have
/// selected in the terminal anyway. The 64-bit truncation only bounds accidental collisions, which are detected and
/// logged where {@link ServerRecipeProviders} builds its token indexes.
public final class ServerRecipeToken {

    private ServerRecipeToken() {
    }

    public static long of(ResourceLocation providerId, ResourceLocation recipeId,
            @Nullable ResourceLocation categoryId, List<PatternIngredient> inputs, List<GenericStack> outputs,
            List<Boolean> outputsIgnore) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, providerId.toString());
            update(digest, recipeId.toString());
            update(digest, categoryId == null ? "" : categoryId.toString());
            for (PatternIngredient input : inputs) {
                update(digest, input.what().toString());
                update(digest, Long.toString(input.amount()));
                update(digest, input.tag().map(Object::toString).orElse(""));
                update(digest, Boolean.toString(input.ignoreData()));
            }
            update(digest, "|outputs|");
            for (GenericStack output : outputs) {
                update(digest, output.what().toString());
                update(digest, Long.toString(output.amount()));
            }
            for (Boolean ignored : outputsIgnore) {
                update(digest, Boolean.toString(ignored));
            }
            return ByteBuffer.wrap(digest.digest()).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
