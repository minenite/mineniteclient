package net.minenite.client.mixin;

import net.minenite.client.gun.NvgVision;
import net.minenite.client.gun.ThermalVision;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

/** NODS use carved pumpkin; hide vanilla pumpkinblur so the tube overlay can read. */
@Mixin(Hud.class)
public class HideNvgPumpkinMixin {
	@Redirect(
			method = "extractCameraOverlays",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/equipment/Equippable;cameraOverlay()Ljava/util/Optional;"))
	private Optional<Identifier> minenite$skipPumpkinOnNods(Equippable equippable) {
		if (NvgVision.hasHelmet() || ThermalVision.hasHelmet()) {
			return Optional.empty();
		}
		return equippable.cameraOverlay();
	}
}
