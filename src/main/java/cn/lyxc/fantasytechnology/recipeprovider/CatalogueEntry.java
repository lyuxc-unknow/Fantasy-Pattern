package cn.lyxc.fantasytechnology.recipeprovider;

import net.minecraft.resources.ResourceLocation;

/// One row of the trusted recipe catalogue: enough to sort, search and re-resolve a recipe without holding the
/// resolved recipe itself.
///
/// {@link #searchKey()} is the lower-cased display name, recipe id and provider id joined by NUL, so ordering by it
/// orders by display name with the recipe id as a tie-break, and a plain {@code contains} matches any of the three.
public record CatalogueEntry(ResourceLocation providerId, ResourceLocation recipeId, long token, String searchKey) {
}
