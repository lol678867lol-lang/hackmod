package de.hackmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Weitere Hacks: Bewegung, Kampf, Schutz, Bauen und viele HUD-Anzeigen. */
public final class Extra4 {
    private Extra4() {}

    private static void right(GuiGraphics g, Minecraft mc, String s, int y, int color) {
        int W = mc.getWindow().getGuiScaledWidth();
        g.drawString(mc.font, s, W - mc.font.width(s) - 6, y, color);
    }

    // ------------------------------------------------------------------ Bewegung

    /** Im Wasser automatisch nach oben schwimmen (wie Leertaste halten). */
    public static class AutoSwim extends Module {
        AutoSwim() {
            super("AutoSwim", "Im Wasser oder in Lava wird automatisch Springen gehalten - du schwimmst nach oben.", Category.BEWEGUNG);
        }

        @Override
        public void onPreTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p == null || mc.screen != null) return;
            if ((p.isInWater() || p.isInLava()) && !mc.options.keyShift.isDown()) mc.options.keyJump.setDown(true);
        }
    }

    /** Schneller im Wasser. */
    public static class WaterSpeed extends Module {
        final Setting factor = add(Setting.number("Faktor", 1.15, 1.0, 1.6, 0.05));

        WaterSpeed() {
            super("WaterSpeed", "Du bist im Wasser schneller (Delfin-Gefuehl).", Category.BEWEGUNG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (!p.isInWater() || p.onGround()) return;
            Vec3 m = p.getDeltaMovement();
            double f = factor.value;
            if (m.horizontalDistance() < 0.5) p.setDeltaMovement(m.x * f, m.y, m.z * f);
        }
    }

    /** Beim Gleiten mit Elytra: Leertaste halten = Schub in Blickrichtung. */
    public static class ElytraBoost extends Module {
        final Setting power = add(Setting.number("Schub", 0.06, 0.02, 0.2, 0.02));
        final Setting max = add(Setting.number("Max. Tempo", 2.5, 1.0, 5.0, 0.25));

        ElytraBoost() {
            super("ElytraBoost", "Elytra-Flug: Leertaste halten gibt Schub in Blickrichtung (kein Raketen-Feuerwerk noetig).", Category.FLUG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (!p.isFallFlying() || mc.screen != null || !mc.options.keyJump.isDown()) return;
            Vec3 m = p.getDeltaMovement();
            if (m.length() >= max.value) return;
            p.setDeltaMovement(m.add(p.getViewVector(1.0F).scale(power.value)));
        }
    }

    // ------------------------------------------------------------------ Kampf / Schutz

    /** Springt automatisch, wenn du ein Ziel im Visier hast, damit der Schlag ein Crit wird. */
    public static class AutoCrit extends Module {
        AutoCrit() {
            super("AutoCrit", "Zielst du auf einen Gegner und dein Schlag ist bereit, springst du automatisch - der naechste Schlag ist ein kritischer Treffer.", Category.KAMPF);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.screen != null || !p.onGround() || p.isInWater()) return;
            if (!(mc.hitResult instanceof EntityHitResult ehr) || !(ehr.getEntity() instanceof LivingEntity)) return;
            if (p.getAttackStrengthScale(0.0F) < 0.9F) return;
            Vec3 m = p.getDeltaMovement();
            p.setDeltaMovement(m.x, 0.42, m.z);
        }
    }

    /** Kreist um das naechste Ziel (links/rechts), waehrend du auf es zugehst. */
    public static class TargetStrafe extends Module {
        final Setting range = add(Setting.number("Reichweite", 5, 3, 8, 1));
        final Setting players = add(Setting.toggle("Spieler", true));
        final Setting mobs = add(Setting.toggle("Mobs", false));
        private boolean left;
        private int t;

        TargetStrafe() {
            super("TargetStrafe", "Naeher als die Reichweite am Gegner: du laeufst automatisch im Kreis um ihn (schwerer zu treffen).", Category.KAMPF);
        }

        @Override
        public void onPreTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p == null || mc.screen != null) return;
            LivingEntity e = Combo.nearest(mc, range.value, players.on, mobs.on);
            if (e == null) return;
            if (++t > 25 || p.horizontalCollision) {
                t = 0;
                left = !left;
            }
            mc.options.keyLeft.setDown(left);
            mc.options.keyRight.setDown(!left);
        }
    }

    /** Verringert den Rueckstoss nach einem Treffer. */
    public static class AntiKnockback extends Module {
        final Setting strength = add(Setting.number("Staerke", 0.6, 0.1, 1.0, 0.1));

        AntiKnockback() {
            super("AntiKnockback", "Reduziert den Rueckstoss, wenn du getroffen wirst (client-seitig; manche Server korrigieren das wieder).", Category.SCHUTZ);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p.hurtTime < 8) return;
            Vec3 m = p.getDeltaMovement();
            double f = 1.0 - strength.value;
            p.setDeltaMovement(m.x * f, m.y, m.z * f);
        }
    }

    // ------------------------------------------------------------------ Bauen

    /** Setzt automatisch Fackeln, wenn es dunkel wird. */
    public static class AutoTorch extends Module {
        final Setting level = add(Setting.number("Lichtlevel unter", 7, 1, 10, 1));
        private int cd;

        AutoTorch() {
            super("AutoTorch", "Setzt Fackeln unter dich, sobald das Licht unter dem Level liegt (Fackeln in die Hotbar).", Category.BAUEN);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (cd-- > 0 || mc.screen != null || mc.gameMode == null || !p.onGround()) return;
            BlockPos feet = p.blockPosition();
            if (mc.level.getMaxLocalRawBrightness(feet) >= level.value) return;
            if (!mc.level.getBlockState(feet).isAir()) return;
            BlockPos floor = feet.below();
            if (mc.level.getBlockState(floor).getCollisionShape(mc.level, floor).isEmpty()) return;
            int slot = -1;
            for (int i = 0; i < 9; i++) {
                String n = Combo.path(p.getInventory().getItem(i));
                if (n.equals("torch")) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) return;
            int prev = p.getInventory().getSelectedSlot();
            p.getInventory().setSelectedSlot(slot);
            Modules.placeBlockAt(mc, floor);
            p.getInventory().setSelectedSlot(prev);
            cd = 10;
        }
    }

    // ------------------------------------------------------------------ HUD

    public static class CoordsHud extends Module {
        CoordsHud() {
            super("Koordinaten", "Zeigt X/Y/Z, Blickrichtung und Nether-/Oberwelt-Umrechnung.", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p == null) return;
            int x = p.getBlockX(), y = p.getBlockY(), z = p.getBlockZ();
            right(g, mc, "XYZ " + x + " " + y + " " + z, 6, 0xFFFFFFFF);
            right(g, mc, "Nether " + (x / 8) + " " + (z / 8) + "  |  Oberwelt " + (x * 8) + " " + (z * 8), 18, 0xFFB8B8C8);
            right(g, mc, "Blick: " + p.getDirection().getName(), 30, 0xFFB8B8C8);
        }
    }

    public static class SpeedHud extends Module {
        SpeedHud() {
            super("Tempo-Anzeige", "Zeigt deine Geschwindigkeit in Bloecken pro Sekunde.", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p == null) return;
            double bps = new Vec3(p.getX() - p.xo, 0, p.getZ() - p.zo).length() * 20.0;
            right(g, mc, String.format("Tempo %.1f Bloecke/s", bps), 44, 0xFF66FFAA);
        }
    }

    public static class LightHud extends Module {
        LightHud() {
            super("Lichtlevel", "Zeigt das Lichtlevel an deiner Position (unter 8 koennen Monster spawnen).", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p == null || mc.level == null) return;
            int l = mc.level.getMaxLocalRawBrightness(p.blockPosition());
            right(g, mc, "Licht " + l + (l < 8 ? "  (Monster-Gefahr)" : ""), 56, l < 8 ? 0xFFFF6A6A : 0xFFFFE066);
        }
    }

    public static class FallHud extends Module {
        FallHud() {
            super("Fallhoehe", "Zeigt deine aktuelle Fallstrecke.", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p == null || p.fallDistance < 1.0F) return;
            right(g, mc, String.format("Fall %.1f Bloecke", p.fallDistance), 68, p.fallDistance > 3 ? 0xFFFF6A6A : 0xFFFFFFFF);
        }
    }

    public static class CpsHud extends Module {
        private final List<Long> clicks = new ArrayList<>();
        private boolean was;

        CpsHud() {
            super("CPS", "Zeigt deine Klicks pro Sekunde (linke Maustaste).", Category.HUD);
        }

        @Override
        public void onPreTick(Minecraft mc) {
            boolean d = mc.options.keyAttack.isDown();
            if (d && !was) clicks.add(System.currentTimeMillis());
            was = d;
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            long now = System.currentTimeMillis();
            clicks.removeIf(t -> now - t > 1000);
            right(g, mc, "CPS " + clicks.size(), 80, 0xFFFFFFFF);
        }
    }

    public static class HungerWarn extends Module {
        final Setting below = add(Setting.number("Warnung bei", 8, 1, 19, 1));

        HungerWarn() {
            super("HungerWarnung", "Warnt mitten im Bild, wenn dein Hunger niedrig ist.", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p == null || p.isCreative()) return;
            int f = p.getFoodData().getFoodLevel();
            if (f > below.value) return;
            String s = "Hunger niedrig: " + f + "/20";
            int W = mc.getWindow().getGuiScaledWidth(), H = mc.getWindow().getGuiScaledHeight();
            g.drawString(mc.font, s, (W - mc.font.width(s)) / 2, H / 2 + 40, 0xFFFF9A3C);
        }
    }

    public static class DurabilityWarn extends Module {
        final Setting percent = add(Setting.number("Warnung unter %", 10, 2, 50, 1));

        DurabilityWarn() {
            super("Haltbarkeits-Warnung", "Warnt, wenn Ruestung oder dein Item in der Hand fast kaputt ist.", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p == null) return;
            List<ItemStack> list = new ArrayList<>();
            list.add(p.getMainHandItem());
            for (int i = 36; i < 40; i++) list.add(p.getInventory().getItem(i));
            int W = mc.getWindow().getGuiScaledWidth(), H = mc.getWindow().getGuiScaledHeight();
            int y = H / 2 + 54;
            for (ItemStack st : list) {
                if (st.isEmpty() || !st.isDamageableItem() || st.getMaxDamage() <= 0) continue;
                double left = 100.0 * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage();
                if (left > percent.value) continue;
                String s = Combo.path(st) + " nur noch " + (int) left + "%";
                g.drawString(mc.font, s, (W - mc.font.width(s)) / 2, y, 0xFFFF6A6A);
                y += 10;
            }
        }
    }

    public static class Keystrokes extends Module {
        Keystrokes() {
            super("Tasten-Anzeige", "Zeigt unten links, welche Bewegungstasten du gerade drueckst (W A S D, Leertaste).", Category.HUD);
        }

        private static void key(GuiGraphics g, Minecraft mc, String s, int x, int y, int w, boolean on) {
            g.fill(x, y, x + w, y + 14, on ? 0xC0FFFFFF : 0x90000000);
            g.drawString(mc.font, s, x + (w - mc.font.width(s)) / 2, y + 3, on ? 0xFF000000 : 0xFFFFFFFF);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            int H = mc.getWindow().getGuiScaledHeight();
            int x = 8, y = H - 66;
            key(g, mc, "W", x + 17, y, 14, mc.options.keyUp.isDown());
            key(g, mc, "A", x, y + 16, 14, mc.options.keyLeft.isDown());
            key(g, mc, "S", x + 17, y + 16, 14, mc.options.keyDown.isDown());
            key(g, mc, "D", x + 34, y + 16, 14, mc.options.keyRight.isDown());
            key(g, mc, "SPACE", x, y + 32, 48, mc.options.keyJump.isDown());
        }
    }
}
