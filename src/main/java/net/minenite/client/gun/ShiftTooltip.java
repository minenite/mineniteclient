package net.minenite.client.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * WarZ compact lore expands while Shift is held. Detail is stored on the item as
 * Bukkit PDC {@code pvpgunminus:tooltip_detail} (legacy {@code &} lines, RS-separated).
 */
public final class ShiftTooltip {
	public static final String DETAIL_SEP = "\u001e";

	private ShiftTooltip() {
	}

	public static void expandIfShift(ItemStack stack, List<Component> lines) {
		if (stack == null || stack.isEmpty() || lines == null || lines.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || !mc.hasShiftDown()) {
			return;
		}
		String raw = GunAttachmentVisuals.pdcStringRaw(stack, "tooltip_detail");
		if (raw == null || raw.isBlank()) {
			return;
		}
		List<Component> detail = parseDetail(raw);
		if (detail.isEmpty()) {
			return;
		}
		int hold = indexOfHoldShift(lines);
		int from = 1;
		int to = hold >= 0 ? hold + 1 : 1;
		if (to > lines.size()) {
			to = lines.size();
		}
		if (from < to) {
			lines.subList(from, to).clear();
		}
		int at = Math.min(1, lines.size());
		String namePlain = lines.isEmpty() ? "" : lines.get(0).getString().trim();
		for (Component extra : detail) {
			String plain = extra.getString().trim();
			if (plain.isEmpty()) {
				continue;
			}
			if (!namePlain.isEmpty() && plain.equalsIgnoreCase(namePlain)) {
				continue;
			}
			lines.add(at++, extra);
		}
	}

	private static int indexOfHoldShift(List<Component> lines) {
		for (int i = 1; i < lines.size(); i++) {
			String plain = lines.get(i).getString().toLowerCase(Locale.ROOT);
			if (plain.contains("hold") && plain.contains("shift")) {
				return i;
			}
		}
		return -1;
	}

	private static List<Component> parseDetail(String raw) {
		List<Component> out = new ArrayList<>();
		String[] parts = raw.split(DETAIL_SEP, -1);
		for (String part : parts) {
			if (part == null || part.isBlank()) {
				continue;
			}
			out.add(parseLegacy(part));
		}
		return out;
	}

	static Component parseLegacy(String raw) {
		MutableComponent root = Component.empty();
		Style style = Style.EMPTY.withItalic(false);
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if ((c == '&' || c == '\u00a7') && i + 1 < raw.length()) {
				flush(root, buf, style);
				char code = Character.toLowerCase(raw.charAt(++i));
				ChatFormatting fmt = ChatFormatting.getByCode(code);
				if (fmt == null) {
					buf.append(c).append(raw.charAt(i));
					continue;
				}
				boolean color = fmt.ordinal() <= ChatFormatting.WHITE.ordinal();
				if (color || fmt == ChatFormatting.RESET) {
					style = Style.EMPTY.withItalic(false);
					if (color) {
						style = style.withColor(fmt);
					}
				} else {
					style = style.applyFormat(fmt);
				}
			} else {
				buf.append(c);
			}
		}
		flush(root, buf, style);
		return root;
	}

	private static void flush(MutableComponent root, StringBuilder buf, Style style) {
		if (buf.isEmpty()) {
			return;
		}
		root.append(Component.literal(buf.toString()).setStyle(style));
		buf.setLength(0);
	}
}
