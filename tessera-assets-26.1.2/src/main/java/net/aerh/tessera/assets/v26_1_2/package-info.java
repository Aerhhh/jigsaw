/**
 * Minecraft 26.1.2 asset provider. Ships the 26.1.2 manifest and one {@code AssetProvider}
 * implementation registered via {@code META-INF/services/net.aerh.tessera.api.assets.AssetProvider}.
 * No Mojang bytes are bundled; the provider resolves against the consumer's
 * {@code ~/.tessera/assets/26.1.2/} cache populated by {@code TesseraAssets.fetch(...)}.
 */
package net.aerh.tessera.assets.v26_1_2;
