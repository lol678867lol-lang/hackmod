package de.hackmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Seed-X-Ray: liest die echte Welt (Seed) ueber den eingebauten Server aus. Nur Einzelspieler / LAN-Host. */
public final class Extra3 {
    private Extra3() {}

    public static class SeedXray extends Module {
        final Setting range = add(Setting.choice("Radius", 128, 64, 128, 256, 512, 1024));
        final Setting perTick = add(Setting.choice("Chunks pro Tick", 2, 1, 2, 4, 8));
        final Setting maxBoxes = add(Setting.choice("Max. Boxen", 300, 100, 300, 600, 1000));
        final Setting onlyRare = add(Setting.toggle("Nur seltene", false));

        private final Map<Long, List<int[]>> found = new ConcurrentHashMap<>();
        private final Set<Long> queued = ConcurrentHashMap.newKeySet();
        private List<int[]> offsets = new ArrayList<>();
        private int offsetsRc = -1;
        private volatile List<int[]> draw = new ArrayList<>();
        private int ticks;
        private boolean noServer;

        SeedXray() {
            super("Seed-XRay", "Zeigt Erze aus dem echten Seed deiner Welt (nur Einzelspieler / LAN-Host). Generiert dabei Chunks vor.", Category.SICHT);
        }

        @Override
        protected void onEnable() { reset(); }

        @Override
        protected void onDisable() { reset(); }

        private void reset() {
            found.clear();
            queued.clear();
            draw = new ArrayList<>();
            offsetsRc = -1;
            ticks = 0;
        }

        private static long key(int cx, int cz) {
            return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        }

        @Override
        public void onTick(Minecraft mc) {
            MinecraftServer srv = mc.getSingleplayerServer();
            noServer = srv == null;
            if (noServer || mc.player == null || mc.level == null) return;
            ServerLevel level = srv.getLevel(mc.level.dimension());
            if (level == null) return;
            ticks++;

            int rc = (int) Math.ceil(range.value / 16.0) + 1;
            if (rc != offsetsRc) {
                offsetsRc = rc;
                offsets = new ArrayList<>();
                for (int dx = -rc; dx <= rc; dx++) {
                    for (int dz = -rc; dz <= rc; dz++) {
                        if (dx * dx + dz * dz <= rc * rc) offsets.add(new int[]{dx, dz});
                    }
                }
                offsets.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
            }
            int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;

            // Naehere Chunks regelmaessig neu scannen (Spieler baut ab / Welt aendert sich)
            if (ticks % 100 == 0) {
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) found.remove(key(pcx + dx, pcz + dz));
            }

            if (ticks % 2 == 0 && queued.size() < 8) {
                final boolean rare = onlyRare.on;
                List<int[]> todo = new ArrayList<>();
                int want = (int) perTick.value;
                for (int[] o : offsets) {
                    int cx = pcx + o[0], cz = pcz + o[1];
                    long k = key(cx, cz);
                    if (found.containsKey(k) || queued.contains(k)) continue;
                    todo.add(new int[]{cx, cz});
                    queued.add(k);
                    if (todo.size() >= want) break;
                }
                if (!todo.isEmpty()) {
                    srv.execute(() -> {
                        for (int[] c : todo) {
                            long k = key(c[0], c[1]);
                            try {
                                LevelChunk ch = level.getChunk(c[0], c[1]);
                                found.put(k, ChunkScan.scan(ch, c[0], c[1], s -> Modules.OreEsp.colorOf(s, rare)));
                            } catch (Throwable t) {
                                found.put(k, new ArrayList<>());
                            } finally {
                                queued.remove(k);
                            }
                        }
                    });
                }
            }

            if (ticks % 20 == 0) {
                double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
                double r2 = range.value * range.value;
                List<int[]> all = new ArrayList<>();
                for (List<int[]> l : found.values()) {
                    for (int[] h : l) {
                        double dx = h[0] + 0.5 - px, dy = h[1] + 0.5 - py, dz = h[2] + 0.5 - pz;
                        if (dx * dx + dy * dy + dz * dz <= r2) all.add(h);
                    }
                }
                all.sort(Comparator.comparingDouble((int[] h) -> {
                    double dx = h[0] + 0.5 - px, dy = h[1] + 0.5 - py, dz = h[2] + 0.5 - pz;
                    return dx * dx + dy * dy + dz * dz;
                }));
                int max = (int) maxBoxes.value;
                draw = all.size() > max ? new ArrayList<>(all.subList(0, max)) : all;
            }
        }

        @Override
        public void onEsp(GuiGraphics g, Minecraft mc, Cam cam) {
            double[] r = new double[4];
            for (int[] h : draw) {
                AABB b = new AABB(h[0], h[1], h[2], h[0] + 1, h[1] + 1, h[2] + 1);
                if (cam.box(b, r)) Gfx.outline(g, (int) r[0], (int) r[1], (int) r[2], (int) r[3], h[3]);
            }
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            String t = noServer
                    ? "Seed-XRay: nur Einzelspieler / LAN-Host"
                    : "Seed-XRay: " + found.size() + " Chunks gescannt, " + draw.size() + " Erze";
            g.drawString(mc.font, t, 6, 40, noServer ? 0xFFFF6666 : 0xFF66FFAA, true);
        }
    }
}
