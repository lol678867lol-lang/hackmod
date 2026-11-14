package de.hackmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

    /** Schaden, den DU bekommst: Explosion minus Ruestung (Vanilla-Formel), mal Blastschutz-Schaetzung. */
    static double selfDamage(LocalPlayer p, Vec3 center, double power, double factor) {
        double d = estimate(center, p.position(), power);
        if (d <= 0.0) return 0.0;
        double armor = p.getArmorValue();
        double tough = p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double f = Math.min(20.0, Math.max(armor / 5.0, armor - d / (2.0 + tough / 4.0)));
        return d * (1.0 - f / 25.0) * factor;
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

    private static boolean freeSpace(LocalPlayer p, LivingEntity t, BlockPos cell) {
        AABB box = new AABB(cell);
        return !p.getBoundingBox().intersects(box) && !t.getBoundingBox().intersects(box);
    }

    private static void drawStatus(GuiGraphics g, Minecraft mc, String name, String status) {
        int W = mc.getWindow().getGuiScaledWidth(), H = mc.getWindow().getGuiScaledHeight();
        String s = name + ": " + status;
        g.drawString(mc.font, s, (W - mc.font.width(s)) / 2, H - 75, 0xFFFFD166);
    }

    // ------------------------------------------------------------------ CrystalAura

    public static class CrystalAura extends Module {
        final Setting targetRange = add(Setting.number("Zielradius", 10, 4, 20, 1));
        final Setting reach = add(Setting.number("Reichweite", 4.5, 3.0, 5.5, 0.5));
        final Setting delay = add(Setting.number("Pause (Ticks)", 2, 0, 10, 1));
        final Setting minDmg = add(Setting.number("Min. Schaden Gegner", 6, 1, 30, 1));
        final Setting selfCheck = add(Setting.toggle("Selbstschutz", true));
        final Setting maxSelf = add(Setting.number("Max. Schaden dir", 15, 1, 30, 1));
        final Setting selfFactor = add(Setting.number("Blastschutz-Faktor", 0.5, 0.1, 1.0, 0.1));
        final Setting safeHp = add(Setting.number("Stopp unter HP", 6, 0, 19, 1));
        final Setting placeObi = add(Setting.toggle("Obsidian setzen", true));
        final Setting breakOn = add(Setting.toggle("Kristalle zerschlagen", true));
        final Setting players = add(Setting.toggle("Spieler", true));
        final Setting mobs = add(Setting.toggle("Mobs", true));
        private int cd;
        private String status = "bereit";

        CrystalAura() {
            super("CrystalAura", "Setzt Obsidian und Enderkristalle neben Gegner und sprengt sie - mit Schadensrechnung, damit du selbst wenig abbekommst. Kristalle + Obsidian in die Hotbar. Unten steht, was gerade fehlt.", Category.CRYSTAL);
        }

        private double self(LocalPlayer p, Vec3 c) {
            return selfCheck.on ? selfDamage(p, c, 6.0, selfFactor.value) : 0.0;
        }

        private double limit(LocalPlayer p) {
            return Math.min(maxSelf.value, p.getHealth() + p.getAbsorptionAmount() - safeHp.value);
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
            Vec3 tp = t.position(), eye = p.getEyePosition();
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
                        double dt = estimate(c, tp, 6.0), ds = self(p, c);
                        if (dt < minDmg.value || ds > limit(p)) continue;
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
            Vec3 tp = t.position(), eye = p.getEyePosition();
            BlockPos best = null;
            double bestScore = -1e9;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos cell = new BlockPos(tb.getX() + dx, tb.getY(), tb.getZ() + dz);
                    if (!mc.level.getBlockState(cell).canBeReplaced()) continue;
                    if (mc.level.getBlockState(cell.below()).getCollisionShape(mc.level, cell.below()).isEmpty()) continue;
                    if (!mc.level.getBlockState(cell.above()).isAir() || !mc.level.getBlockState(cell.above(2)).isAir()) continue;
                    if (!freeSpace(p, t, cell)) continue;
                    if (eye.distanceTo(Vec3.atCenterOf(cell)) > reach.value) continue;
                    Vec3 c = new Vec3(cell.getX() + 0.5, cell.getY() + 1.0, cell.getZ() + 0.5);
                    double dt = estimate(c, tp, 6.0), ds = self(p, c);
                    if (dt < minDmg.value || ds > limit(p)) continue;
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
        public void onHud(GuiGraphics g, Minecraft mc) {
            drawStatus(g, mc, "CrystalAura", status);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || mc.screen != null) return;
            if (cd > 0) {
                cd--;
                return;
            }
            if (p.getHealth() + p.getAbsorptionAmount() <= safeHp.value) {
                status = "zu wenig Leben (Stopp unter HP)";
                return;
            }
            LivingEntity t = Combo.nearest(mc, targetRange.value, players.on, mobs.on);
            if (t == null) {
                status = "kein Ziel in " + (int) targetRange.value + " Bloecken (Spieler/Mobs-Schalter pruefen)";
                return;
            }
            Vec3 tp = t.position();

            // 1) Kristalle zerschlagen
            if (breakOn.on) {
                Entity best = null;
                double bestScore = -1e9;
                AABB area = p.getBoundingBox().inflate(reach.value + 1.0);
                for (Entity e : mc.level.getEntities((Entity) null, area, x -> true)) {
                    if (!isCrystal(e) || !e.isAlive() || p.distanceTo(e) > reach.value) continue;
                    Vec3 c = e.position();
                    double dt = estimate(c, tp, 6.0), ds = self(p, c);
                    if (dt < minDmg.value || ds > limit(p)) continue;
                    if (dt - ds > bestScore) {
                        bestScore = dt - ds;
                        best = e;
                    }
                }
                if (best != null) {
                    mc.gameMode.attack(p, best);
                    p.swing(InteractionHand.MAIN_HAND);
                    status = "Kristall zerschlagen";
                    cd = (int) delay.value;
                    return;
                }
            }

            // 2) Kristall auf vorhandene Obsidian-Basis setzen
            boolean off = path(p.getOffhandItem()).equals("end_crystal");
            int crystalSlot = off ? -1 : exact(p, "end_crystal");
            if (!off && crystalSlot < 0) {
                status = "kein Enderkristall in Hotbar/Zweithand";
                return;
            }
            BlockPos base = bestBase(mc, p, t);
            if (base != null) {
                final BlockPos b = base;
                if (off) {
                    useOn(mc, InteractionHand.OFF_HAND, b, Direction.UP);
                } else {
                    withSlot(mc, crystalSlot, () -> useOn(mc, InteractionHand.MAIN_HAND, b, Direction.UP));
                }
                status = "Kristall gesetzt";
                cd = (int) delay.value;
                return;
            }

            // 3) Obsidian setzen
            if (!placeObi.on) {
                status = "keine Obsidian-Basis in der Naehe (Obsidian setzen ist aus)";
                return;
            }
            int obi = exact(p, "obsidian");
            if (obi < 0) {
                status = "kein Obsidian in der Hotbar";
                return;
            }
            BlockPos spot = bestObiSpot(mc, p, t);
            if (spot == null) {
                status = "keine passende Stelle (Platz, Reichweite oder Selbstschaden)";
                return;
            }
            final BlockPos s = spot;
            withSlot(mc, obi, () -> Modules.placeBlockAt(mc, s));
            status = "Obsidian gesetzt";
            cd = (int) delay.value;
        }
    }

    // ------------------------------------------------------------------ AnchorAura

    public static class AnchorAura extends Module {
        final Setting targetRange = add(Setting.number("Zielradius", 8, 4, 16, 1));
        final Setting reach = add(Setting.number("Reichweite", 4.5, 3.0, 5.5, 0.5));
        final Setting delay = add(Setting.number("Pause (Ticks)", 2, 0, 10, 1));
        final Setting minDist = add(Setting.number("Min. Abstand", 2, 1, 6, 1));
        final Setting selfCheck = add(Setting.toggle("Selbstschutz", true));
        final Setting maxSelf = add(Setting.number("Max. Schaden dir", 15, 1, 30, 1));
        final Setting selfFactor = add(Setting.number("Blastschutz-Faktor", 0.5, 0.1, 1.0, 0.1));
        final Setting safeHp = add(Setting.number("Stopp unter HP", 6, 0, 19, 1));
        final Setting shield = add(Setting.toggle("Glowstone-Schild", true));
        final Setting players = add(Setting.toggle("Spieler", true));
        final Setting mobs = add(Setting.toggle("Mobs", true));
        private int cd;
        private String status = "bereit";

        AnchorAura() {
            super("AnchorAura", "Respawn-Anker neben den Gegner, mit Glowstone laden und sprengen. Setzt vorher Glowstone als Schild vor dich. Anker + Glowstone in die Hotbar. Geht NICHT im Nether. Unten steht, was gerade fehlt.", Category.CRYSTAL);
        }

        private double limit(LocalPlayer p) {
            return Math.min(maxSelf.value, p.getHealth() + p.getAbsorptionAmount() - safeHp.value);
        }

        private BlockPos findAnchor(Minecraft mc, LocalPlayer p, BlockPos around) {
            Vec3 eye = p.getEyePosition();
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -4; dz <= 4; dz++) {
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
                        if (!freeSpace(p, t, cell)) continue;
                        Vec3 c = Vec3.atCenterOf(cell);
                        if (eye.distanceTo(c) > reach.value || me.distanceTo(c) < minDist.value) continue;
                        double dt = estimate(c, tp, 5.0);
                        double ds = (selfCheck.on ? selfDamage(p, c, 5.0, selfFactor.value) : 0.0) * (shielded ? 0.5 : 1.0);
                        if (dt < 4.0 || ds > limit(p)) continue;
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
                if (!freeSpace(p, t, cell)) continue;
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
        public void onHud(GuiGraphics g, Minecraft mc) {
            drawStatus(g, mc, "AnchorAura", status);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || mc.screen != null) return;
            if (cd > 0) {
                cd--;
                return;
            }
            if (p.getHealth() + p.getAbsorptionAmount() <= safeHp.value) {
                status = "zu wenig Leben (Stopp unter HP)";
                return;
            }
            LivingEntity t = Combo.nearest(mc, targetRange.value, players.on, mobs.on);
            if (t == null) {
                status = "kein Ziel in " + (int) targetRange.value + " Bloecken (Spieler/Mobs-Schalter pruefen)";
                return;
            }
            int glow = exact(p, "glowstone");
            boolean shielded = shield.on && glow >= 0;

            BlockPos anchor = findAnchor(mc, p, t.blockPosition());
            if (anchor == null) {
                // Neuen Anker setzen (vorher Schild)
                int a = exact(p, "respawn_anchor");
                if (a < 0) {
                    status = "kein Respawn-Anker in der Hotbar";
                    return;
                }
                if (shielded && placeShield(mc, p, t, glow, null)) {
                    status = "Glowstone-Schild gesetzt";
                    cd = (int) delay.value;
                    return;
                }
                BlockPos spot = bestAnchorSpot(mc, p, t, shielded);
                if (spot == null) {
                    status = "keine passende Stelle (Reichweite, Abstand oder Selbstschaden - Selbstschutz aus?)";
                    return;
                }
                final BlockPos s = spot;
                withSlot(mc, a, () -> Modules.placeBlockAt(mc, s));
                status = "Anker gesetzt";
                cd = (int) delay.value;
                return;
            }

            // Schild immer zuerst nachsetzen
            if (shielded && placeShield(mc, p, t, glow, anchor)) {
                status = "Glowstone-Schild gesetzt";
                cd = (int) delay.value;
                return;
            }

            int charge = mc.level.getBlockState(anchor).getValue(RespawnAnchorBlock.CHARGE);
            if (charge <= 0) {
                if (glow < 0) {
                    status = "kein Glowstone in der Hotbar zum Laden";
                    return;
                }
                final BlockPos an = anchor;
                withSlot(mc, glow, () -> useOn(mc, InteractionHand.MAIN_HAND, an, Direction.UP));
                status = "Anker geladen";
                cd = (int) delay.value;
                return;
            }

            // Geladen: mit etwas anderem als Glowstone benutzen = Explosion
            int other = nonGlowSlot(p);
            if (other < 0) {
                status = "kein Slot ohne Glowstone zum Zuenden";
                return;
            }
            final BlockPos an = anchor;
            withSlot(mc, other, () -> useOn(mc, InteractionHand.MAIN_HAND, an, Direction.UP));
            status = "Anker gezuendet";
            cd = (int) delay.value;
        }
    }
}
