package de.hackmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Reach, Resistenz und Staerke. Diese drei aendern echte Werte (Reichweite, Effekte) und gehen deshalb nur
 * im Einzelspieler oder als LAN-Host mit erlaubten Cheats - ueber den eingebauten Server, wie /bot.
 */
public final class Extra5 {
    private Extra5() {}

    private static void run(MinecraftServer srv, String cmd) {
        var src = srv.createCommandSourceStack().withSuppressedOutput();
        srv.getCommands().performPrefixedCommand(src, cmd);
    }

    static String me(Minecraft mc) {
        return mc.getSingleplayerServer() != null ? mc.player.getUUID().toString() : "@s";
    }

    /** Einzelspieler/LAN-Host: ueber den eingebauten Server. Sonst: als normaler Chat-Befehl (braucht Op-Rechte auf dem Server). */
    static void exec(Minecraft mc, String cmd) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv != null) srv.execute(() -> run(srv, cmd));
        else if (mc.getConnection() != null) mc.getConnection().sendCommand(cmd);
    }

    static boolean online(Minecraft mc) {
        return mc.player != null && (mc.getSingleplayerServer() != null || mc.getConnection() != null);
    }

    private static void note(GuiGraphics g, Minecraft mc, String name, boolean remote) {
        if (!remote) return;
        String s = name + ": Server-Befehl - wirkt nur mit Op-Rechten";
        g.drawString(mc.font, s, 6, 28, 0xFFFFAA44);
    }

    // ------------------------------------------------------------------ Reach

    /** Setzt deine Interaktionsreichweite (Angriff und Bloecke) hoch. */
    public static class Reach extends Module {
        final Setting range = add(Setting.number("Reichweite", 6, 3, 64, 1));
        private boolean noServer;
        private int cool;

        Reach() {
            super("Reach", "Erhoeht deine Angriffs-/Interaktionsreichweite ueber ein Attribut (Einzelspieler / LAN-Host). Auf fremden Servern gilt deren Reichweite.", Category.KAMPF);
        }

        @Override
        protected void onDisable() {
            Minecraft mc = Minecraft.getInstance();
            if (!online(mc)) return;
            String id = me(mc);
            exec(mc, "attribute " + id + " minecraft:entity_interaction_range base set 3");
            exec(mc, "attribute " + id + " minecraft:block_interaction_range base set 4.5");
        }

        @Override
        public void onTick(Minecraft mc) {
            if (!online(mc)) return;
            noServer = mc.getSingleplayerServer() == null;
            if (cool-- > 0) return;
            double cur = mc.player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            if (Math.abs(cur - range.value) < 0.01) return;
            cool = noServer ? 200 : 0;
            String id = me(mc);
            double r = range.value;
            exec(mc, "attribute " + id + " minecraft:entity_interaction_range base set " + r);
            exec(mc, "attribute " + id + " minecraft:block_interaction_range base set " + Math.max(4.5, r));
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            note(g, mc, "Reach", noServer);
        }
    }

    // ------------------------------------------------------------------ Effekte

    private abstract static class EffectMod extends Module {
        final Setting level = add(Setting.number("Stufe", 5, 1, 255, 1));
        private final String effect;
        private int ticks;
        private boolean noServer;

        EffectMod(String name, String desc, String effect, Category c) {
            super(name, desc, c);
            this.effect = effect;
        }

        @Override
        protected void onEnable() {
            ticks = 0;
        }

        @Override
        protected void onDisable() {
            Minecraft mc = Minecraft.getInstance();
            if (!online(mc)) return;
            exec(mc, "effect clear " + me(mc) + " minecraft:" + effect);
        }

        @Override
        public void onTick(Minecraft mc) {
            if (!online(mc)) return;
            noServer = mc.getSingleplayerServer() == null;
            if (ticks-- > 0) return;
            ticks = noServer ? 600 : 60;
            int amp = (int) level.value - 1;
            exec(mc, "effect give " + me(mc) + " minecraft:" + effect + " infinite " + amp + " true");
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            note(g, mc, name, noServer);
        }
    }

    public static class Resistenz extends EffectMod {
        Resistenz() {
            super("Resistenz", "Dauerhafter Resistenz-Effekt (Stufe einstellbar, Stufe 5 = unverwundbar). Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "resistance", Category.SCHUTZ);
        }
    }

    public static class Staerke extends EffectMod {
        Staerke() {
            super("Staerke", "Dauerhafter Staerke-Effekt (Stufe einstellbar). Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "strength", Category.KAMPF);
        }
    }

    public static class Eff0 extends EffectMod {
        Eff0() {
            super("Tempo-Effekt", "Dauerhafter Speed-Effekt, Stufe bis 255. Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "speed", Category.BEWEGUNG);
        }
    }

    public static class Eff1 extends EffectMod {
        Eff1() {
            super("Eile", "Dauerhafter Eile-Effekt (schneller abbauen), Stufe bis 255. Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "haste", Category.WELT);
        }
    }

    public static class Eff2 extends EffectMod {
        Eff2() {
            super("Sprungkraft", "Dauerhafter Sprungkraft-Effekt, Stufe bis 255. Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "jump_boost", Category.BEWEGUNG);
        }
    }

    public static class Eff3 extends EffectMod {
        Eff3() {
            super("Regeneration", "Dauerhafte Regeneration, Stufe bis 255. Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "regeneration", Category.SCHUTZ);
        }
    }

    public static class Eff4 extends EffectMod {
        Eff4() {
            super("Absorption", "Dauerhafte Absorptions-Herzen, Stufe bis 255. Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "absorption", Category.SCHUTZ);
        }
    }

    public static class Eff5 extends EffectMod {
        Eff5() {
            super("Extra-Herzen", "Dauerhaft mehr maximale Herzen, Stufe bis 255. Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "health_boost", Category.SCHUTZ);
        }
    }

    public static class Eff6 extends EffectMod {
        Eff6() {
            super("Feuerresistenz", "Dauerhaft immun gegen Feuer und Lava. Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "fire_resistance", Category.SCHUTZ);
        }
    }

    public static class Eff7 extends EffectMod {
        Eff7() {
            super("Wasseratmung", "Dauerhaft unter Wasser atmen. Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "water_breathing", Category.SCHUTZ);
        }
    }

    public static class Eff8 extends EffectMod {
        Eff8() {
            super("Saettigung", "Dauerhaft satt - Hunger sinkt nicht. Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "saturation", Category.SCHUTZ);
        }
    }

    public static class Eff9 extends EffectMod {
        Eff9() {
            super("Nachtsicht", "Dauerhafte Nachtsicht. Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "night_vision", Category.SICHT);
        }
    }

    public static class Eff10 extends EffectMod {
        Eff10() {
            super("Langsamer Fall", "Dauerhaft kein Fallschaden, langsames Fallen. Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "slow_falling", Category.FLUG);
        }
    }

    public static class Eff11 extends EffectMod {
        Eff11() {
            super("Glueck", "Dauerhaftes Glueck, Stufe bis 255. Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "luck", Category.WELT);
        }
    }

    public static class Eff12 extends EffectMod {
        Eff12() {
            super("Unsichtbar", "Dauerhaft unsichtbar (Ruestung bleibt sichtbar). Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "invisibility", Category.SCHUTZ);
        }
    }

    public static class Eff13 extends EffectMod {
        Eff13() {
            super("Delfingnade", "Dauerhaft schnell schwimmen. Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "dolphins_grace", Category.BEWEGUNG);
        }
    }

    public static class Eff14 extends EffectMod {
        Eff14() {
            super("Heldendorf", "Dauerhaft Held des Dorfes (Handelsrabatt), Stufe bis 255. Einzelspieler / LAN-Host, sonst nur mit Op-Rechten.", "hero_of_the_village", Category.WELT);
        }
    }
}
