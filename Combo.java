package de.hackmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Speer-/Mace-Kombos, OneShot (Fake-Fall), Windcharge-Boosts und gemeinsame Helfer. */
public final class Combo {
    private Combo() {}

    // ------------------------------------------------------------------ Helfer

    static int slot(LocalPlayer p, String endsWith) {
        return Modules.findHotbar(p, endsWith);
    }

    /** Dreht den Spieler zum Zielpunkt. */
    static void aim(LocalPlayer p, Vec3 target) {
        Vec3 eye = p.getEyePosition();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        p.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        p.setXRot(Mth.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -90.0F, 90.0F));
    }

    /** Benutzt einen Hotbar-Gegenstand mit bestimmter Blickrichtung und stellt danach alles zurueck. */
    static void use(Minecraft mc, int slot, float yaw, float pitch) {
        LocalPlayer p = mc.player;
        if (mc.gameMode == null || slot < 0) return;
        int prev = p.getInventory().getSelectedSlot();
        float oy = p.getYRot(), ox = p.getXRot();
        p.getInventory().setSelectedSlot(slot);
        p.setYRot(yaw);
        p.setXRot(pitch);
        mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        p.swing(InteractionHand.MAIN_HAND);
        p.setYRot(oy);
        p.setXRot(ox);
        p.getInventory().setSelectedSlot(prev);
    }

    /**
     * OneShot: taeuscht dem Server eine grosse Fallhoehe vor (hoch und wieder runter in kleinen Schritten),
     * damit der naechste Mace-Schlag den vollen Smash-Schaden macht. Klappt nur auf Servern ohne
     * Anti-Cheat gegen diesen Trick.
     */
    static void fakeFall(Minecraft mc, double height) {
        LocalPlayer p = mc.player;
        if (p == null || mc.getConnection() == null || height < 2) return;
        double x = p.getX(), y = p.getY(), z = p.getZ();
        int steps = (int) Math.ceil(height / 8.0);
        double h = height / steps;
        for (int i = 1; i <= steps; i++) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y + h * i, z, false, false));
        }
        for (int i = steps - 1; i >= 0; i--) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y + h * i, z, false, false));
        }
    }

    static boolean isTarget(LocalPlayer p, Entity e, boolean players, boolean mobs) {
        if (!(e instanceof LivingEntity le) || le == p || !le.isAlive() || le.isSpectator()) return false;
        boolean isPlayer = le instanceof Player;
        return isPlayer ? players : mobs;
    }

    static List<LivingEntity> targets(Minecraft mc, double r, boolean players, boolean mobs) {
        LocalPlayer p = mc.player;
        List<LivingEntity> out = new ArrayList<>();
        AABB area = p.getBoundingBox().inflate(r);
        for (Entity e : mc.level.getEntities((Entity) null, area, x -> true)) {
            if (isTarget(p, e, players, mobs) && p.distanceTo(e) <= r) out.add((LivingEntity) e);
        }
        return out;
    }

    static LivingEntity nearest(Minecraft mc, double r, boolean players, boolean mobs) {
        LocalPlayer p = mc.player;
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : targets(mc, r, players, mobs)) {
            double d = p.distanceTo(e);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    static String path(net.minecraft.world.item.ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    // ------------------------------------------------------------------ Speer-Combo

    /**
     * Voll-Automatik: Windcharge nach oben -> mit dem Speer auf den Gegner dashen -> Mace-Smash (OneShot)
     * -> optional Pearl-Catch -> wieder Dash ... bis der Gegner tot ist.
     */
    public static class SpearCombo extends Module {
        final Setting search = add(Setting.number("Suchradius", 24, 6, 64, 2));
        final Setting wind = add(Setting.toggle("Windcharge hoch", true));
        final Setting pearl = add(Setting.toggle("Pearl-Catch", false));
        final Setting oneshot = add(Setting.toggle("OneShot (Fake-Fall)", true));
        final Setting fall = add(Setting.number("Fake-Fall Hoehe", 30, 8, 80, 4));
        final Setting dash = add(Setting.number("Dash-Tempo", 1.6, 0.5, 4.0, 0.1));
        final Setting players = add(Setting.toggle("Spieler", true));
        final Setting mobs = add(Setting.toggle("Mobs", false));

        private static final int IDLE = 0, UP = 1, DASH = 2, SMASH = 3, AFTER = 4, PEARL = 5, WAIT_PEARL = 6;
        private int st = IDLE;
        private int t;
        private LivingEntity target;
        private int restore = -1;
        private boolean holding;

        SpearCombo() {
            super("Speer-Combo", "Windcharge hoch, Speer-Dash auf den Gegner, Mace-Smash, optional Pearl-Catch - wiederholt bis er tot ist. Speer, Mace, Windcharges (und Perle) in die Hotbar.", Category.COMBO);
        }

        private void release(Minecraft mc) {
            if (holding) mc.options.keyUse.setDown(false);
            holding = false;
        }

        private void finish(Minecraft mc) {
            release(mc);
            if (restore >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(restore);
            restore = -1;
            target = null;
            st = IDLE;
            t = 0;
        }

        @Override
        protected void onEnable() {
            st = IDLE;
            t = 0;
            target = null;
            restore = -1;
        }

        @Override
        protected void onDisable() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) finish(mc);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || mc.screen != null) return;
            t++;

            if (target != null && (!target.isAlive() || target.isRemoved() || p.distanceTo(target) > search.value + 8.0)) {
                target = null;
            }
            if (target == null) {
                release(mc);
                target = nearest(mc, search.value, players.on, mobs.on);
                if (target == null) {
                    finish(mc);
                    return;
                }
                if (restore < 0) restore = p.getInventory().getSelectedSlot();
                st = wind.on ? UP : DASH;
                t = 0;
            }

            int spear = slot(p, "_spear");
            int mace = slot(p, "mace");
            int windS = slot(p, "wind_charge");
            int pearlS = slot(p, "ender_pearl");
            if (spear < 0 && mace < 0) {
                finish(mc);
                return;
            }
            Vec3 c = target.getBoundingBox().getCenter();

            if (st == UP) {
                if (windS < 0) {
                    st = DASH;
                    t = 0;
                } else if (t == 1) {
                    use(mc, windS, p.getYRot(), 90.0F); // Windcharge unter die Fuesse -> hoch
                } else if (t >= 6 && (p.getDeltaMovement().y <= 0.15 || t >= 25)) {
                    st = DASH;
                    t = 0;
                }
            } else if (st == DASH) {
                aim(p, c);
                if (spear >= 0) {
                    p.getInventory().setSelectedSlot(spear);
                    mc.options.keyUse.setDown(true); // Speer aufladen
                    holding = true;
                } else {
                    p.getInventory().setSelectedSlot(mace);
                }
                Vec3 dir = c.subtract(p.position());
                double d = dir.length();
                if (d > 0.01) p.setDeltaMovement(dir.scale(dash.value / d));
                if (d <= 3.2 || t >= 30) {
                    if (d <= 6.0) {
                        mc.gameMode.attack(p, target);
                        p.swing(InteractionHand.MAIN_HAND);
                    }
                    release(mc);
                    st = SMASH;
                    t = 0;
                }
            } else if (st == SMASH) {
                aim(p, c);
                if (mace < 0) {
                    st = AFTER;
                    t = 0;
                } else {
                    p.getInventory().setSelectedSlot(mace);
                    if (t >= 2) {
                        if (p.distanceTo(target) <= 6.0) {
                            if (oneshot.on) fakeFall(mc, fall.value);
                            mc.gameMode.attack(p, target);
                            p.swing(InteractionHand.MAIN_HAND);
                        }
                        st = AFTER;
                        t = 0;
                    }
                }
            } else if (st == AFTER) {
                if (t >= 3) {
                    if (!target.isAlive()) {
                        target = null;
                    } else if (pearl.on && pearlS >= 0) {
                        st = PEARL;
                    } else if (p.getDeltaMovement().y > 0.6) {
                        st = DASH; // Mace-Windburst hat dich schon hochgeschossen
                    } else {
                        st = wind.on ? UP : DASH;
                    }
                    t = 0;
                }
            } else if (st == PEARL) {
                aim(p, c);
                use(mc, pearlS, p.getYRot(), p.getXRot());
                st = WAIT_PEARL;
                t = 0;
            } else if (st == WAIT_PEARL) {
                if (t >= 18) {
                    st = DASH;
                    t = 0;
                }
            }
        }
    }

    // ------------------------------------------------------------------ MaceKill

    /** Mace in der Hand + Ziel im Fadenkreuz: Fake-Fall und Schlag -> voller Smash-Schaden. */
    public static class MaceKill extends Module {
        final Setting fall = add(Setting.number("Fake-Fall Hoehe", 30, 8, 80, 4));
        final Setting players = add(Setting.toggle("Spieler", true));
        final Setting mobs = add(Setting.toggle("Mobs", true));

        MaceKill() {
            super("MaceKill", "OneShot: Mace in der Hand, Fadenkreuz auf ein Ziel - Fake-Fall und Smash-Schlag automatisch.", Category.COMBO);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || mc.screen != null) return;
            if (!path(p.getMainHandItem()).endsWith("mace")) return;
            if (p.getAttackStrengthScale(0.0F) < 1.0F) return;
            if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le && le.isAlive()) {
                boolean isPlayer = le instanceof Player;
                if (isPlayer && !players.on) return;
                if (!isPlayer && !mobs.on) return;
                fakeFall(mc, fall.value);
                mc.gameMode.attack(p, le);
                p.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    // ------------------------------------------------------------------ WindBoost

    /** Leertaste druecken -> Windcharge unter die Fuesse -> Boost nach oben. */
    public static class WindBoost extends Module {
        final Setting ground = add(Setting.toggle("Auch am Boden", true));
        private boolean wasDown;
        private int cd;

        WindBoost() {
            super("WindBoost", "Leertaste druecken: wirft eine Windcharge unter dich und schleudert dich hoch.", Category.COMBO);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (cd > 0) cd--;
            boolean down = mc.options.keyJump.isDown();
            if (down && !wasDown && cd == 0 && mc.screen == null && (ground.on || !p.onGround())) {
                int s = slot(p, "wind_charge");
                if (s >= 0) {
                    use(mc, s, p.getYRot(), 90.0F);
                    cd = 10;
                }
            }
            wasDown = down;
        }
    }
}
