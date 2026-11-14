package de.hackmod;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Struktur-Finder: berechnet aus dem Seed, wo Strukturen liegen koennten (gleiche Regeln wie das Spiel,
 * ohne Biom-Pruefung - Treffer sind Kandidaten). Seed: Einzelspieler automatisch, sonst /structseed <seed>.
 */
public final class Structs {
    private Structs() {}

    /** name, spacing, separation, salt, triangular, dimension (0 Overworld, 1 Nether, 2 Ende), color */
    private static final Object[][] TYPES = {
        {"Dorf", 34, 8, 10387312, false, 0, 0xFF66FF66},
        {"Pluenderer-Aussenposten", 32, 8, 165745296, false, 0, 0xFFFF6666},
        {"Wuestentempel", 32, 8, 14357617, false, 0, 0xFFFFE066},
        {"Dschungeltempel", 32, 8, 14357619, false, 0, 0xFF55DD55},
        {"Iglu", 32, 8, 14357618, false, 0, 0xFFE8F8FF},
        {"Hexenhuette", 32, 8, 14357620, false, 0, 0xFFAA66FF},
        {"Ozeanmonument", 32, 5, 10387313, true, 0, 0xFF4DEEEA},
        {"Waldanwesen", 80, 20, 10387319, true, 0, 0xFFB5533C},
        {"Schiffswrack", 24, 4, 165745295, false, 0, 0xFF8899FF},
        {"Ruine", 20, 8, 14357621, false, 0, 0xFF88BBFF},
        {"Portalruine", 40, 15, 34222645, false, 0, 0xFFFF55FF},
        {"Ancient City", 24, 8, 20083232, false, 0, 0xFF2E5BFF},
        {"Trial Chamber", 32, 12, 94251327, false, 0, 0xFFFFAA33},
        {"Trail Ruins", 34, 8, 83469867, false, 0, 0xFFD8AF93},
        {"Nether-Festung/Bastion", 27, 4, 30084232, false, 1, 0xFFFF5522},
        {"End City", 20, 11, 10387313, true, 2, 0xFFE0C0FF},
    };

    static volatile Long cmdSeed = null;

    static final class Hit {
        final String name; final int x, z, color;
        Hit(String n, int x, int z, int c) { name = n; this.x = x; this.z = z; color = c; }
    }

    static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("structseed")
                .then(ClientCommandManager.argument("seed", StringArgumentType.greedyString()).executes(ctx -> {
                    String s = StringArgumentType.getString(ctx, "seed").trim();
                    try {
                        cmdSeed = Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        cmdSeed = (long) s.hashCode();
                    }
                    ctx.getSource().sendFeedback(Component.literal("Struktur-Seed gesetzt: " + cmdSeed));
                    return 1;
                }))));
    }

    static Long seed(Minecraft mc) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv != null) return srv.overworld().getSeed();
        return cmdSeed;
    }

    private static int[] potential(long seed, int rx, int rz, int spacing, int sep, int salt, boolean tri) {
        long s = (long) rx * 341873128712L + (long) rz * 132897987541L + seed + salt;
        Random r = new Random(s);
        int n = spacing - sep;
        int ox, oz;
        if (tri) {
            ox = (r.nextInt(n) + r.nextInt(n)) / 2;
            oz = (r.nextInt(n) + r.nextInt(n)) / 2;
        } else {
            ox = r.nextInt(n);
            oz = r.nextInt(n);
        }
        return new int[]{rx * spacing + ox, rz * spacing + oz};
    }

    static List<Hit> find(long seed, int dim, int px, int pz, int radius) {
        List<Hit> out = new ArrayList<>();
        for (Object[] t : TYPES) {
            if ((int) t[5] != dim) continue;
            int spacing = (int) t[1], sep = (int) t[2], salt = (int) t[3];
            boolean tri = (boolean) t[4];
            int regionBlocks = spacing * 16;
            int r0x = Math.floorDiv(px - radius, regionBlocks), r1x = Math.floorDiv(px + radius, regionBlocks);
            int r0z = Math.floorDiv(pz - radius, regionBlocks), r1z = Math.floorDiv(pz + radius, regionBlocks);
            for (int rx = r0x; rx <= r1x; rx++) {
                for (int rz = r0z; rz <= r1z; rz++) {
                    int[] c = potential(seed, rx, rz, spacing, sep, salt, tri);
                    int x = c[0] * 16 + 8, z = c[1] * 16 + 8;
                    long dx = x - px, dz = z - pz;
                    if (dx * dx + dz * dz <= (long) radius * radius) out.add(new Hit((String) t[0], x, z, (int) t[6]));
                }
            }
        }
        out.sort(Comparator.comparingLong((Hit h) -> (long) (h.x - px) * (h.x - px) + (long) (h.z - pz) * (h.z - pz)));
        return out;
    }

    public static class Finder extends Module {
        final Setting range = add(Setting.choice("Radius", 2000, 500, 1000, 2000, 5000, 10000));
        final Setting count = add(Setting.choice("Anzahl", 10, 5, 10, 15, 20));
        final Setting lines = add(Setting.toggle("Linien (Tracer)", true));
        private List<Hit> hits = new ArrayList<>();
        private int ticks;
        private String info = "";

        Finder() {
            super("Struktur-Finder", "Berechnet Strukturen aus dem Seed (Einzelspieler automatisch, sonst /structseed <seed>). Kandidaten ohne Biom-Pruefung.", Category.SICHT);
        }

        @Override
        public void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null) return;
            if (ticks++ % 40 != 0) return;
            Long seed = seed(mc);
            if (seed == null) {
                info = "Struktur-Finder: Seed fehlt - /structseed <seed>";
                hits = new ArrayList<>();
                return;
            }
            String d = String.valueOf(mc.level.dimension());
            int dim = d.contains("the_nether") ? 1 : d.contains("the_end") ? 2 : 0;
            info = "Strukturen (Seed " + seed + ")";
            hits = find(seed, dim, mc.player.getBlockX(), mc.player.getBlockZ(), (int) range.value);
        }

        @Override
        public void onEsp(GuiGraphics g, Minecraft mc, Cam cam) {
            if (!lines.on) return;
            int n = 0;
            for (Hit h : hits) {
                if (n++ >= 5) break;
                cam.tracer(g, h.x + 0.5, mc.player.getY(), h.z + 0.5, h.color);
            }
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            if (mc.player == null) return;
            g.drawString(mc.font, info, 6, 54, 0xFFFFFFFF, true);
            int y = 66, n = 0;
            for (Hit h : hits) {
                if (n++ >= (int) count.value) break;
                int dist = (int) Math.sqrt(Math.pow(h.x - mc.player.getX(), 2) + Math.pow(h.z - mc.player.getZ(), 2));
                g.drawString(mc.font, h.name + "  X " + h.x + "  Z " + h.z + "  (" + dist + "m)", 6, y, h.color, true);
                y += 10;
            }
        }
    }
}
