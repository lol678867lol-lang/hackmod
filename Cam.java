package de.hackmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Rechnet Welt-Koordinaten in Bildschirm-Koordinaten (GUI-Pixel) um. Nur fuer Ego-Perspektive gedacht. */
public final class Cam {
    public final float pt;
    private final double ex, ey, ez;
    private final double fx, fy, fz;
    private final double rx, rz;
    private final double ux, uy, uz;
    private final double tanHalf, aspect;
    private final int w, h;

    public Cam(Minecraft mc, float pt) {
        this.pt = pt;
        LocalPlayer p = mc.player;
        Vec3 eye = p.getEyePosition(pt);
        ex = eye.x;
        ey = eye.y;
        ez = eye.z;
        double yaw = Math.toRadians(p.getViewYRot(pt));
        double pitch = Math.toRadians(p.getViewXRot(pt));
        double cp = Math.cos(pitch), sp = Math.sin(pitch), sy = Math.sin(yaw), cy = Math.cos(yaw);
        fx = -sy * cp;
        fy = -sp;
        fz = cy * cp;
        rx = -cy;
        rz = -sy;
        // up = right x forward (right.y = 0)
        ux = -rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy;
        w = mc.getWindow().getGuiScaledWidth();
        h = mc.getWindow().getGuiScaledHeight();
        aspect = (double) w / (double) h;
        tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
    }

    /** Projiziert einen Punkt. Gibt false zurueck, wenn er hinter der Kamera liegt. */
    public boolean project(double x, double y, double z, double[] out) {
        double dx = x - ex, dy = y - ey, dz = z - ez;
        double zc = dx * fx + dy * fy + dz * fz;
        if (zc <= 0.05) return false;
        double xc = dx * rx + dz * rz;
        double yc = dx * ux + dy * uy + dz * uz;
        double nx = xc / (zc * tanHalf * aspect);
        double ny = yc / (zc * tanHalf);
        out[0] = (w / 2.0) * (1.0 + nx);
        out[1] = (h / 2.0) * (1.0 - ny);
        return true;
    }

    /** Bildschirm-Rechteck (x1,y1,x2,y2) um eine Box. false, wenn eine Ecke hinter der Kamera liegt. */
    public boolean box(AABB b, double[] out) {
        double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
        double[] p = new double[2];
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? b.minX : b.maxX;
            double y = (i & 2) == 0 ? b.minY : b.maxY;
            double z = (i & 4) == 0 ? b.minZ : b.maxZ;
            if (!project(x, y, z, p)) return false;
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        if (maxX - minX > 4000 || maxY - minY > 4000) return false;
        out[0] = minX;
        out[1] = minY;
        out[2] = maxX;
        out[3] = maxY;
        return true;
    }

    private double depth(double x, double y, double z) {
        return (x - ex) * fx + (y - ey) * fy + (z - ez) * fz;
    }

    /** Zeichnet eine Linie zwischen zwei Weltpunkten (mit Clipping an der Kamera und am Bildschirmrand). */
    public void line(GuiGraphics g, double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        final double near = 0.05;
        double c1 = depth(x1, y1, z1), c2 = depth(x2, y2, z2);
        if (c1 <= near && c2 <= near) return;
        if (c1 <= near) {
            double t = (near + 0.001 - c1) / (c2 - c1);
            x1 += (x2 - x1) * t; y1 += (y2 - y1) * t; z1 += (z2 - z1) * t;
        } else if (c2 <= near) {
            double t = (near + 0.001 - c2) / (c1 - c2);
            x2 += (x1 - x2) * t; y2 += (y1 - y2) * t; z2 += (z1 - z2) * t;
        }
        double[] a = new double[2], b = new double[2];
        if (!project(x1, y1, z1, a) || !project(x2, y2, z2, b)) return;
        double dx = b[0] - a[0], dy = b[1] - a[1];
        double t0 = 0, t1 = 1;
        double[] pp = {-dx, dx, -dy, dy};
        double[] qq = {a[0], w - a[0], a[1], h - a[1]};
        for (int i = 0; i < 4; i++) {
            if (pp[i] == 0) {
                if (qq[i] < 0) return;
            } else {
                double r = qq[i] / pp[i];
                if (pp[i] < 0) {
                    if (r > t1) return;
                    if (r > t0) t0 = r;
                } else {
                    if (r < t0) return;
                    if (r < t1) t1 = r;
                }
            }
        }
        double sx = a[0] + t0 * dx, sy = a[1] + t0 * dy, tx = a[0] + t1 * dx, ty = a[1] + t1 * dy;
        int n = (int) Math.ceil(Math.max(Math.abs(tx - sx), Math.abs(ty - sy)) / 2.0);
        n = Math.max(1, Math.min(n, 1500));
        for (int i = 0; i <= n; i++) {
            int px = (int) (sx + (tx - sx) * i / n);
            int py = (int) (sy + (ty - sy) * i / n);
            g.fill(px, py, px + 2, py + 2, color);
        }
    }

    /** Linie von der Bildschirmmitte (Fadenkreuz) zu einem Weltpunkt. */
    public void tracer(GuiGraphics g, double x, double y, double z, int color) {
        line(g, ex + fx, ey + fy, ez + fz, x, y, z, color);
    }

    /** Bewegungsrichtung (x, z) aus den WASD-Tasten, normalisiert. (0,0) wenn keine Taste gedrueckt ist. */
    public static double[] moveDir(Minecraft mc, float yawDeg) {
        double f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        double s = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        if (f == 0 && s == 0) return new double[]{0, 0};
        double len = Math.sqrt(f * f + s * s);
        f /= len;
        s /= len;
        double yaw = Math.toRadians(yawDeg);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        return new double[]{f * -sin + s * cos, f * cos + s * sin};
    }
}
