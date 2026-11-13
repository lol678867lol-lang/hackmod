package de.hackmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** CrystalAura (Obsidian + Enderkristalle) und AnchorAura (Respawn-Anker + Glowstone mit Schild). */
public final class Aura {
    private Aura() {}

    // ------------------------------------------------------------------ Helfer

    private static String path(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    /** Hotbar-Slot mit genau diesem Gegenstand, sonst -1. */
    private static int exact(LocalPlayer p, String name) {
        for (int i = 0; i < 9; i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (!st.isEmpty() && path(st).equals(name)) return i;
        }
        return -1;
    }

    private static String blockName(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
    }

    private static boolean isCrystal(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("end_crystal");
    }

    /** Geschaetzter Explosionsschaden ohne Ruestung (Vanilla-Formel, ohne Deckung). power: Kristall 6, Anker 5. */
    static double estimate(Vec3 center, Vec3 pos, double power) {
        double radius = power * 2.0;
        double d = center.distanceTo(pos);
        if (d >= radius) return 0.0;
        double impact = 1.0 - d / radius;
        return (impact * impact + impact) / 2.0 * 7.0 * radius + 1.0;
    }

    private static void withSlot(Minecraft mc, int slot, Runnable r) {
        LocalPlayer p = mc.player;
        int prev = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        r.run();
        p.getInventory().setSelectedSlot(prev);
    }

    /** Rechtsklick auf eine Blockseite (mit dem, was in der angegebenen Hand liegt). */
    private static void useOn(Minecraft mc, InteractionHand hand, BlockPos pos, Direction face) {
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        mc.gameMode.useItemOn(mc.player, hand, new BlockHitResult(hit, face, pos, false));
        mc.player.swing(hand);
    }

    private static boolean freeSpace(Minecraft mc, LocalPlayer p, LivingEntity t, BlockPos cell) {
        AABB box = new AABB(cell);
        return !p.getBoundingBox().intersects(box) && !t.getBoundingBox().intersects(box);
    }

    // ------------------------------------------------------------------ CrystalAura

    public static class CrystalAura extends Module {
        final Setting targetRange = add(Setting.number("Zielradius", 10, 4, 20, 1));
        final Setting reach = add(Setting.number("Reichweite", 4.5, 3.0, 5.5, 0.5));
        final Setting delay = add(Setting.number("Pause (Ticks)", 2, 0, 10, 1));
        final Setting minDmg = add(Setting.number("Min. Schaden Gegner", 6, 1, 30, 1));
        final Setting maxSelf = add(Setting.number("Max. Schaden dir", 8, 1, 30, 1));
        final Setting safeHp = add(Setting.number("Stopp unter HP", 6, 0, 19, 1));
        final Setting placeObi = add(Setting.toggle("Obsidian setzen", true));
        final Setting breakOn = add(Setting.toggle("Kristalle zerschlagen", true));
        private int cd;

        CrystalAura() {
            super("CrystalAura", "Setzt Obsidian und Enderkristalle neben Gegner und sprengt sie - mit Schadensrechnung, damit du selbst wenig abbekommst. Kristalle + Obsidian in die Hotbar.", Category.CRYSTAL);
        }

        private boolean crystalAt(Minecraft mc, BlockPos pos) {
            for (Entity e : mc.level.getEntities((Entity) null, new AABB(pos), x -> true)) {
                if (isCrystal(e)) return true;
            }
            return false;
        }

        /** Beste Obsidian-/Bedrock-Basis fuer einen Kristall (oder null). */
        private BlockPos bestBase(Minecraft mc, LocalPlayer p, LivingEntity t) {
            BlockPos tb = t.blockPosition();
            Vec3 tp = t.position(), me = p.position(), eye = p.getEyePosition();
            BlockPos best = null;
            double bestScore = -1e9;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -2; dy <= 1; dy++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        m.set(tb.getX() + dx, tb.getY() + dy, tb.getZ() + dz);
                        String bn = blockName(mc.level.getBlockState(m));
                        if (!bn.equals("obsidian") && !bn.equals("bedrock")) continue;
                        BlockPos base = m.immutable();
                        if (!mc.level.getBlockState(base.above()).isAir() || !mc.level.getBlockState(base.above(2)).isAir()) continue;
                        if (eye.distanceTo(Vec3.atCenterOf(base)) > reach.value) continue;
                        Vec3 c = new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
                        double dt = estimate(c, tp, 6.0), ds = estimate(c, me, 6.0);
                        if (dt < minDmg.value || ds > maxSelf.value) continue;
                        if (crystalAt(mc, base.above())) continue;
                        double score = dt - ds;
                        if (score > bestScore) {
                            bestScore = score;
                            best = base;
                        }
                    }
                }
            }
            return best;
        }

        /** Beste freie Stelle (Fusshoehe neben dem Gegner), auf die Obsidian gesetzt werden kann. */
        private BlockPos bestObiSpot(Minecraft mc, LocalPlayer p, LivingEntity t) {
            BlockPos tb = t.blockPosition();
            Vec3 tp = t.position(), me = p.position(), eye = p.getEyePosition();
            BlockPos best = null;
            double bestScore = -1e9;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos cell = new BlockPos(tb.getX() + dx, tb.getY(), tb.getZ() + dz);
                    if (!mc.level.getBlockState(cell).canBeReplaced()) continue;
                    if (mc.level.getBlockState(cell.below()).getCollisionShape(mc.level, cell.below()).isEmpty()) continue;
                    if (!mc.level.getBlockState(cell.above()).isAir() || !mc.level.getBlockState(cell.above(2)).isAir()) continue;
                    if (!freeSpace(mc, p, t, cell)) continue;
                    if (eye.distanceTo(Vec3.atCenterOf(cell)) > reach.value) continue;
                    Vec3 c = new Vec3(cell.getX() + 0.5, cell.getY() + 1.0, cell.getZ() + 0.5);
                    double dt = estimate(c, tp, 6.0), ds = estimate(c, me, 6.0);
                    if (dt < minDmg.value || ds > maxSelf.value) continue;
                    double score = dt - ds;
                    if (score > bestScore) {
                        bestScore = score;
                        best = cell;
                    }
                }
            }
            return best;
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || mc.screen != null) return;
            if (cd > 0) {
                cd--;
                return;
            }
            if (p.getHealth() + p.getAbsorptionAmount() <= safeHp.value) return;
            LivingEntity t = Combo.nearest(mc, targetRange.value, true, false);
            if (t == null) return;
            Vec3 tp = t.position(), me = p.position();

            // 1) Kristalle zerschlagen
            if (breakOn.on) {
                Entity best = null;
                double bestScore = -1e9;
                AABB area = p.getBoundingBox().inflate(reach.value + 1.0);
                for (Entity e : mc.level.getEntities((Entity) null, area, x -> true)) {
                    if (!isCrystal(e) || !e.isAlive() || p.distanceTo(e) > reach.value) continue;
                    Vec3 c = e.position();
                    double dt = estimate(c, tp, 6.0), ds = estimate(c, me, 6.0);
                    if (dt < minDmg.value || ds > maxSelf.value) continue;
                    if (dt - ds > bestScore) {
                        bestScore = dt - ds;
                        best = e;
                    }
                }
                if (best != null) {
                    mc.gameMode.attack(p, best);
                    p.swing(InteractionHand.MAIN_HAND);
                    cd = (int) delay.value;
                    return;
                }
            }

            // 2) Kristall auf vorhandene Obsidian-Basis setzen
            boolean off = path(p.getOffhandItem()).equals("end_crystal");
            int crystalSlot = off ? -1 : exact(p, "end_crystal");
            if (!off && crystalSlot < 0) return;
            BlockPos base = bestBase(mc, p, t);
            if (base != null) {
                final BlockPos b = base;
                if (off) {
                    useOn(mc, InteractionHand.OFF_HAND, b, Direction.UP);
                } else {
                    withSlot(mc, crystalSlot, () -> useOn(mc, InteractionHand.MAIN_HAND, b, Direction.UP));
                }
                cd = (int) delay.value;
                return;
            }

            // 3) Obsidian setzen
            if (placeObi.on) {
                int obi = exact(p, "obsidian");
                if (obi < 0) return;
                BlockPos spot = bestObiSpot(mc, p, t);
                if (spot == null) return;
                final BlockPos s = spot;
                withSlot(mc, obi, () -> Modules.placeBlockAt(mc, s));
                cd = (int) delay.value;
            }
        }
    }

    // ------------------------------------------------------------------ AnchorAura

    public static class AnchorAura extends Module {
        final Setting targetRange = add(Setting.number("Zielradius", 8, 4, 16, 1));
        final Setting reach = add(Setting.number("Reichweite", 4.5, 3.0, 5.5, 0.5));
        final Setting delay = add(Setting.number("Pause (Ticks)", 2, 0, 10, 1));
        final Setting minDist = add(Setting.number("Min. Abstand", 3, 2, 6, 1));
        final Setting maxSelf = add(Setting.number("Max. Schaden dir", 12, 1, 30, 1));
        final Setting safeHp = add(Setting.number("Stopp unter HP", 6, 0, 19, 1));
        final Setting shield = add(Setting.toggle("Glowstone-Schild", true));
        private int cd;

        AnchorAura() {
            super("AnchorAura", "Respawn-Anker neben den Gegner, mit Glowstone laden und sprengen. Setzt vorher Glowstone als Schild vor dich. Anker + Glowstone in die Hotbar. Geht NICHT im Nether.", Category.CRYSTAL);
        }

        private BlockPos findAnchor(Minecraft mc, LocalPlayer p, BlockPos around) {
            Vec3 eye = p.getEyePosition();
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 3; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        m.set(around.getX() + dx, around.getY() + dy, around.getZ() + dz);
                        if (!(mc.level.getBlockState(m).getBlock() instanceof RespawnAnchorBlock)) continue;
                        double d = eye.distanceTo(Vec3.atCenterOf(m));
                        if (d <= reach.value && d < bestD) {
                            bestD = d;
                            best = m.immutable();
                        }
                    }
                }
            }
            return best;
        }

        private BlockPos bestAnchorSpot(Minecraft mc, LocalPlayer p, LivingEntity t, boolean shielded) {
            BlockPos tb = t.blockPosition();
            Vec3 tp = t.position(), me = p.position(), eye = p.getEyePosition();
            BlockPos best = null;
            double bestScore = -1e9;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dx == 0 && dz == 0 && dy == 0) continue;
                        BlockPos cell = new BlockPos(tb.getX() + dx, tb.getY() + dy, tb.getZ() + dz);
                        if (!mc.level.getBlockState(cell).canBeReplaced()) continue;
                        if (!freeSpace(mc, p, t, cell)) continue;
                        Vec3 c = Vec3.atCenterOf(cell);
                        if (eye.distanceTo(c) > reach.value || me.distanceTo(c) < minDist.value) continue;
                        double dt = estimate(c, tp, 5.0);
                        double ds = estimate(c, me, 5.0) * (shielded ? 0.5 : 1.0);
                        if (dt < 4.0 || ds > maxSelf.value) continue;
                        if (dt - ds > bestScore) {
                            bestScore = dt - ds;
                            best = cell;
                        }
                    }
                }
            }
            return best;
        }

        /** Setzt Glowstone zwischen dich und den Gegner (Fuss- und Kopfhoehe). true = in diesem Tick etwas gesetzt. */
        private boolean placeShield(Minecraft mc, LocalPlayer p, LivingEntity t, int glow, BlockPos anchor) {
            if (glow < 0) return false;
            Vec3 d = t.position().subtract(p.position());
            Direction dir = Direction.getApproximateNearest(d.x, 0.0, d.z);
            BlockPos s1 = p.blockPosition().relative(dir);
            BlockPos[] cells = {s1, s1.above()};
            for (BlockPos cell : cells) {
                if (cell.equals(anchor)) continue;
                if (!mc.level.getBlockState(cell).canBeReplaced()) continue;
                if (!freeSpace(mc, p, t, cell)) continue;
                final BlockPos c = cell;
                withSlot(mc, glow, () -> Modules.placeBlockAt(mc, c));
                return true;
            }
            return false;
        }

        private int nonGlowSlot(LocalPlayer p) {
            for (int i = 0; i < 9; i++) {
                if (p.getInventory().getItem(i).isEmpty()) return i;
            }
            int cur = p.getInventory().getSelectedSlot();
            if (!path(p.getInventory().getItem(cur)).equals("glowstone")) return cur;
            for (int i = 0; i < 9; i++) {
                if (!path(p.getInventory().getItem(i)).equals("glowstone")) return i;
            }
            return -1;
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || mc.screen != null) return;
            if (cd > 0) {
                cd--;
                return;
            }
            if (p.getHealth() + p.getAbsorptionAmount() <= safeHp.value) return;
            LivingEntity t = Combo.nearest(mc, targetRange.value, true, false);
            if (t == null) return;
            int glow = exact(p, "glowstone");
            boolean shielded = shield.on && glow >= 0;

            BlockPos anchor = findAnchor(mc, p, t.blockPosition());
            if (anchor == null) {
                // Neuen Anker setzen (vorher Schild)
                int a = exact(p, "respawn_anchor");
                if (a < 0) return;
                if (shielded && placeShield(mc, p, t, glow, null)) {
                    cd = (int) delay.value;
                    return;
                }
                BlockPos spot = bestAnchorSpot(mc, p, t, shielded);
                if (spot == null) return;
                final BlockPos s = spot;
                withSlot(mc, a, () -> Modules.placeBlockAt(mc, s));
                cd = (int) delay.value;
                return;
            }

            // Schild immer zuerst nachsetzen
            if (shielded && placeShield(mc, p, t, glow, anchor)) {
                cd = (int) delay.value;
                return;
            }

            int charge = mc.level.getBlockState(anchor).getValue(RespawnAnchorBlock.CHARGE);
            if (charge <= 0) {
                if (glow < 0) return;
                final BlockPos an = anchor;
                withSlot(mc, glow, () -> useOn(mc, InteractionHand.MAIN_HAND, an, Direction.UP));
                cd = (int) delay.value;
                return;
            }

            // Geladen: mit etwas anderem als Glowstone benutzen = Explosion
            int other = nonGlowSlot(p);
            if (other < 0) return;
            final BlockPos an = anchor;
            withSlot(mc, other, () -> useOn(mc, InteractionHand.MAIN_HAND, an, Direction.UP));
            cd = (int) delay.value;
        }
    }
}
