package de.hackmod;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Durchsucht alle geladenen Chunks (nicht nur einen Wuerfel um dich) nach bestimmten Bloecken.
 * Chunks werden nach und nach gescannt (wenige pro Tick) und zwischengespeichert, deshalb sind
 * sehr grosse Radien moeglich. Mehr als die geladenen Chunks (Sichtweite des Servers) gibt es
 * auf dem Client aber nicht - dahinter kennt dein Spiel die Bloecke einfach nicht.
 */
final class ChunkScan {
    interface Matcher {
        /** Farbe (ARGB) des Blocks oder 0, wenn er nicht gesucht wird. */
        int color(BlockState s);
    }

    private static final class Entry {
        final int cx, cz;
        final long tick;
        final List<int[]> hits;

        Entry(int cx, int cz, long tick, List<int[]> hits) {
            this.cx = cx;
            this.cz = cz;
            this.tick = tick;
            this.hits = hits;
        }
    }

    private final Map<Long, Entry> cache = new HashMap<>();
    private List<int[]> offsets = new ArrayList<>();
    private int offsetsRc = -1;
    private List<int[]> draw = new ArrayList<>();
    private long now;

    void clear() {
        cache.clear();
        draw = new ArrayList<>();
        now = 0;
        offsetsRc = -1;
    }

    /** Treffer der letzten Aktualisierung, nah -> fern sortiert. */
    List<int[]> hits() {
        return draw;
    }

    private void buildOffsets(int rc) {
        offsets = new ArrayList<>();
        for (int dx = -rc; dx <= rc; dx++) {
            for (int dz = -rc; dz <= rc; dz++) {
                if (dx * dx + dz * dz <= rc * rc) offsets.add(new int[]{dx, dz});
            }
        }
        offsets.sort(Comparator.comparingInt((int[] o) -> o[0] * o[0] + o[1] * o[1]));
        offsetsRc = rc;
    }

    void tick(Minecraft mc, int radius, int perTick, int maxDraw, Matcher m) {
        if (mc.player == null || mc.level == null) return;
        now++;
        int rd = mc.options.renderDistance().get();
        int rc = Math.max(1, Math.min((radius >> 4) + 1, rd + 2));
        if (rc != offsetsRc) buildOffsets(rc);
        int pcx = Mth.floor(mc.player.getX()) >> 4;
        int pcz = Mth.floor(mc.player.getZ()) >> 4;

        if (now % 2 == 0) {
            int scanned = 0;
            for (int[] o : offsets) {
                int cx = pcx + o[0], cz = pcz + o[1];
                long key = ChunkPos.asLong(cx, cz);
                Entry e = cache.get(key);
                int dist2 = o[0] * o[0] + o[1] * o[1];
                boolean need = e == null
                        || (dist2 <= 9 && now - e.tick > 80)
                        || now - e.tick > 1200;
                if (!need) continue;
                LevelChunk ch = mc.level.getChunk(cx, cz);
                if (ch instanceof EmptyLevelChunk) continue;
                cache.put(key, new Entry(cx, cz, now, scan(ch, cx, cz, m)));
                if (++scanned >= perTick) break;
            }
        }

        if (now % 20 == 1) {
            double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
            double r2 = (double) radius * radius;
            List<double[]> tmp = new ArrayList<>();
            List<int[]> all = new ArrayList<>();
            Iterator<Entry> it = cache.values().iterator();
            while (it.hasNext()) {
                Entry e = it.next();
                if (Math.abs(e.cx - pcx) > rc + 3 || Math.abs(e.cz - pcz) > rc + 3) {
                    it.remove();
                    continue;
                }
                for (int[] h : e.hits) {
                    double dx = h[0] + 0.5 - px, dy = h[1] + 0.5 - py, dz = h[2] + 0.5 - pz;
                    if (dx * dx + dy * dy + dz * dz <= r2) all.add(h);
                }
            }
            all.sort(Comparator.comparingDouble((int[] h) -> {
                double dx = h[0] + 0.5 - px, dy = h[1] + 0.5 - py, dz = h[2] + 0.5 - pz;
                return dx * dx + dy * dy + dz * dz;
            }));
            draw = all.size() > maxDraw ? new ArrayList<>(all.subList(0, maxDraw)) : all;
        }
    }

    private static List<int[]> scan(LevelChunk ch, int cx, int cz, Matcher m) {
        List<int[]> out = new ArrayList<>();
        LevelChunkSection[] secs = ch.getSections();
        // Overworld: 24 Sektionen ab Y=-64 (Sektion -4). Nether/Ende: 16 Sektionen ab Y=0.
        int minSec = secs.length == 24 ? -4 : 0;
        for (int i = 0; i < secs.length; i++) {
            LevelChunkSection s = secs[i];
            if (s == null || s.hasOnlyAir()) continue;
            if (!s.maybeHas(st -> m.color(st) != 0)) continue;
            int by = (minSec + i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int c = m.color(s.getBlockState(x, y, z));
                        if (c != 0) out.add(new int[]{(cx << 4) + x, by + y, (cz << 4) + z, c});
                    }
                }
            }
            if (out.size() > 6000) break;
        }
        return out;
    }
}
