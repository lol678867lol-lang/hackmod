package de.hackmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Freecam, Lunge-Swap und SpearKill. */
public final class Extra2 {
    private Extra2() {}

    // ------------------------------------------------------------------ Freecam

    /**
     * Freie Kamera: Die Kamera loest sich vom Spieler und fliegt mit WASD / Leertaste / Shift (Standard-Tasten).
     * Deine Figur bleibt stehen, die Maus dreht aber weiterhin deine Blickrichtung. Klicks sind gesperrt.
     */
    public static class Freecam extends Module {
        final Setting speed = add(Setting.number("Tempo", 1.0, 0.2, 5.0, 0.2));
        private RemotePlayer cam;
        private boolean fw, bw, le, ri, up, dn;

        Freecam() {
            super("Freecam", "Freie Kamera (WASD, Leertaste hoch, Shift runter). Deine Figur bleibt stehen.", Category.SICHT);
        }

        @Override
        protected void onEnable() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                enabled = false;
                return;
            }
            cam = new RemotePlayer(mc.level, mc.player.getGameProfile());
            cam.copyPosition(mc.player);
            mc.setCameraEntity(cam);
        }

        @Override
        protected void onDisable() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.setCameraEntity(mc.player);
            cam = null;
        }

        @Override
        public void onPreTick(Minecraft mc) {
            if (cam == null) return;
            if (mc.screen != null) {
                fw = bw = le = ri = up = dn = false;
                return;
            }
            long win = GLFW.glfwGetCurrentContext();
            fw = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
            bw = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
            le = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS;
            ri = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;
            up = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
            dn = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
            // Die Figur soll sich nicht bewegen und nichts anklicken
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(false);
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
            mc.options.keyJump.setDown(false);
            mc.options.keyShift.setDown(false);
            mc.options.keySprint.setDown(false);
            mc.options.keyAttack.setDown(false);
            mc.options.keyUse.setDown(false);
        }

        @Override
        public void onTick(Minecraft mc) {
            if (cam == null) return;
            LocalPlayer p = mc.player;
            Vec3 look = p.getViewVector(1.0F);
            double yaw = Math.toRadians(p.getYRot());
            double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
            double f = (fw ? 1 : 0) - (bw ? 1 : 0);
            double r = (ri ? 1 : 0) - (le ? 1 : 0);
            double vx = f * look.x + r * rx;
            double vy = f * look.y + (up ? 1 : 0) - (dn ? 1 : 0);
            double vz = f * look.z + r * rz;
            double s = speed.value * 0.6;
            cam.setOldPosAndRot();
            cam.setPos(cam.getX() + vx * s, cam.getY() + vy * s, cam.getZ() + vz * s);
            cam.setYRot(p.getYRot());
            cam.setXRot(p.getXRot());
            cam.yRotO = p.yRotO;
            cam.xRotO = p.xRotO;
        }
    }

    // ------------------------------------------------------------------ LungeSwap

    /**
     * Linksklick mit einer Windcharge (oder einem Speer) in der Hand:
     * - kein Ziel: du dashst in Blickrichtung
     * - Gegner im Blick: du dashst hin und schlaegst automatisch mit dem Mace aus der Hotbar zu (OneShot).
     */
    public static class LungeSwap extends Module {
        final Setting onWind = add(Setting.toggle("Windcharge-Klick", true));
        final Setting onSpear = add(Setting.toggle("Speer-Klick", false));
        final Setting dash = add(Setting.number("Dash-Kraft", 1.6, 0.5, 4.0, 0.1));
        final Setting range = add(Setting.number("Ziel-Reichweite", 8, 3, 20, 1));
        final Setting oneshot = add(Setting.toggle("OneShot (Fake-Fall)", true));
        final Setting fall = add(Combo.fallSetting());
        final Setting back = add(Setting.toggle("Zurueckwechseln", true));
        private boolean wasDown;
        private int restore = -1;
        private LivingEntity pending;
        private int pendTicks;

        LungeSwap() {
            super("Lunge-Swap", "Linksklick mit Windcharge: Dash. Auf einen Gegner: Dash + Mace-Schlag aus der Hotbar (OneShot).", Category.COMBO);
        }

        @Override
        protected void onDisable() {
            Minecraft mc = Minecraft.getInstance();
            if (restore >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(restore);
            restore = -1;
            pending = null;
        }

        private LivingEntity aimed(Minecraft mc, LocalPlayer p, double r) {
            Vec3 eye = p.getEyePosition();
            Vec3 look = p.getViewVector(1.0F);
            LivingEntity best = null;
            double bestDot = 0.93;
            for (LivingEntity e : Combo.targets(mc, r, true, true)) {
                Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
                double len = d.length();
                if (len < 0.01) continue;
                double dot = d.dot(look) / len;
                if (dot > bestDot) {
                    bestDot = dot;
                    best = e;
                }
            }
            return best;
        }

        private void smash(Minecraft mc, LivingEntity t, boolean manual) {
            LocalPlayer p = mc.player;
            int mace = Combo.slot(p, "mace");
            if (mace < 0) return;
            if (restore < 0) restore = p.getInventory().getSelectedSlot();
            p.getInventory().setSelectedSlot(mace);
            if (oneshot.on) Combo.fakeFall(mc, fall.value);
            if (manual) {
                mc.gameMode.attack(p, t);
                p.swing(InteractionHand.MAIN_HAND);
            }
        }

        @Override
        public void onPreTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            boolean down = mc.options.keyAttack.isDown();
            boolean edge = down && !wasDown;
            wasDown = down;
            if (!edge || mc.screen != null || mc.gameMode == null) return;
            String held = Combo.path(p.getMainHandItem());
            boolean ok = (onWind.on && held.equals("wind_charge")) || (onSpear.on && held.endsWith("_spear"));
            if (!ok) return;

            LivingEntity t = aimed(mc, p, range.value);
            if (t == null) {
                Vec3 look = p.getViewVector(1.0F);
                p.setDeltaMovement(look.scale(dash.value));
                return;
            }
            if (p.distanceTo(t) <= 3.2) {
                boolean crosshair = mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() == t;
                smash(mc, t, !crosshair);
            } else {
                Vec3 dir = t.getBoundingBox().getCenter().subtract(p.position());
                double len = dir.length();
                if (len > 0.01) p.setDeltaMovement(dir.scale(dash.value / len));
                pending = t;
                pendTicks = 14;
            }
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (back.on && restore >= 0) {
                p.getInventory().setSelectedSlot(restore);
                restore = -1;
            }
            if (pending == null || mc.gameMode == null) return;
            if (!pending.isAlive() || --pendTicks < 0) {
                pending = null;
                return;
            }
            if (p.distanceTo(pending) <= 3.3 && p.getAttackStrengthScale(0.0F) >= 0.9F) {
                smash(mc, pending, true);
                pending = null;
            }
        }
    }

    // ------------------------------------------------------------------ SpearKill

    /** Speer in der Hand: jedes Ziel in Reichweite wird per Mace-Smash mit Fake-Fall sofort getoetet. */
    public static class SpearKill extends Module {
        final Setting reach = add(Setting.number("Reichweite", 4.0, 2.0, 6.0, 0.5));
        final Setting fall = add(Combo.fallSetting());
        final Setting players = add(Setting.toggle("Spieler", true));
        final Setting mobs = add(Setting.toggle("Mobs", true));

        SpearKill() {
            super("SpearKill", "Speer in der Hand: Ziele in Reichweite werden per Mace-Smash + Fake-Fall mit einem Schlag getoetet (Mace in der Hotbar).", Category.COMBO);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || mc.screen != null) return;
            if (!Combo.path(p.getMainHandItem()).endsWith("_spear")) return;
            int mace = Combo.slot(p, "mace");
            if (mace < 0 || p.getAttackStrengthScale(0.0F) < 1.0F) return;
            LivingEntity t = Combo.nearest(mc, reach.value, players.on, mobs.on);
            if (t == null) return;
            int prev = p.getInventory().getSelectedSlot();
            p.getInventory().setSelectedSlot(mace);
            Combo.fakeFall(mc, fall.value);
            mc.gameMode.attack(p, t);
            p.swing(InteractionHand.MAIN_HAND);
            p.getInventory().setSelectedSlot(prev);
        }
    }
}
