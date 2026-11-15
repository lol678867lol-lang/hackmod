package de.hackmod;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Nuetzliche Alltags-Hacks: Hotbar auffuellen, Muell droppen, Wegpunkte, Todespunkt, Alarme und mehr. */
public final class Extra6 {
    private Extra6() {}

    // ------------------------------------------------------------------ Wegpunkte (Befehl /wp)

    private record Wp(String name, double x, double y, double z, String dim) {}

    private static final List<Wp> WPS = new ArrayList<>();

    private static String dimOf(Minecraft mc) {
        return String.valueOf(mc.level.dimension());
    }

    static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("wp")
                .then(ClientCommandManager.literal("add")
                    .then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player == null || mc.level == null) return 0;
                        String n = StringArgumentType.getString(ctx, "name");
                        WPS.removeIf(w -> w.name().equalsIgnoreCase(n) && w.dim().equals(dimOf(mc)));
                        WPS.add(new Wp(n, mc.player.getX(), mc.player.getY(), mc.player.getZ(), dimOf(mc)));
                        ctx.getSource().sendFeedback(Component.literal("Wegpunkt '" + n + "' gesetzt (Modul 'Wegpunkte' einschalten)."));
                        return 1;
                    })))
                .then(ClientCommandManager.literal("del")
                    .then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
                        String n = StringArgumentType.getString(ctx, "name");
                        boolean ok = WPS.removeIf(w -> w.name().equalsIgnoreCase(n));
                        ctx.getSource().sendFeedback(Component.literal(ok ? "Wegpunkt geloescht." : "Gibt es nicht."));
                        return 1;
                    })))
                .then(ClientCommandManager.literal("list").executes(ctx -> {
                    for (Wp w : WPS) {
                        ctx.getSource().sendFeedback(Component.literal(w.name() + "  " + (int) w.x() + " " + (int) w.y() + " " + (int) w.z() + "  (" + w.dim() + ")"));
                    }
                    if (WPS.isEmpty()) ctx.getSource().sendFeedback(Component.literal("Keine Wegpunkte. Setzen: /wp add <name>"));
                    return 1;
                }))));
    }

    public static class Wegpunkte extends Module {
        final Setting tracers = add(Setting.toggle("Linien", true));

        Wegpunkte() {
            super("Wegpunkte", "Befehl /wp add <name>, /wp del <name>, /wp list. Zeigt Wegpunkte mit Entfernung und Linie (nur in der Dimension, in der du sie gesetzt hast; gehen beim Neustart verloren).", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p == null || mc.level == null) return;
            String dim = dimOf(mc);
            int y = 110;
            for (Wp w : WPS) {
                if (!w.dim().equals(dim)) continue;
                int d = (int) Math.sqrt(Math.pow(w.x() - p.getX(), 2) + Math.pow(w.y() - p.getY(), 2) + Math.pow(w.z() - p.getZ(), 2));
                g.drawString(mc.font, w.name() + "  " + d + "m", 6, y, 0xFF66E0FF);
                y += 10;
            }
        }

        @Override
        public void onEsp(GuiGraphics g, Minecraft mc, Cam cam) {
            if (!tracers.on || mc.level == null) return;
            String dim = dimOf(mc);
            for (Wp w : WPS) if (w.dim().equals(dim)) cam.tracer(g, w.x(), w.y(), w.z(), 0xFF66E0FF);
        }
    }

    // ------------------------------------------------------------------ Todespunkt

    public static class Todespunkt extends Module {
        private double lx, ly, lz;
        private boolean have, recorded, dead;
        private double dx, dy, dz;

        Todespunkt() {
            super("Todespunkt", "Merkt sich, wo du gestorben bist: Koordinaten, Entfernung und Linie dorthin - bis du wieder da bist.", Category.HUD);
        }

        @Override
        protected void onDisable() {
            dead = false;
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.screen instanceof DeathScreen) {
                if (!recorded && have) {
                    dx = lx;
                    dy = ly;
                    dz = lz;
                    dead = true;
                    recorded = true;
                }
                return;
            }
            recorded = false;
            lx = p.getX();
            ly = p.getY();
            lz = p.getZ();
            have = true;
            if (dead && Math.sqrt(Math.pow(dx - lx, 2) + Math.pow(dy - ly, 2) + Math.pow(dz - lz, 2)) < 4) dead = false;
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            if (!dead || mc.player == null) return;
            int d = (int) Math.sqrt(Math.pow(dx - mc.player.getX(), 2) + Math.pow(dy - mc.player.getY(), 2) + Math.pow(dz - mc.player.getZ(), 2));
            g.drawString(mc.font, "Todespunkt: " + (int) dx + " " + (int) dy + " " + (int) dz + "  (" + d + "m)", 6, 96, 0xFFFF6A6A);
        }

        @Override
        public void onEsp(GuiGraphics g, Minecraft mc, Cam cam) {
            if (dead) cam.tracer(g, dx, dy, dz, 0xFFFF6A6A);
        }
    }

    // ------------------------------------------------------------------ Inventar

    /** Leere Hotbar-Slots automatisch mit dem gleichen Item aus dem Inventar auffuellen. */
    public static class HotbarRefill extends Module {
        private final String[] last = new String[9];
        private int cd;

        HotbarRefill() {
            super("HotbarRefill", "Ist ein Hotbar-Slot leer geworden (z.B. Bloecke aufgebraucht), wird er automatisch mit dem gleichen Item aus dem Inventar aufgefuellt.", Category.SCHUTZ);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null) return;
            if (cd > 0) cd--;
            for (int i = 0; i < 9; i++) {
                ItemStack st = p.getInventory().getItem(i);
                if (!st.isEmpty()) {
                    last[i] = Combo.path(st);
                    continue;
                }
                if (last[i] == null || cd > 0 || mc.screen != null) continue;
                for (int s = 9; s < 36; s++) {
                    ItemStack c = p.getInventory().getItem(s);
                    if (!c.isEmpty() && Combo.path(c).equals(last[i])) {
                        mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, s, i, ClickType.SWAP, p);
                        cd = 4;
                        break;
                    }
                }
                last[i] = null;
            }
        }
    }

    /** Wirft unnuetze Items (Stein, Erde, Faulfleisch ...) aus dem Inventar. */
    public static class AutoMuell extends Module {
        private static final Set<String> JUNK = new HashSet<>(List.of(
                "cobblestone", "dirt", "gravel", "andesite", "diorite", "granite", "tuff", "cobbled_deepslate",
                "netherrack", "rotten_flesh", "spider_eye", "poisonous_potato", "pufferfish", "bone", "string"));
        final Setting hotbar = add(Setting.toggle("Auch Hotbar", false));
        private int cd;

        AutoMuell() {
            super("AutoMuell", "Wirft Stein, Erde, Kies, Granit, Faulfleisch, Knochen usw. automatisch weg (Liste fest eingebaut).", Category.WELT);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || mc.screen != null || cd-- > 0) return;
            for (int s = hotbar.on ? 0 : 9; s < 36; s++) {
                ItemStack st = p.getInventory().getItem(s);
                if (st.isEmpty() || !JUNK.contains(Combo.path(st))) continue;
                int menuSlot = s < 9 ? 36 + s : s;
                mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, menuSlot, 1, ClickType.THROW, p);
                cd = 4;
                return;
            }
        }
    }

    public static class InvFullWarn extends Module {
        final Setting freeSlots = add(Setting.number("Warnung ab freien Slots", 3, 0, 10, 1));

        InvFullWarn() {
            super("Inventar-Warnung", "Warnt, wenn dein Inventar fast voll ist.", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p == null) return;
            int free = 0;
            for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).isEmpty()) free++;
            if (free > freeSlots.value) return;
            String s = free == 0 ? "Inventar VOLL" : "Inventar fast voll: " + free + " frei";
            int W = mc.getWindow().getGuiScaledWidth(), H = mc.getWindow().getGuiScaledHeight();
            g.drawString(mc.font, s, (W - mc.font.width(s)) / 2, H / 2 + 74, free == 0 ? 0xFFFF4D4D : 0xFFFFD166);
        }
    }

    // ------------------------------------------------------------------ Werkzeug / Klicks

    /** Stoppt Abbauen und Benutzen, bevor dein Werkzeug kaputtgeht. */
    public static class ToolSaver extends Module {
        final Setting left = add(Setting.number("Stopp bei Rest-Haltbarkeit", 5, 1, 50, 1));
        private boolean warn;

        ToolSaver() {
            super("ToolSaver", "Ist die Haltbarkeit deines Werkzeugs in der Hand fast aufgebraucht, werden Abbauen und Schlagen gesperrt - dein Item geht nicht kaputt.", Category.SCHUTZ);
        }

        @Override
        public void onPreTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            ItemStack st = p == null ? ItemStack.EMPTY : p.getMainHandItem();
            warn = !st.isEmpty() && st.isDamageableItem() && st.getMaxDamage() - st.getDamageValue() <= left.value;
            if (!warn) return;
            mc.options.keyAttack.setDown(false);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            if (!warn) return;
            String s = "ToolSaver: Item fast kaputt - gesperrt";
            int W = mc.getWindow().getGuiScaledWidth(), H = mc.getWindow().getGuiScaledHeight();
            g.drawString(mc.font, s, (W - mc.font.width(s)) / 2, H / 2 + 88, 0xFFFF6A6A);
        }
    }

    /** Drueckt automatisch in festem Takt Rechtsklick (z.B. fuer Farmen, Fuettern, Angeln). */
    public static class AutoRechtsklick extends Module {
        final Setting every = add(Setting.number("Alle ... Ticks", 10, 2, 100, 1));
        private int t;
        private boolean mine;

        AutoRechtsklick() {
            super("AutoRechtsklick", "Rechtsklick wird automatisch im Takt ausgeloest (Farmen, Tiere fuettern, Knoepfe).", Category.WELT);
        }

        @Override
        protected void onDisable() {
            if (mine) Minecraft.getInstance().options.keyUse.setDown(false);
            mine = false;
        }

        @Override
        public void onPreTick(Minecraft mc) {
            if (mc.player == null || mc.screen != null) return;
            if (++t >= every.value) {
                t = 0;
                mc.options.keyUse.setDown(true);
                mine = true;
            } else if (mine) {
                mc.options.keyUse.setDown(false);
                mine = false;
            }
        }
    }

    // ------------------------------------------------------------------ Alarm und Sicht

    public static class SpielerAlarm extends Module {
        final Setting range = add(Setting.number("Reichweite", 64, 16, 256, 16));
        private final Map<UUID, Long> seen = new HashMap<>();
        private final List<String> msgs = new ArrayList<>();
        private final List<Long> times = new ArrayList<>();

        SpielerAlarm() {
            super("Spieler-Alarm", "Meldet im Bild, sobald ein anderer Spieler in deine Naehe kommt.", Category.HUD);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            long now = System.currentTimeMillis();
            Set<UUID> inRange = new HashSet<>();
            for (Player e : mc.level.players()) {
                if (e == p || p.distanceTo(e) > range.value) continue;
                inRange.add(e.getUUID());
                if (!seen.containsKey(e.getUUID())) {
                    msgs.add(e.getName().getString() + " in Reichweite (" + (int) p.distanceTo(e) + "m)");
                    times.add(now);
                }
                seen.put(e.getUUID(), now);
            }
            seen.keySet().retainAll(inRange);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            long now = System.currentTimeMillis();
            int W = mc.getWindow().getGuiScaledWidth();
            int y = 140;
            for (int i = msgs.size() - 1; i >= 0; i--) {
                if (now - times.get(i) > 6000) {
                    msgs.remove(i);
                    times.remove(i);
                    continue;
                }
                String s = "! " + msgs.get(i);
                g.drawString(mc.font, s, (W - mc.font.width(s)) / 2, y, 0xFFFF4D4D);
                y += 10;
            }
        }
    }

    public static class SpielerLinien extends Module {
        final Setting range = add(Setting.choice("Reichweite", 128, 32, 64, 128, 256, 512, 1000));

        SpielerLinien() {
            super("Spieler-Linien", "Zieht Linien vom Fadenkreuz zu allen Spielern in Reichweite (rot = nah, gruen = fern).", Category.SICHT);
        }

        @Override
        public void onEsp(GuiGraphics g, Minecraft mc, Cam cam) {
            LocalPlayer p = mc.player;
            for (Player e : mc.level.players()) {
                if (e == p) continue;
                double d = p.distanceTo(e);
                if (d > range.value) continue;
                cam.tracer(g, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), d < 20 ? 0xFFFF4D4D : (d < 60 ? 0xFFFFD166 : 0xFF66FF99));
            }
        }
    }

    public static class UmgebungsZaehler extends Module {
        final Setting range = add(Setting.number("Radius", 32, 8, 128, 8));

        UmgebungsZaehler() {
            super("Umgebung-Zaehler", "Zaehlt Spieler, Monster, Tiere und Items im Radius um dich.", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p == null || mc.level == null) return;
            int pl = 0, mo = 0, an = 0, it = 0;
            for (Entity e : mc.level.getEntities((Entity) null, p.getBoundingBox().inflate(range.value), x -> true)) {
                if (e == p) continue;
                if (e instanceof Player) pl++;
                else if (e instanceof Monster) mo++;
                else if (e instanceof Animal) an++;
                else if (e instanceof ItemEntity) it++;
            }
            int W = mc.getWindow().getGuiScaledWidth();
            String s = "Spieler " + pl + "  Monster " + mo + "  Tiere " + an + "  Items " + it;
            g.drawString(mc.font, s, W - mc.font.width(s) - 6, 92, 0xFFB8B8C8);
        }
    }
}
