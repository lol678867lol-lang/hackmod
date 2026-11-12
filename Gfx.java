package de.hackmod;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

/** Kleine Zeichen- und Farbhelfer. Alle Farben sind ARGB. */
public final class Gfx {
    private Gfx() {}

    public static int alpha(int rgb, int a) {
        return ((a & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    public static int lerp(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t);
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Pulsierende Akzentfarbe (Lila <-> Tuerkis). offset verschiebt die Phase. */
    public static int accent(double offset) {
        double t = Util.getMillis() / 1800.0 + offset;
        float k = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2));
        return lerp(0xFF8A5CFF, 0xFF2EE6D6, k);
    }

    public static void outline(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }
}
