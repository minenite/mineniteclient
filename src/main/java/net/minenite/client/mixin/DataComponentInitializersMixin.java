package net.minenite.client.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets a client keep an item whose data this server cannot supply.
 *
 * <p>Items carry references into datapack registries - an armor trim material, a
 * jukebox song - which are resolved during registry sync against what the server
 * sent. A server that does not load the mod never sends those entries, so the
 * lookup throws and the connection dies at {@code finish_configuration}, before
 * the world appears:
 *
 * <pre>
 * IllegalStateException: Missing element
 *   ResourceKey[minecraft:trim_material / biomesoplenty:glowworm_silk]
 *   at Item$Properties.lambda$delayedHolderComponent$0
 *   at RegistryDataCollector.collectGameRegistries
 * </pre>
 *
 * <p>The single failing initializer is skipped instead. The item still exists;
 * it loses only the component that depended on data this server does not have,
 * which is the right outcome - the mod providing that data is not loaded here
 * either, so nothing on this server could have used it.
 *
 * <h2>Why here and not at the lookup</h2>
 *
 * <p>The obvious target is {@code HolderGetter.getOrThrow}, but it returns a
 * {@code Holder.Reference<T>} and there is no way to produce a valid one for an
 * arbitrary registry without inventing a value. Doing so would hand back
 * something broken that fails later and further from the cause. An initializer,
 * by contrast, is already a discrete unit of "give this item this component" -
 * dropping one is a bounded, meaningful action.
 *
 * <p>The enclosing {@code InitializerEntry} is package-private, so it is targeted
 * by name and the redirect is placed on its call to the public
 * {@code Initializer} interface. That keeps this working without an access
 * transformer widening a vanilla class.
 *
 * <h2>What this does not fix</h2>
 *
 * <p>Mods that register network channels still refuse to connect to a server
 * without them; that check happens earlier, during negotiation, and is a
 * deliberate one. This only addresses data the client can do without.
 */
@Mixin(targets = "net.minecraft.core.component.DataComponentInitializers$InitializerEntry")
public class DataComponentInitializersMixin {

    private static final Logger MINENITE$LOGGER = LoggerFactory.getLogger("MineniteClient");

    /**
     * Runs one initializer, keeping the sync alive if it cannot resolve its data.
     *
     * <p>Only {@link IllegalStateException} is caught, which is what a missing
     * registry element raises. Anything else is a different fault and is left to
     * propagate rather than being quietly buried - a client that silently drops
     * unrelated errors here would be worse than one that disconnects.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
            method = "run",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/core/component/DataComponentInitializers$Initializer;"
                            + "run(Lnet/minecraft/core/component/DataComponentMap$Builder;"
                            + "Lnet/minecraft/core/HolderLookup$Provider;"
                            + "Lnet/minecraft/resources/ResourceKey;)V"))
    private void minenite$skipUnresolvableComponent(DataComponentInitializers.Initializer initializer,
                                                    DataComponentMap.Builder builder,
                                                    HolderLookup.Provider registries,
                                                    ResourceKey key) {
        try {
            initializer.run(builder, registries, key);
        } catch (IllegalStateException unresolvable) {
            // Logged at debug: on a server missing a large mod this fires once per
            // affected item, and a wall of warnings would say the same thing
            // hundreds of times.
            // ResourceKey.toString() already prints registry and id, which is the
            // whole of what is worth saying here.
            MINENITE$LOGGER.debug("Skipping a component for {} - this server did not send the data it needs: {}",
                    key, unresolvable.getMessage());
        }
    }
}
