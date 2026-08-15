package net.minenite.client.mixin;

import net.minenite.client.gun.ShiftTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * While Shift is held, swap compact WarZ lore for the PDC {@code tooltip_detail} stats.
 */
@Mixin(ItemStack.class)
public class ShiftTooltipMixin {
	@Inject(method = "getTooltipLines", at = @At("RETURN"))
	private void minenite$shiftTooltip(
			Item.TooltipContext context,
			Player player,
			TooltipFlag tooltipFlag,
			CallbackInfoReturnable<List<Component>> cir) {
		List<Component> lines = cir.getReturnValue();
		if (lines == null || lines.isEmpty()) {
			return;
		}
		if (!(lines instanceof java.util.ArrayList)) {
			lines = new java.util.ArrayList<>(lines);
			cir.setReturnValue(lines);
		}
		ShiftTooltip.expandIfShift((ItemStack) (Object) this, lines);
	}
}
