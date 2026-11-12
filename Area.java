package de.hackmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Die Mine-Area: zwei Ecken (Pos1/Pos2), die du mit den Tasten Z und X setzt. */
public final class Area {
    private Area() {}

    public static BlockPos p1, p2;

    public static boolean isSet() { return p1 != null && p2 != null; }

    public static int minX() { return Math.min(p1.getX(), p2.getX()); }
    public static int minY() { return Math.min(p1.getY(), p2.getY()); }
    public static int minZ() { return Math.min(p1.getZ(), p2.getZ()); }
    public static int maxX() { return Math.max(p1.getX(), p2.getX()); }
    public static int maxY() { return Math.max(p1.getY(), p2.getY()); }
    public static int maxZ() { return Math.max(p1.getZ(), p2.getZ()); }

    public static String sizeText() {
        return (maxX() - minX() + 1) + "x" + (maxY() - minY() + 1) + "x" + (maxZ() - minZ() + 1);
    }

    /** Setzt Ecke 1 oder 2 auf den Block, den du ansiehst (sonst auf deinen Standort). */
    public static void set(Minecraft mc, int which) {
        if (mc.player == null) return;
        BlockPos pos = mc.player.blockPosition();
        HitResult hr = mc.hitResult;
        if (hr != null && hr.getType() == HitResult.Type.BLOCK && hr instanceof BlockHitResult bhr) {
            pos = bhr.getBlockPos();
        }
        if (which == 1) p1 = pos; else p2 = pos;
        String msg = "Mine-Area Pos" + which + ": " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        if (isSet()) msg += "  (" + sizeText() + ")";
        mc.player.displayClientMessage(Component.literal(msg), true);
    }

    public static void clear(Minecraft mc) {
        p1 = null;
        p2 = null;
        if (mc.player != null) mc.player.displayClientMessage(Component.literal("Mine-Area geloescht"), true);
    }

    /** Zeichnet den Quader der Area als Drahtgitter. */
    public static void draw(GuiGraphics g, Minecraft mc, Cam cam) {
        if (!isSet()) return;
        double x0 = minX(), y0 = minY(), z0 = minZ();
        double x1 = maxX() + 1, y1 = maxY() + 1, z1 = maxZ() + 1;
        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) {
            c[i] = new double[]{(i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1};
        }
        int col = Gfx.accent(0.3);
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            cam.line(g, c[e[0]][0], c[e[0]][1], c[e[0]][2], c[e[1]][0], c[e[1]][1], c[e[1]][2], col);
        }
    }
}
