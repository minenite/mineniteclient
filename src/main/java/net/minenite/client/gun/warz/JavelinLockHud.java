package net.minenite.client.gun.warz;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Javelin lock-on box: brackets that close as the seeker acquires, then snap
 * shut and turn red when it has a lock.
 *
 * JavelinService sends locked plus a 0..100 progress; nothing drew it, so
 * locking on gave no feedback at all.
 */
public final class JavelinLockHud {

    private JavelinLockHud() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        int progress = WarzFxStores.javelinProgress();
        boolean locked = WarzFxStores.javelinLocked();
        if (progress <= 0 && !locked) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int cx = w / 2;
        int cy = h / 2;

        float t = locked ? 1f : Mth.clamp(progress / 100f, 0f, 1f);
        // Brackets start wide and close in as the lock builds.
        int size = (int) Mth.lerp(t, Math.min(w, h) * 0.18f, Math.min(w, h) * 0.07f);
        int arm = Math.max(3, size / 3);
        int thick = 2;
        int colour = locked ? ARGB.color(230, 255, 60, 50) : ARGB.color(190, 255, 210, 70);

        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int x = cx + sx * size;
                int y = cy + sy * size;
                int x0 = sx < 0 ? x : x - arm;
                graphics.fill(x0, y, x0 + arm, y + thick, colour);
                int y0 = sy < 0 ? y : y - arm;
                graphics.fill(x, y0, x + thick, y0 + arm, colour);
            }
        }
        if (locked && (System.currentTimeMillis() / 250) % 2 == 0) {
            graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, ARGB.color(230, 255, 60, 50));
        }
    }
}
