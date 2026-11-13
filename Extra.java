package de.hackmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Viele weitere Hacks. Jede Klasse ist ein Modul (siehe Modules.init). */
public final class Extra {
    private Extra() {}

    private static final double[] RANGES = {16, 32, 64, 128, 256, 512, 1000, 2000, 5000, 10000};
    private static final Random RND = new Random();

    // ------------------------------------------------------------------ Helfer

    private static boolean moving(Minecraft mc) {
        return mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
    }

    private static String path(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    /** Hotbar-Slot mit genau diesem Gegenstand (z.B. "bucket"), sonst -1. */
    private static int exact(LocalPlayer p, String name) {
        for (int i = 0; i < 9; i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (!st.isEmpty() && path(st).equals(name)) return i;
        }
        return -1;
    }

    /** true, wenn vor deinen Fuessen (in Laufrichtung) der Boden fehlt. */
    private static boolean edgeAhead(Minecraft mc, LocalPlayer p) {
        double[] d = Cam.moveDir(mc, p.getYRot());
        if (d[0] == 0 && d[1] == 0) return false;
        BlockPos b = BlockPos.containing(p.getX() + d[0] * 0.45, p.getY() - 0.6, p.getZ() + d[1] * 0.45);
        return mc.level.getBlockState(b).getCollisionShape(mc.level, b).isEmpty();
    }

    private static void jump(LocalPlayer p, double power) {
        Vec3 m = p.getDeltaMovement();
        p.setDeltaMovement(m.x, power, m.z);
    }

    // ================================================================== BEWEGUNG

    public static class BunnyHop extends Module {
        final Setting boost = add(Setting.number("Tempo", 1.3, 1.0, 3.0, 0.1));

        BunnyHop() {
            super("BunnyHop", "Springt beim Laufen automatisch und beschleunigt dabei.", Category.BEWEGUNG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            double[] d = Cam.moveDir(mc, p.getYRot());
            if ((d[0] == 0 && d[1] == 0) || mc.options.keyShift.isDown()) return;
            Vec3 m = p.getDeltaMovement();
            double y = p.onGround() ? 0.42 : m.y;
            double v = 0.23 * boost.value;
            p.setDeltaMovement(d[0] * v, y, d[1] * v);
        }
    }

    public static class AutoJump extends Module {
        AutoJump() {
            super("AutoJump", "Springt automatisch ueber Bloecke, wenn du dagegen laeufst.", Category.BEWEGUNG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p.onGround() && p.horizontalCollision && moving(mc)) jump(p, 0.42);
        }
    }

    public static class EdgeSneak extends Module {
        private boolean forced;

        EdgeSneak() {
            super("EdgeSneak", "Schleicht automatisch an Kanten, damit du nicht herunterfaellst.", Category.BEWEGUNG);
        }

        @Override
        protected void onDisable() {
            if (forced) Minecraft.getInstance().options.keyShift.setDown(false);
            forced = false;
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            boolean edge = p.onGround() && edgeAhead(mc, p);
            if (edge) {
                mc.options.keyShift.setDown(true);
                forced = true;
            } else if (forced) {
                mc.options.keyShift.setDown(false);
                forced = false;
            }
        }
    }

    public static class Sneak extends Module {
        Sneak() {
            super("Sneak", "Schleicht dauerhaft.", Category.BEWEGUNG);
        }

        @Override
        public void onTick(Minecraft mc) { mc.options.keyShift.setDown(true); }

        @Override
        protected void onDisable() { Minecraft.getInstance().options.keyShift.setDown(false); }
    }

    public static class Parkour extends Module {
        Parkour() {
            super("Parkour", "Springt automatisch an der Kante ab.", Category.BEWEGUNG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p.onGround() && !mc.options.keyShift.isDown() && edgeAhead(mc, p)) jump(p, 0.42);
        }
    }

    public static class Dolphin extends Module {
        final Setting factor = add(Setting.number("Faktor", 1.15, 1.0, 1.6, 0.05));

        Dolphin() {
            super("Dolphin", "Viel schneller schwimmen.", Category.BEWEGUNG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (!p.isInWater()) return;
            Vec3 m = p.getDeltaMovement();
            double hs = Math.sqrt(m.x * m.x + m.z * m.z);
            double f = hs * factor.value > 0.9 ? 1.0 : factor.value;
            p.setDeltaMovement(m.x * f, m.y + (mc.options.keyJump.isDown() ? 0.03 : 0), m.z * f);
        }
    }

    public static class Twerk extends Module {
        private int t;

        Twerk() {
            super("Twerk", "Wechselt ganz schnell zwischen Schleichen und Stehen.", Category.BEWEGUNG);
        }

        @Override
        public void onTick(Minecraft mc) { mc.options.keyShift.setDown((t++ & 1) == 0); }

        @Override
        protected void onDisable() { Minecraft.getInstance().options.keyShift.setDown(false); }
    }

    // ================================================================== FLUG

    public static class Jetpack extends Module {
        final Setting power = add(Setting.number("Kraft", 1.0, 0.3, 3.0, 0.1));

        Jetpack() {
            super("Jetpack", "Leertaste in der Luft halten = nach oben schweben.", Category.FLUG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.options.keyJump.isDown() && !p.onGround()) {
                Vec3 m = p.getDeltaMovement();
                double vy = Math.min(m.y + 0.08 * power.value, 0.9 * power.value);
                p.setDeltaMovement(m.x, vy, m.z);
                p.fallDistance = 0;
            }
        }
    }

    public static class Hover extends Module {
        Hover() {
            super("Hover", "Schwebt in der Luft (Leertaste = hoch, Shift = runter).", Category.FLUG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p.onGround()) return;
            Vec3 m = p.getDeltaMovement();
            double vy = mc.options.keyJump.isDown() ? 0.25 : (mc.options.keyShift.isDown() ? -0.25 : 0.0);
            p.setDeltaMovement(m.x * 0.6, vy, m.z * 0.6);
            p.fallDistance = 0;
        }
    }

    public static class ElytraFly extends Module {
        final Setting speed = add(Setting.number("Tempo", 1.5, 0.5, 4.0, 0.1));

        ElytraFly() {
            super("ElytraFly", "Elytra steuern: W = Schub in Blickrichtung, S = bremsen, Leertaste = Hoehe halten.", Category.FLUG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (!p.isFallFlying()) return;
            Vec3 look = p.getViewVector(1.0F);
            Vec3 m = p.getDeltaMovement();
            if (mc.options.keyUp.isDown()) {
                p.setDeltaMovement(look.x * speed.value, look.y * speed.value, look.z * speed.value);
            } else if (mc.options.keyDown.isDown()) {
                p.setDeltaMovement(m.x * 0.8, m.y * 0.8, m.z * 0.8);
            } else if (mc.options.keyJump.isDown()) {
                p.setDeltaMovement(m.x, 0.0, m.z);
            }
        }
    }

    public static class AutoRocket extends Module {
        final Setting minSpeed = add(Setting.number("Unter Tempo", 1.0, 0.3, 3.0, 0.1));
        private int cd;

        AutoRocket() {
            super("AutoRocket", "Zuendet beim Elytra-Flug automatisch Feuerwerksraketen aus der Hotbar.", Category.FLUG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (cd > 0) cd--;
            if (!p.isFallFlying() || cd > 0 || mc.screen != null) return;
            if (p.getDeltaMovement().length() >= minSpeed.value) return;
            int s = Modules.findHotbar(p, "firework_rocket");
            if (s < 0) return;
            Combo.use(mc, s, p.getYRot(), p.getXRot());
            cd = 40;
        }
    }

    public static class NoClip extends Module {
        final Setting speed = add(Setting.number("Tempo", 1.0, 0.2, 4.0, 0.1));

        NoClip() {
            super("NoClip", "Durch Waende fliegen (Leertaste / Shift). Server kann dich zuruecksetzen.", Category.FLUG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            p.noPhysics = true;
            double s = speed.value * 0.5;
            double[] d = Cam.moveDir(mc, p.getYRot());
            double vy = mc.options.keyJump.isDown() ? s : (mc.options.keyShift.isDown() ? -s : 0.0);
            p.setDeltaMovement(d[0] * s, vy, d[1] * s);
            p.fallDistance = 0;
        }

        @Override
        protected void onDisable() {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) p.noPhysics = false;
        }
    }

    public static class LongJump extends Module {
        final Setting power = add(Setting.number("Weite", 1.0, 0.4, 3.0, 0.1));

        LongJump() {
            super("LongJump", "Beim Abspringen ein grosser Satz nach vorne.", Category.FLUG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            double[] d = Cam.moveDir(mc, p.getYRot());
            if (p.onGround() && mc.options.keyJump.isDown() && (d[0] != 0 || d[1] != 0)) {
                p.setDeltaMovement(d[0] * power.value, 0.5, d[1] * power.value);
            }
        }
    }

    public static class FastFall extends Module {
        final Setting power = add(Setting.number("Kraft", 1.0, 0.3, 3.0, 0.1));

        FastFall() {
            super("FastFall", "Shift in der Luft halten = viel schneller fallen.", Category.FLUG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (!p.onGround() && mc.options.keyShift.isDown()) {
                Vec3 m = p.getDeltaMovement();
                p.setDeltaMovement(m.x, Math.max(m.y - 0.15 * power.value, -3.0), m.z);
            }
        }
    }

    // ================================================================== KAMPF

    public static class MultiAura extends Module {
        final Setting range = add(Setting.number("Reichweite", 4.2, 2.0, 6.0, 0.1));
        final Setting players = add(Setting.toggle("Spieler", true));
        final Setting mobs = add(Setting.toggle("Mobs", true));
        private int idx;

        MultiAura() {
            super("MultiAura", "Verteilt die Schlaege reihum auf alle Ziele in Reichweite.", Category.KAMPF);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || p.getAttackStrengthScale(0.0F) < 1.0F) return;
            List<LivingEntity> list = Combo.targets(mc, range.value, players.on, mobs.on);
            if (list.isEmpty()) return;
            idx = (idx + 1) % list.size();
            mc.gameMode.attack(p, list.get(idx));
            p.swing(InteractionHand.MAIN_HAND);
        }
    }

    public static class CritAura extends Module {
        final Setting range = add(Setting.number("Reichweite", 4.0, 2.0, 6.0, 0.1));
        final Setting players = add(Setting.toggle("Spieler", true));
        final Setting mobs = add(Setting.toggle("Mobs", true));

        CritAura() {
            super("CritAura", "Springt und schlaegt erst beim Fallen zu = jeder Treffer ist ein Crit.", Category.KAMPF);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null) return;
            LivingEntity t = Combo.nearest(mc, range.value, players.on, mobs.on);
            if (t == null) return;
            if (p.onGround()) {
                if (p.getAttackStrengthScale(0.0F) >= 0.9F) jump(p, 0.42);
                return;
            }
            if (p.fallDistance > 0 && p.getDeltaMovement().y < 0 && !p.isInWater() && p.getAttackStrengthScale(0.0F) >= 1.0F) {
                mc.gameMode.attack(p, t);
                p.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    public static class AutoClicker extends Module {
        final Setting delay = add(Setting.number("Pause (Ticks)", 10, 1, 20, 1));
        private int cd;

        AutoClicker() {
            super("AutoClicker", "Linksklick halten = automatisch schlagen, sobald ein Ziel im Fadenkreuz ist.", Category.KAMPF);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (cd > 0) {
                cd--;
                return;
            }
            if (mc.screen != null || mc.gameMode == null || !mc.options.keyAttack.isDown()) return;
            if (mc.hitResult instanceof EntityHitResult ehr) {
                mc.gameMode.attack(p, ehr.getEntity());
                p.swing(InteractionHand.MAIN_HAND);
                cd = (int) delay.value;
            }
        }
    }

    public static class AutoWeapon extends Module {
        final Setting axe = add(Setting.toggle("Axt statt Schwert", false));

        AutoWeapon() {
            super("AutoWeapon", "Nimmt beim Angreifen automatisch Schwert oder Axt aus der Hotbar.", Category.KAMPF);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.screen != null || !mc.options.keyAttack.isDown()) return;
            if (!(mc.hitResult instanceof EntityHitResult)) return;
            int sword = Modules.findHotbar(p, "_sword");
            int ax = Modules.findHotbar(p, "_axe");
            int s = axe.on ? (ax >= 0 ? ax : sword) : (sword >= 0 ? sword : ax);
            if (s >= 0) p.getInventory().setSelectedSlot(s);
        }
    }

    public static class FastBow extends Module {
        final Setting ticks = add(Setting.number("Ladezeit (Ticks)", 3, 1, 20, 1));

        FastBow() {
            super("FastBow", "Laesst den Bogen schon nach kurzer Zeit los (Rechtsklick halten).", Category.KAMPF);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || !p.isUsingItem()) return;
            if (!path(p.getUseItem()).equals("bow")) return;
            if (p.getTicksUsingItem() >= (int) ticks.value) mc.gameMode.releaseUsingItem(p);
        }
    }

    public static class SpinBot extends Module {
        final Setting speed = add(Setting.number("Tempo", 40, 5, 90, 5));
        private float yaw;

        SpinBot() {
            super("SpinBot", "Andere sehen dich wild kreiseln (deine Kamera bleibt ruhig).", Category.KAMPF);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.getConnection() == null) return;
            yaw += (float) speed.value;
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, p.getXRot(), p.onGround(), p.horizontalCollision));
        }
    }

    public static class Derp extends Module {
        Derp() {
            super("Derp", "Dein Kopf zuckt fuer andere wild in alle Richtungen (Kamera bleibt ruhig).", Category.KAMPF);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.getConnection() == null) return;
            float yaw = RND.nextFloat() * 360.0F - 180.0F;
            float pitch = RND.nextFloat() * 180.0F - 90.0F;
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, p.onGround(), p.horizontalCollision));
        }
    }

    public static class CrystalBreaker extends Module {
        final Setting range = add(Setting.number("Reichweite", 4.5, 2.0, 6.0, 0.5));

        CrystalBreaker() {
            super("CrystalBreaker", "Zerschlaegt Enderkristalle in Reichweite sofort.", Category.KAMPF);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null) return;
            AABB area = p.getBoundingBox().inflate(range.value);
            for (Entity e : mc.level.getEntities((Entity) null, area, x -> true)) {
                if (!e.isAlive()) continue;
                if (BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("end_crystal")
                        && p.distanceTo(e) <= range.value) {
                    mc.gameMode.attack(p, e);
                    p.swing(InteractionHand.MAIN_HAND);
                    return;
                }
            }
        }
    }

    public static class WebTrap extends Module {
        final Setting range = add(Setting.number("Reichweite", 5.0, 2.0, 6.0, 0.5));
        private int cd;

        WebTrap() {
            super("WebTrap", "Setzt Spinnweben auf Gegner in der Naehe (Cobweb in die Hotbar).", Category.KAMPF);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (cd > 0) {
                cd--;
                return;
            }
            if (mc.gameMode == null || mc.screen != null) return;
            int s = Modules.findHotbar(p, "cobweb");
            if (s < 0) return;
            LivingEntity t = Combo.nearest(mc, range.value, true, false);
            if (t == null) return;
            BlockPos bp = t.blockPosition();
            if (!mc.level.getBlockState(bp).canBeReplaced()) return;
            int prev = p.getInventory().getSelectedSlot();
            p.getInventory().setSelectedSlot(s);
            Modules.placeBlockAt(mc, bp);
            p.getInventory().setSelectedSlot(prev);
            cd = 10;
        }
    }

    // ================================================================== SCHUTZ

    public static class AutoGapple extends Module {
        final Setting hp = add(Setting.number("HP ab", 10, 2, 19, 1));
        private int restore = -1;
        private boolean eating;

        AutoGapple() {
            super("AutoGapple", "Isst goldene Aepfel aus der Hotbar, sobald deine Leben unter den Wert fallen.", Category.SCHUTZ);
        }

        private int find(LocalPlayer p) {
            int e = exact(p, "enchanted_golden_apple");
            return e >= 0 ? e : exact(p, "golden_apple");
        }

        private void stop(Minecraft mc) {
            if (eating) mc.options.keyUse.setDown(false);
            eating = false;
            if (restore >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(restore);
            restore = -1;
        }

        @Override
        protected void onDisable() { stop(Minecraft.getInstance()); }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.screen != null) return;
            int s = (p.getHealth() <= hp.value) ? find(p) : -1;
            if (s < 0) {
                if (eating) stop(mc);
                return;
            }
            if (!eating) {
                restore = p.getInventory().getSelectedSlot();
                eating = true;
            }
            p.getInventory().setSelectedSlot(s);
            mc.options.keyUse.setDown(true);
        }
    }

    public static class AutoArmor extends Module {
        private int cd;

        AutoArmor() {
            super("AutoArmor", "Zieht fehlende Ruestungsteile aus dem Inventar automatisch an.", Category.SCHUTZ);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || mc.screen != null) return;
            if (cd > 0) {
                cd--;
                return;
            }
            String[] parts = {"_helmet", "_chestplate", "_leggings", "_boots"};
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int k = 0; k < 4; k++) {
                if (!p.getItemBySlot(slots[k]).isEmpty()) continue;
                for (int i = 9; i <= 44; i++) {
                    ItemStack st = p.inventoryMenu.getSlot(i).getItem();
                    if (!st.isEmpty() && path(st).endsWith(parts[k])) {
                        mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, i, 0, ClickType.QUICK_MOVE, p);
                        cd = 3;
                        return;
                    }
                }
            }
        }
    }

    public static class AutoBlock extends Module {
        final Setting range = add(Setting.number("Reichweite", 4.5, 2.0, 8.0, 0.5));
        private boolean forced;

        AutoBlock() {
            super("AutoBlock", "Hebt den Schild (Zweithand) automatisch, wenn ein Spieler nah ist.", Category.SCHUTZ);
        }

        @Override
        protected void onDisable() {
            if (forced) Minecraft.getInstance().options.keyUse.setDown(false);
            forced = false;
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            boolean shield = path(p.getOffhandItem()).equals("shield");
            boolean near = shield && Combo.nearest(mc, range.value, true, false) != null;
            if (near) {
                mc.options.keyUse.setDown(true);
                forced = true;
            } else if (forced) {
                mc.options.keyUse.setDown(false);
                forced = false;
            }
        }
    }

    public static class Surround extends Module {
        private static final Direction[] DIRS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        private int cd;

        Surround() {
            super("Surround", "Mauert dich mit Obsidian ein (Obsidian in die Hotbar).", Category.SCHUTZ);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (cd > 0) {
                cd--;
                return;
            }
            if (mc.gameMode == null || mc.screen != null) return;
            int s = Modules.findHotbar(p, "obsidian");
            if (s < 0) return;
            BlockPos feet = p.blockPosition();
            int placed = 0;
            int prev = p.getInventory().getSelectedSlot();
            p.getInventory().setSelectedSlot(s);
            for (Direction dir : DIRS) {
                BlockPos bp = feet.relative(dir);
                if (!mc.level.getBlockState(bp).canBeReplaced()) continue;
                if (p.getBoundingBox().intersects(new AABB(bp))) continue;
                Modules.placeBlockAt(mc, bp);
                if (++placed >= 2) break;
            }
            p.getInventory().setSelectedSlot(prev);
            if (placed > 0) cd = 2;
        }
    }

    public static class AutoXP extends Module {
        final Setting below = add(Setting.number("Haltbark. unter %", 60, 10, 95, 5));
        private int cd;

        AutoXP() {
            super("AutoXP", "Wirft Erfahrungsflaschen auf deine Fuesse, wenn die Ruestung kaputt geht (Flaschen in die Hotbar).", Category.SCHUTZ);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (cd > 0) {
                cd--;
                return;
            }
            if (mc.screen != null) return;
            int s = Modules.findHotbar(p, "experience_bottle");
            if (s < 0) return;
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            boolean worn = false;
            for (EquipmentSlot es : slots) {
                ItemStack st = p.getItemBySlot(es);
                if (st.isEmpty() || !st.isDamageableItem() || st.getDamageValue() <= 0) continue;
                double left = 100.0 * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage();
                if (left < below.value) worn = true;
            }
            if (!worn) return;
            Combo.use(mc, s, p.getYRot(), 90.0F);
            cd = 6;
        }
    }

    // ================================================================== BAUEN

    public static class FastPlace extends Module {
        final Setting delay = add(Setting.number("Pause (Ticks)", 1, 0, 4, 1));
        private int cd;

        FastPlace() {
            super("FastPlace", "Bloecke blitzschnell setzen, solange du Rechtsklick haeltst.", Category.BAUEN);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (cd > 0) {
                cd--;
                return;
            }
            if (mc.screen != null || mc.gameMode == null || !mc.options.keyUse.isDown()) return;
            if (!(p.getMainHandItem().getItem() instanceof BlockItem)) return;
            if (mc.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
                mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, bhr);
                p.swing(InteractionHand.MAIN_HAND);
                cd = (int) delay.value;
            }
        }
    }

    public static class Nuker extends Module {
        final Setting radius = add(Setting.number("Radius", 3, 1, 5, 1));
        final Setting perTick = add(Setting.number("Bloecke/Tick", 3, 1, 8, 1));
        private BlockPos target;

        Nuker() {
            super("Nuker", "Baut alle Bloecke um dich herum ab.", Category.BAUEN);
        }

        @Override
        protected void onDisable() { target = null; }

        private BlockPos pick(Minecraft mc) {
            LocalPlayer p = mc.player;
            Vec3 eye = p.getEyePosition();
            int r = (int) radius.value;
            BlockPos c = p.blockPosition();
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                        BlockState s = mc.level.getBlockState(m);
                        if (s.isAir() || s.getBlock() instanceof LiquidBlock) continue;
                        if (s.getDestroySpeed(mc.level, m) < 0) continue;
                        double d2 = eye.distanceToSqr(m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5);
                        if (d2 > r * r + 1.0) continue;
                        if (d2 < bestD) {
                            bestD = d2;
                            best = m.immutable();
                        }
                    }
                }
            }
            return best;
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || mc.screen != null) return;
            Vec3 eye = p.getEyePosition();
            if (p.isCreative()) {
                for (int i = 0; i < (int) perTick.value; i++) {
                    BlockPos t = pick(mc);
                    if (t == null) return;
                    Direction dir = Direction.getApproximateNearest(eye.x - (t.getX() + 0.5), eye.y - (t.getY() + 0.5), eye.z - (t.getZ() + 0.5));
                    mc.gameMode.startDestroyBlock(t, dir);
                }
                p.swing(InteractionHand.MAIN_HAND);
                return;
            }
            if (target != null && mc.level.getBlockState(target).isAir()) target = null;
            if (target == null) target = pick(mc);
            if (target == null) return;
            Direction dir = Direction.getApproximateNearest(eye.x - (target.getX() + 0.5), eye.y - (target.getY() + 0.5), eye.z - (target.getZ() + 0.5));
            mc.gameMode.continueDestroyBlock(target, dir);
            p.swing(InteractionHand.MAIN_HAND);
        }
    }

    public static class CropBreaker extends Module {
        final Setting radius = add(Setting.number("Radius", 4, 1, 5, 1));
        private int cd;

        CropBreaker() {
            super("CropBreaker", "Erntet reife Pflanzen (Weizen, Karotten, Kartoffeln, Rote Bete) in der Naehe.", Category.BAUEN);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || mc.screen != null) return;
            if (cd > 0) {
                cd--;
                return;
            }
            int r = (int) radius.value;
            BlockPos c = p.blockPosition();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            int done = 0;
            for (int dx = -r; dx <= r && done < 3; dx++) {
                for (int dy = -2; dy <= 2 && done < 3; dy++) {
                    for (int dz = -r; dz <= r && done < 3; dz++) {
                        m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                        BlockState s = mc.level.getBlockState(m);
                        if (s.getBlock() instanceof CropBlock cb && cb.isMaxAge(s)) {
                            mc.gameMode.startDestroyBlock(m.immutable(), Direction.UP);
                            done++;
                        }
                    }
                }
            }
            if (done > 0) {
                p.swing(InteractionHand.MAIN_HAND);
                cd = 2;
            }
        }
    }

    public static class AutoFish extends Module {
        private int cd, since;

        AutoFish() {
            super("AutoFish", "Angelt automatisch: wirft aus, zieht bei Biss ein und wirft wieder aus (Angel in der Hand).", Category.BAUEN);
        }

        @Override
        protected void onEnable() {
            cd = 0;
            since = 0;
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.screen != null || mc.gameMode == null) return;
            if (cd > 0) {
                cd--;
                return;
            }
            if (!path(p.getMainHandItem()).equals("fishing_rod")) return;
            Entity bobber = null;
            for (Entity e : mc.level.getEntities((Entity) null, p.getBoundingBox().inflate(32), x -> true)) {
                if (BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("fishing_bobber")) {
                    bobber = e;
                    break;
                }
            }
            if (bobber == null) {
                mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
                p.swing(InteractionHand.MAIN_HAND);
                cd = 25;
                since = 0;
                return;
            }
            since++;
            if (since > 30 && bobber.getDeltaMovement().y < -0.09) {
                mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
                p.swing(InteractionHand.MAIN_HAND);
                cd = 20;
            }
        }
    }

    public static class ChestStealer extends Module {
        final Setting delay = add(Setting.number("Pause (Ticks)", 1, 0, 5, 1));
        private int cd;

        ChestStealer() {
            super("ChestStealer", "Leert Kisten und Fasser automatisch, sobald du sie oeffnest.", Category.BAUEN);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (!(mc.screen instanceof ContainerScreen cs) || mc.gameMode == null) return;
            if (cd > 0) {
                cd--;
                return;
            }
            ChestMenu menu = cs.getMenu();
            int n = menu.getRowCount() * 9;
            for (int i = 0; i < n; i++) {
                if (menu.getSlot(i).hasItem()) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, p);
                    cd = (int) delay.value;
                    return;
                }
            }
        }
    }

    // ================================================================== WELT

    public static class MLG extends Module {
        final Setting minFall = add(Setting.number("Ab Fallhoehe", 4, 3, 20, 1));
        private boolean placed;
        private int cd;

        MLG() {
            super("MLG", "Faengt Stuerze automatisch mit einem Wassereimer ab (Eimer in die Hotbar).", Category.WELT);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (cd > 0) cd--;
            if (mc.gameMode == null || mc.screen != null) return;
            if (!placed) {
                if (cd > 0 || p.onGround() || p.isInWater() || p.fallDistance < minFall.value) return;
                int s = exact(p, "water_bucket");
                if (s < 0) return;
                for (int k = 1; k <= 4; k++) {
                    BlockPos b = BlockPos.containing(p.getX(), p.getY() - k, p.getZ());
                    if (!mc.level.getBlockState(b).isAir()) {
                        Combo.use(mc, s, p.getYRot(), 90.0F);
                        placed = true;
                        cd = 6;
                        break;
                    }
                }
            } else if (cd == 0 && (p.onGround() || p.isInWater())) {
                int s = exact(p, "bucket");
                if (s >= 0) Combo.use(mc, s, p.getYRot(), 90.0F);
                placed = false;
                cd = 20;
            }
        }
    }

    public static class AntiVoid extends Module {
        AntiVoid() {
            super("AntiVoid", "Schleudert dich aus der Leere zurueck nach oben, bevor du stirbst.", Category.WELT);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p.getY() < -58 && p.getDeltaMovement().y < 0) {
                Vec3 m = p.getDeltaMovement();
                p.setDeltaMovement(m.x, 1.0, m.z);
                p.fallDistance = 0;
            }
        }
    }

    // ================================================================== SICHT

    public static class Zoom extends Module {
        final Setting fov = add(Setting.number("FOV", 30, 30, 70, 5));
        private int old = -1;

        Zoom() {
            super("Zoom", "Zoomt hinein (FOV kleiner). Minecraft erlaubt mindestens 30.", Category.SICHT);
        }

        @Override
        protected void onEnable() {
            Minecraft mc = Minecraft.getInstance();
            old = mc.options.fov().get();
        }

        @Override
        protected void onDisable() {
            Minecraft mc = Minecraft.getInstance();
            if (old > 0) mc.options.fov().set(old);
            old = -1;
        }

        @Override
        public void onTick(Minecraft mc) {
            if (old < 0) old = mc.options.fov().get();
            if (mc.options.fov().get() != (int) fov.value) mc.options.fov().set((int) fov.value);
        }
    }

    public static class Trail extends Module {
        final Setting len = add(Setting.number("Laenge", 200, 20, 600, 20));
        private final List<Vec3> pts = new ArrayList<>();

        Trail() {
            super("Trail", "Zieht eine bunte Spur hinter dir her.", Category.SICHT);
        }

        @Override
        protected void onDisable() { pts.clear(); }

        @Override
        public void onTick(Minecraft mc) {
            pts.add(mc.player.position());
            while (pts.size() > (int) len.value) pts.remove(0);
        }

        @Override
        public void onEsp(GuiGraphics g, Minecraft mc, Cam cam) {
            for (int i = 0; i + 1 < pts.size(); i++) {
                Vec3 a = pts.get(i), b = pts.get(i + 1);
                cam.line(g, a.x, a.y + 0.1, a.z, b.x, b.y + 0.1, b.z, Gfx.accent(i * 0.01));
            }
        }
    }

    public static class SpawnerEsp extends Module {
        final Setting range = add(Setting.choice("Radius", 256, RANGES));
        private final ChunkScan scan = new ChunkScan();

        SpawnerEsp() {
            super("Spawner-ESP", "Zeigt Monsterkaefige (Spawner) durch Waende - bis 10000 Bloecke (soweit geladen).", Category.SICHT);
        }

        @Override
        protected void onEnable() { scan.clear(); }

        @Override
        protected void onDisable() { scan.clear(); }

        @Override
        public void onTick(Minecraft mc) {
            scan.tick(mc, (int) range.value, 2, 200, s ->
                    BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().endsWith("spawner") ? 0xFFFF3DF2 : 0);
        }

        @Override
        public void onEsp(GuiGraphics g, Minecraft mc, Cam cam) {
            double[] r = new double[4];
            for (int[] h : scan.hits()) {
                AABB b = new AABB(h[0], h[1], h[2], h[0] + 1, h[1] + 1, h[2] + 1);
                if (cam.box(b, r)) Gfx.outline(g, (int) r[0], (int) r[1], (int) r[2], (int) r[3], h[3]);
            }
        }
    }

    public static class ItemEsp extends Module {
        final Setting range = add(Setting.choice("Reichweite", 64, RANGES));
        final Setting valuable = add(Setting.toggle("Nur wertvolle", false));

        ItemEsp() {
            super("Item-ESP", "Zeigt herumliegende Gegenstaende mit Namen - auch durch Waende.", Category.SICHT);
        }

        private static boolean isValuable(String n) {
            return n.contains("diamond") || n.contains("netherite") || n.contains("totem") || n.contains("elytra")
                    || n.contains("emerald") || n.contains("golden_apple") || n.contains("shulker") || n.contains("ancient_debris");
        }

        @Override
        public void onEsp(GuiGraphics g, Minecraft mc, Cam cam) {
            LocalPlayer p = mc.player;
            Font f = mc.font;
            double[] r = new double[4];
            double rr = Math.min(range.value, 300.0);
            AABB area = p.getBoundingBox().inflate(rr);
            for (Entity e : mc.level.getEntities((Entity) null, area, x -> true)) {
                if (!(e instanceof ItemEntity ie)) continue;
                ItemStack st = ie.getItem();
                if (valuable.on && !isValuable(path(st))) continue;
                Vec3 off = e.getPosition(cam.pt).subtract(e.position());
                if (!cam.box(e.getBoundingBox().move(off), r)) continue;
                Gfx.outline(g, (int) r[0], (int) r[1], (int) r[2], (int) r[3], 0xFFFFE066);
                String n = st.getHoverName().getString() + " x" + st.getCount();
                g.drawString(f, n, (int) ((r[0] + r[2]) / 2) - f.width(n) / 2, (int) r[1] - 10, 0xFFFFFFFF);
            }
        }
    }

    public static class TargetEsp extends Module {
        final Setting range = add(Setting.choice("Reichweite", 128, 32, 64, 128, 256, 300));

        TargetEsp() {
            super("Target-ESP", "Hebt den naechsten Spieler gross hervor (gefuellte Box, Linie, Leben, Abstand).", Category.SICHT);
        }

        @Override
        public void onEsp(GuiGraphics g, Minecraft mc, Cam cam) {
            LivingEntity t = Combo.nearest(mc, range.value, true, false);
            if (t == null) return;
            double[] r = new double[4];
            Vec3 off = t.getPosition(cam.pt).subtract(t.position());
            AABB b = t.getBoundingBox().move(off);
            int col = Gfx.accent(0.1);
            Vec3 c = b.getCenter();
            cam.tracer(g, c.x, c.y, c.z, col);
            if (!cam.box(b, r)) return;
            g.fill((int) r[0], (int) r[1], (int) r[2], (int) r[3], Gfx.alpha(col, 50));
            Gfx.outline(g, (int) r[0], (int) r[1], (int) r[2], (int) r[3], col);
            Font f = mc.font;
            String n = t.getName().getString() + "  " + (int) t.getHealth() + "hp  " + (int) mc.player.distanceTo(t) + "m";
            g.drawString(f, n, (int) ((r[0] + r[2]) / 2) - f.width(n) / 2, (int) r[1] - 11, 0xFFFFFFFF);
        }
    }

    // ================================================================== HUD

    public static class Radar extends Module {
        final Setting range = add(Setting.choice("Reichweite", 200, RANGES));

        Radar() {
            super("Radar", "Liste aller Spieler in der Naehe mit Abstand und Leben.", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            LocalPlayer p = mc.player;
            Font f = mc.font;
            List<Player> list = new ArrayList<>();
            for (Player e : mc.level.players()) {
                if (e != p && p.distanceTo(e) <= range.value) list.add(e);
            }
            list.sort(Comparator.comparingDouble(p::distanceTo));
            int y = 70;
            g.drawString(f, "Spieler: " + list.size(), 4, y, 0xFFFFFFFF);
            y += 11;
            for (int i = 0; i < list.size() && i < 8; i++) {
                Player e = list.get(i);
                double d = p.distanceTo(e);
                String s = e.getName().getString() + "  " + (int) d + "m  " + (int) e.getHealth() + "hp";
                g.drawString(f, s, 4, y, d < 20 ? 0xFFFF6A6A : (d < 60 ? 0xFFFFD166 : 0xFFB8B8C8));
                y += 10;
            }
        }
    }

    public static class TotemCount extends Module {
        TotemCount() {
            super("TotemCount", "Zeigt unten, wie viele Totems du noch hast.", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            LocalPlayer p = mc.player;
            int n = 0;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack st = p.getInventory().getItem(i);
                if (!st.isEmpty() && path(st).equals("totem_of_undying")) n += st.getCount();
            }
            String s = "Totems: " + n;
            int W = mc.getWindow().getGuiScaledWidth(), H = mc.getWindow().getGuiScaledHeight();
            g.drawString(mc.font, s, (W - mc.font.width(s)) / 2, H - 60, n == 0 ? 0xFFFF4D4D : 0xFFFFD166);
        }
    }

    public static class ArmorHud extends Module {
        ArmorHud() {
            super("ArmorHud", "Zeigt die Haltbarkeit deiner Ruestung links an.", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            LocalPlayer p = mc.player;
            Font f = mc.font;
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            String[] names = {"Helm", "Brust", "Hose", "Schuhe"};
            int H = mc.getWindow().getGuiScaledHeight();
            int y = H / 2 - 22;
            for (int i = 0; i < 4; i++) {
                ItemStack st = p.getItemBySlot(slots[i]);
                String s;
                int col = 0xFFB8B8C8;
                if (st.isEmpty()) {
                    s = names[i] + ": -";
                } else if (!st.isDamageableItem()) {
                    s = names[i] + ": ok";
                } else {
                    int pct = (int) (100.0 * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage());
                    s = names[i] + ": " + pct + "%";
                    col = pct < 20 ? 0xFFFF4D4D : (pct < 50 ? 0xFFFFD166 : 0xFF5CFF7A);
                }
                g.drawString(f, s, 4, y, col);
                y += 11;
            }
        }
    }

    public static class TargetHud extends Module {
        TargetHud() {
            super("TargetHud", "Zeigt oben Mitte Name und Leben des Gegners im Fadenkreuz (oder des naechsten Spielers).", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            LivingEntity t = null;
            if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) t = le;
            if (t == null) t = Combo.nearest(mc, 30.0, true, false);
            if (t == null) return;
            Font f = mc.font;
            int W = mc.getWindow().getGuiScaledWidth();
            String s = t.getName().getString() + "  " + (int) t.getHealth() + "/" + (int) t.getMaxHealth();
            int w = Math.max(100, f.width(s) + 12);
            int x = (W - w) / 2, y = 30;
            g.fill(x, y, x + w, y + 24, 0xB0000000);
            Gfx.outline(g, x, y, x + w, y + 24, Gfx.accent(0.2));
            g.drawString(f, s, x + 6, y + 4, 0xFFFFFFFF);
            float k = Math.max(0f, Math.min(1f, t.getHealth() / Math.max(1f, t.getMaxHealth())));
            g.fill(x + 6, y + 15, x + 6 + (int) ((w - 12) * k), y + 19, k > 0.5f ? 0xFF5CFF7A : (k > 0.25f ? 0xFFFFD166 : 0xFFFF4D4D));
        }
    }

    public static class Compass extends Module {
        Compass() {
            super("Kompass", "Zeigt oben Mitte deine Blickrichtung in Grad.", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            LocalPlayer p = mc.player;
            float yaw = p.getYRot() % 360.0F;
            if (yaw < 0) yaw += 360.0F;
            String s = p.getDirection().getName().toUpperCase() + "  " + (int) yaw + "°";
            int W = mc.getWindow().getGuiScaledWidth();
            g.drawString(mc.font, s, (W - mc.font.width(s)) / 2, 4, 0xFFFFFFFF);
        }
    }

    public static class Clock extends Module {
        Clock() {
            super("Uhr", "Zeigt die echte Uhrzeit oben Mitte.", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            java.time.LocalTime t = java.time.LocalTime.now();
            String s = String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond());
            int W = mc.getWindow().getGuiScaledWidth();
            g.drawString(mc.font, s, (W - mc.font.width(s)) / 2, 15, 0xFFC8C8D8);
        }
    }

    public static class LowHealthWarn extends Module {
        final Setting hp = add(Setting.number("Warnen ab HP", 6, 2, 12, 1));

        LowHealthWarn() {
            super("LowHealthWarn", "Roter Rand und Warnung bei wenig Leben.", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p.getHealth() > hp.value) return;
            int W = mc.getWindow().getGuiScaledWidth(), H = mc.getWindow().getGuiScaledHeight();
            int a = 60 + (int) (40 * Math.sin(System.currentTimeMillis() / 150.0));
            int c = (Math.max(10, a) << 24) | 0xFF0000;
            g.fill(0, 0, W, 6, c);
            g.fill(0, H - 6, W, H, c);
            g.fill(0, 0, 6, H, c);
            g.fill(W - 6, 0, W, H, c);
            String s = "! WENIG LEBEN: " + (int) p.getHealth() + " !";
            g.drawString(mc.font, s, (W - mc.font.width(s)) / 2, H / 2 + 20, 0xFFFF4D4D);
        }
    }
}
