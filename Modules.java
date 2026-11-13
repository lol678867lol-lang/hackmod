package de.hackmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Alle Hacks + Verwaltung (Tick, HUD, ESP). */
public final class Modules {
    private Modules() {}

    public static final List<Module> ALL = new ArrayList<>();
    public static Fly FLY;

    public static void init() {
        FLY = new Fly();
        // Bewegung
        ALL.add(FLY);
        ALL.add(new Speed());
        ALL.add(new Step());
        ALL.add(new AutoSprint());
        ALL.add(new AirJump());
        ALL.add(new Glide());
        ALL.add(new Spider());
        ALL.add(new HighJump());
        ALL.add(new FastLadder());
        ALL.add(new AutoWalk());
        // Kampf
        ALL.add(new KillAura());
        ALL.add(new Aimbot());
        ALL.add(new TriggerBot());
        ALL.add(new SpearMace());
        ALL.add(new ShieldBreaker());
        ALL.add(new AutoTotem());
        ALL.add(new AutoEat());
        // Welt
        ALL.add(new WaterWalk());
        ALL.add(new NoFall());
        ALL.add(new AirPlace());
        ALL.add(new Scaffold());
        ALL.add(new MineArea());
        ALL.add(new AutoRespawn());
        ALL.add(new AntiAfk());
        ALL.add(new Info());
        ALL.add(new AutoTool());
        // Sicht
        ALL.add(new Fullbright());
        ALL.add(new XRay());
        ALL.add(new OreEsp());
        ALL.add(new StorageEsp());
        ALL.add(new EntityEsp());

        // ---- Erweiterung
        ALL.add(new Extra.BunnyHop());
        ALL.add(new Extra.AutoJump());
        ALL.add(new Extra.EdgeSneak());
        ALL.add(new Extra.Sneak());
        ALL.add(new Extra.Parkour());
        ALL.add(new Extra.Dolphin());
        ALL.add(new Extra.Twerk());
        ALL.add(new Extra.Jetpack());
        ALL.add(new Extra.Hover());
        ALL.add(new Extra.ElytraFly());
        ALL.add(new Extra.AutoRocket());
        ALL.add(new Extra.NoClip());
        ALL.add(new Extra.LongJump());
        ALL.add(new Extra.FastFall());
        ALL.add(new Extra.MultiAura());
        ALL.add(new Extra.CritAura());
        ALL.add(new Extra.AutoClicker());
        ALL.add(new Extra.AutoWeapon());
        ALL.add(new Extra.FastBow());
        ALL.add(new Extra.SpinBot());
        ALL.add(new Extra.Derp());
        ALL.add(new Extra.CrystalBreaker());
        ALL.add(new Extra.WebTrap());
        ALL.add(new Combo.SpearCombo());
        ALL.add(new Combo.MaceKill());
        ALL.add(new Combo.WindBoost());
        ALL.add(new Aura.CrystalAura());
        ALL.add(new Aura.AnchorAura());
        ALL.add(new Extra.AutoGapple());
        ALL.add(new Extra.AutoArmor());
        ALL.add(new Extra.AutoBlock());
        ALL.add(new Extra.Surround());
        ALL.add(new Extra.AutoXP());
        ALL.add(new Extra.FastPlace());
        ALL.add(new Extra.Nuker());
        ALL.add(new Extra.CropBreaker());
        ALL.add(new Extra.AutoFish());
        ALL.add(new Extra.ChestStealer());
        ALL.add(new Extra.MLG());
        ALL.add(new Extra.AntiVoid());
        ALL.add(new Extra.Zoom());
        ALL.add(new Extra.Trail());
        ALL.add(new Extra.SpawnerEsp());
        ALL.add(new Extra.ItemEsp());
        ALL.add(new Extra.TargetEsp());
        ALL.add(new Extra.Radar());
        ALL.add(new Extra.TotemCount());
        ALL.add(new Extra.ArmorHud());
        ALL.add(new Extra.TargetHud());
        ALL.add(new Extra.Compass());
        ALL.add(new Extra.Clock());
        ALL.add(new Extra.LowHealthWarn());
    }

    public static List<Module> byCategory(Module.Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : ALL) if (m.category == c) out.add(m);
        return out;
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        for (Module m : ALL) if (m.enabled) m.onTick(mc);
    }

    /** HUD: Wasserzeichen, Modul-Liste und ESP-Overlays. */
    public static void hud(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        Font f = mc.font;

        if (mc.options.getCameraType().isFirstPerson()) {
            Cam cam = new Cam(mc, pt);
            Area.draw(g, mc, cam);
            for (Module m : ALL) if (m.enabled) m.onEsp(g, mc, cam);
        }

        g.drawString(f, "HackMod", 4, 4, Gfx.accent(0));
        if (Area.isSet()) g.drawString(f, "Area " + Area.sizeText(), 4, 15, 0xFFFFFFFF);

        for (Module m : ALL) if (m.enabled) m.onHud(g, mc);

        List<Module> on = new ArrayList<>();
        for (Module m : ALL) if (m.enabled) on.add(m);
        on.sort(Comparator.comparingInt((Module m) -> -f.width(m.name)));
        int W = mc.getWindow().getGuiScaledWidth();
        int y = 4;
        for (Module m : on) {
            int w = f.width(m.name);
            int x = W - w - 8;
            g.fill(x - 2, y - 1, W, y + 10, 0x90000000);
            g.fill(W - 2, y - 1, W, y + 10, Gfx.accent(y * 0.02));
            g.drawString(f, m.name, x, y + 1, Gfx.accent(y * 0.02));
            y += 11;
        }
    }

    // ---------------------------------------------------------------- Bewegung

    public static class Fly extends Module {
        final Setting speed = add(Setting.number("Tempo", 1.0, 0.2, 5.0, 0.1));

        Fly() {
            super("Fly", "Fliegen mit Leertaste / Shift. (Server: allow-flight=true)", Category.FLUG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            double s = speed.value * 0.5;
            double[] d = Cam.moveDir(mc, p.getYRot());
            double vy = 0;
            if (mc.options.keyJump.isDown()) vy += s;
            if (mc.options.keyShift.isDown()) vy -= s;
            p.setDeltaMovement(d[0] * s, vy, d[1] * s);
            p.fallDistance = 0;
        }
    }

    public static class Speed extends Module {
        final Setting factor = add(Setting.number("Faktor", 1.5, 1.0, 6.0, 0.1));

        Speed() {
            super("Speed", "Schneller laufen (wird von Fly uebernommen, wenn Fly an ist).", Category.BEWEGUNG);
        }

        @Override
        public void onTick(Minecraft mc) {
            if (FLY != null && FLY.enabled) return;
            LocalPlayer p = mc.player;
            double[] d = Cam.moveDir(mc, p.getYRot());
            if (d[0] == 0 && d[1] == 0) return;
            double v = 0.22 * factor.value;
            Vec3 m = p.getDeltaMovement();
            p.setDeltaMovement(d[0] * v, m.y, d[1] * v);
        }
    }

    public static class Step extends Module {
        final Setting height = add(Setting.number("Hoehe", 2.0, 1.0, 5.0, 0.5));

        Step() {
            super("Step", "Ganze Bloecke hochlaufen ohne zu springen.", Category.BEWEGUNG);
        }

        private void apply(double v) {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null) return;
            AttributeInstance a = p.getAttribute(Attributes.STEP_HEIGHT);
            if (a != null) a.setBaseValue(v);
        }

        @Override
        public void onTick(Minecraft mc) { apply(height.value); }

        @Override
        protected void onDisable() { apply(0.6); }
    }

    public static class AutoSprint extends Module {
        AutoSprint() {
            super("AutoSprint", "Sprintet automatisch, sobald du vorwaerts laeufst.", Category.BEWEGUNG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.options.keyUp.isDown() && !p.horizontalCollision && !p.isUsingItem()
                    && p.getFoodData().getFoodLevel() > 6) {
                p.setSprinting(true);
            }
        }
    }

    // ---------------------------------------------------------------- Kampf

    public static class KillAura extends Module {
        final Setting range = add(Setting.number("Reichweite", 4.2, 2.0, 6.0, 0.1));
        final Setting players = add(Setting.toggle("Spieler", true));
        final Setting mobs = add(Setting.toggle("Mobs", true));

        KillAura() {
            super("KillAura", "Greift Ziele in Reichweite automatisch an (mit Cooldown).", Category.KAMPF);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || p.getAttackStrengthScale(0.0F) < 1.0F) return;
            Entity best = null;
            double bestD = Double.MAX_VALUE;
            AABB area = p.getBoundingBox().inflate(range.value);
            for (Entity e : mc.level.getEntities((Entity) null, area, x -> true)) {
                if (!(e instanceof LivingEntity le) || le == p || !le.isAlive() || le.isSpectator()) continue;
                boolean isPlayer = le instanceof Player;
                if (isPlayer && !players.on) continue;
                if (!isPlayer && !mobs.on) continue;
                double d = p.distanceTo(le);
                if (d <= range.value && d < bestD) {
                    bestD = d;
                    best = le;
                }
            }
            if (best != null) {
                mc.gameMode.attack(p, best);
                p.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    // ---------------------------------------------------------------- Sicht

    public static class Fullbright extends Module {
        Fullbright() {
            super("Fullbright", "Alles hell, auch in Hoehlen (Nachtsicht nur bei dir).", Category.SICHT);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            MobEffectInstance cur = p.getEffect(MobEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < 300) {
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1000, 0, false, false, false));
            }
        }

        @Override
        protected void onDisable() {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) p.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    public static class OreEsp extends Module {
        final Setting range = add(Setting.choice("Radius", 64, 16, 32, 64, 128, 256, 512, 1000, 2000, 5000, 10000));
        final Setting onlyRare = add(Setting.toggle("Nur seltene", false));
        final Setting maxBoxes = add(Setting.choice("Max. Boxen", 300, 100, 300, 600, 1000));
        final Setting tracers = add(Setting.toggle("Linien zu den naechsten", false));
        private final ChunkScan scan = new ChunkScan();

        OreEsp() {
            super("Erz-ESP", "Zeigt Erze als Boxen durch Waende. Radius bis 10000 - gesehen wird alles, was dein Spiel geladen hat (Sichtweite).", Category.SICHT);
        }

        @Override
        protected void onEnable() { scan.clear(); }

        @Override
        protected void onDisable() { scan.clear(); }

        private static int colorOf(BlockState s, boolean rare) {
            if (s.is(BlockTags.DIAMOND_ORES)) return 0xFF4DEEEA;
            if (s.is(BlockTags.EMERALD_ORES)) return 0xFF17DD62;
            if (s.is(Blocks.ANCIENT_DEBRIS)) return 0xFFB5533C;
            if (s.is(BlockTags.GOLD_ORES) || s.is(Blocks.NETHER_GOLD_ORE)) return 0xFFFCEE4B;
            if (rare) return 0;
            if (s.is(BlockTags.IRON_ORES)) return 0xFFD8AF93;
            if (s.is(BlockTags.COAL_ORES)) return 0xFFAAAAAA;
            if (s.is(BlockTags.REDSTONE_ORES)) return 0xFFFF2B2B;
            if (s.is(BlockTags.LAPIS_ORES)) return 0xFF2E5BFF;
            if (s.is(BlockTags.COPPER_ORES)) return 0xFFE07A3E;
            if (s.is(Blocks.NETHER_QUARTZ_ORE)) return 0xFFF0F0F0;
            return 0;
        }

        @Override
        public void onTick(Minecraft mc) {
            boolean rare = onlyRare.on;
            scan.tick(mc, (int) range.value, 3, (int) maxBoxes.value, s -> colorOf(s, rare));
        }

        @Override
        public void onEsp(GuiGraphics g, Minecraft mc, Cam cam) {
            double[] r = new double[4];
            int n = 0;
            for (int[] h : scan.hits()) {
                AABB b = new AABB(h[0], h[1], h[2], h[0] + 1, h[1] + 1, h[2] + 1);
                if (cam.box(b, r)) Gfx.outline(g, (int) r[0], (int) r[1], (int) r[2], (int) r[3], h[3]);
                if (tracers.on && n++ < 15) cam.tracer(g, h[0] + 0.5, h[1] + 0.5, h[2] + 0.5, h[3]);
            }
        }
    }

    public static class EntityEsp extends Module {
        final Setting range = add(Setting.choice("Reichweite", 64, 16, 32, 64, 128, 256, 512, 1000, 2000, 5000, 10000));
        final Setting players = add(Setting.toggle("Spieler", true));
        final Setting mobs = add(Setting.toggle("Mobs", true));
        final Setting items = add(Setting.toggle("Items", false));
        final Setting tracers = add(Setting.toggle("Tracer-Linien", false));

        EntityEsp() {
            super("Entity-ESP", "Boxen um Spieler und Mobs - auch durch Waende.", Category.SICHT);
        }

        @Override
        public void onEsp(GuiGraphics g, Minecraft mc, Cam cam) {
            LocalPlayer p = mc.player;
            Font f = mc.font;
            double[] r = new double[4];
            AABB area = p.getBoundingBox().inflate(Math.min(range.value, 300.0));
            for (Entity e : mc.level.getEntities((Entity) null, area, x -> true)) {
                if (e instanceof ItemEntity) {
                    if (!items.on) continue;
                    Vec3 io = e.getPosition(cam.pt).subtract(e.position());
                    if (cam.box(e.getBoundingBox().move(io), r)) {
                        Gfx.outline(g, (int) r[0], (int) r[1], (int) r[2], (int) r[3], 0xFFFFE066);
                    }
                    continue;
                }
                if (!(e instanceof LivingEntity) || e == p) continue;
                boolean isPlayer = e instanceof Player;
                if (isPlayer && !players.on) continue;
                if (!isPlayer && !mobs.on) continue;
                Vec3 off = e.getPosition(cam.pt).subtract(e.position());
                AABB b = e.getBoundingBox().move(off);
                if (!cam.box(b, r)) continue;
                int col = isPlayer ? 0xFFFF4D4D : (e instanceof Monster ? 0xFFFFA630 : 0xFF5CFF7A);
                Gfx.outline(g, (int) r[0], (int) r[1], (int) r[2], (int) r[3], col);
                if (tracers.on) {
                    Vec3 c = b.getCenter();
                    cam.tracer(g, c.x, c.y, c.z, col);
                }
                if (isPlayer) {
                    String n = e.getName().getString() + " " + (int) ((LivingEntity) e).getHealth() + "hp " + (int) p.distanceTo(e) + "m";
                    g.drawString(f, n, (int) ((r[0] + r[2]) / 2) - f.width(n) / 2, (int) r[1] - 10, 0xFFFFFFFF);
                }
            }
        }
    }

    // ================================================================ Mehr Bewegung

    public static class AirJump extends Module {
        final Setting power = add(Setting.number("Kraft", 0.42, 0.2, 1.0, 0.02));
        private boolean wasDown;

        AirJump() {
            super("AirJump", "Springen in der Luft - so oft du willst.", Category.FLUG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            boolean down = mc.options.keyJump.isDown();
            if (down && !wasDown && !p.onGround() && !p.isInWater() && !p.isInLava() && (FLY == null || !FLY.enabled)) {
                Vec3 m = p.getDeltaMovement();
                p.setDeltaMovement(m.x, power.value, m.z);
                p.fallDistance = 0;
            }
            wasDown = down;
        }
    }

    public static class Glide extends Module {
        final Setting fall = add(Setting.number("Fallspeed", 0.05, 0.01, 0.3, 0.01));

        Glide() {
            super("Glide", "Langsam herabsinken statt zu fallen.", Category.FLUG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            Vec3 m = p.getDeltaMovement();
            if (!p.onGround() && !p.isInWater() && !p.isInLava() && m.y < -fall.value) {
                p.setDeltaMovement(m.x, -fall.value, m.z);
                p.fallDistance = 0;
            }
        }
    }

    public static class Spider extends Module {
        final Setting speed = add(Setting.number("Tempo", 0.2, 0.1, 0.5, 0.05));

        Spider() {
            super("Spider", "An Waenden hochklettern.", Category.BEWEGUNG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p.horizontalCollision) {
                Vec3 m = p.getDeltaMovement();
                p.setDeltaMovement(m.x, speed.value, m.z);
            }
        }
    }

    // ================================================================ Kampf

    public static class AutoTotem extends Module {
        final Setting delay = add(Setting.number("Verzoeg. Ticks", 0, 0, 10, 1));
        final Setting hp = add(Setting.number("Nur unter HP", 20, 2, 20, 1));
        final Setting hotbarFirst = add(Setting.toggle("Hotbar zuerst", true));
        private int cd;

        AutoTotem() {
            super("AutoTotem", "Haelt immer einen Totem in der Zweithand - sofort nach dem Platzen, auch im Inventar-Fenster.", Category.SCHUTZ);
        }

        private static boolean isTotem(LocalPlayer p, int slot) {
            return p.inventoryMenu.getSlot(slot).getItem().is(Items.TOTEM_OF_UNDYING);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null) return;
            if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                cd = 0;
                return;
            }
            if (mc.screen != null && !(mc.screen instanceof InventoryScreen) && !(mc.screen instanceof ClickGui)) return;
            if (hp.value < 20 && p.getHealth() + p.getAbsorptionAmount() > hp.value) return;
            if (cd > 0) {
                cd--;
                return;
            }
            int found = -1;
            if (hotbarFirst.on) {
                for (int i = 36; i <= 44 && found < 0; i++) if (isTotem(p, i)) found = i;
            }
            for (int i = 9; i <= 44 && found < 0; i++) if (isTotem(p, i)) found = i;
            if (found < 0) return;
            mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, found, 40, ClickType.SWAP, p);
            cd = (int) delay.value;
        }
    }

    // ================================================================ Welt

    public static class WaterWalk extends Module {
        WaterWalk() {
            super("WaterWalk", "Auf Wasser und Lava laufen (Shift = untertauchen).", Category.WELT);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.options.keyShift.isDown()) return;
            Vec3 m = p.getDeltaMovement();
            boolean inFluid = !mc.level.getFluidState(p.blockPosition()).isEmpty();
            boolean fluidBelow = !mc.level.getFluidState(BlockPos.containing(p.getX(), p.getY() - 0.05, p.getZ())).isEmpty();
            if (inFluid) {
                p.setDeltaMovement(m.x, 0.15, m.z);
            } else if (fluidBelow && m.y <= 0) {
                p.setDeltaMovement(m.x, 0, m.z);
                p.fallDistance = 0;
            }
        }
    }

    public static class NoFall extends Module {
        NoFall() {
            super("NoFall", "Kein Fallschaden.", Category.WELT);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p.fallDistance > 2.5 && mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true, p.horizontalCollision));
            }
        }
    }

    /** Setzt einen Block an eine Position, auch mitten in der Luft. */
    static void placeBlockAt(Minecraft mc, BlockPos bp) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(bp), Direction.UP, bp, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    public static class AirPlace extends Module {
        final Setting range = add(Setting.number("Reichweite", 4.0, 2.0, 5.0, 0.5));
        private int cd;

        AirPlace() {
            super("AirPlace", "Bloecke mitten in die Luft setzen (Rechtsklick halten).", Category.BAUEN);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (cd > 0) cd--;
            if (mc.screen != null || mc.gameMode == null || cd > 0) return;
            if (!mc.options.keyUse.isDown()) return;
            if (!(p.getMainHandItem().getItem() instanceof BlockItem)) return;
            if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) return;
            Vec3 pos = p.getEyePosition().add(p.getViewVector(1.0F).scale(range.value));
            BlockPos bp = BlockPos.containing(pos);
            if (!mc.level.getBlockState(bp).canBeReplaced()) return;
            if (p.getBoundingBox().intersects(new AABB(bp))) return;
            placeBlockAt(mc, bp);
            cd = 4;
        }
    }

    public static class Scaffold extends Module {
        private int cd;

        Scaffold() {
            super("Scaffold", "Setzt automatisch Bloecke unter deine Fuesse (Block in die Hand nehmen).", Category.BAUEN);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (cd > 0) cd--;
            if (mc.screen != null || mc.gameMode == null || cd > 0) return;
            if (!(p.getMainHandItem().getItem() instanceof BlockItem)) return;
            BlockPos bp = BlockPos.containing(p.getX(), p.getY() - 0.5, p.getZ());
            if (!mc.level.getBlockState(bp).canBeReplaced()) return;
            placeBlockAt(mc, bp);
            cd = 2;
        }
    }

    /** Baut alle Bloecke in der Mine-Area ab (Ecken setzen mit Z und X). */
    public static class MineArea extends Module {
        final Setting reach = add(Setting.number("Reichweite", 4.0, 2.0, 5.0, 0.5));
        final Setting perTick = add(Setting.number("Bloecke/Tick", 3, 1, 8, 1));
        final Setting fromTop = add(Setting.toggle("Von oben", true));
        private BlockPos target;

        MineArea() {
            super("Mine-Area", "Baut die Area ab. Ecken: Z = Pos1, X = Pos2, V = loeschen.", Category.BAUEN);
        }

        @Override
        protected void onDisable() { target = null; }

        private BlockPos pick(Minecraft mc) {
            LocalPlayer p = mc.player;
            Vec3 eye = p.getEyePosition();
            int r = (int) Math.ceil(reach.value);
            int ex = (int) Math.floor(eye.x), ey = (int) Math.floor(eye.y), ez = (int) Math.floor(eye.z);
            int x0 = Math.max(Area.minX(), ex - r), x1 = Math.min(Area.maxX(), ex + r);
            int y0 = Math.max(Area.minY(), ey - r), y1 = Math.min(Area.maxY(), ey + r);
            int z0 = Math.max(Area.minZ(), ez - r), z1 = Math.min(Area.maxZ(), ez + r);
            double maxD2 = reach.value * reach.value;
            BlockPos best = null;
            double bestScore = Double.MAX_VALUE;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        m.set(x, y, z);
                        BlockState s = mc.level.getBlockState(m);
                        if (s.isAir() || s.getBlock() instanceof LiquidBlock) continue;
                        if (s.getDestroySpeed(mc.level, m) < 0) continue;
                        double d2 = eye.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
                        if (d2 > maxD2) continue;
                        double score = d2 + (fromTop.on ? (Area.maxY() - y) * 10000.0 : 0);
                        if (score < bestScore) {
                            bestScore = score;
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
            if (!Area.isSet() || mc.gameMode == null || mc.screen != null) return;
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
            if (target != null && (mc.level.getBlockState(target).isAir()
                    || eye.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > reach.value * reach.value)) {
                target = null;
            }
            if (target == null) target = pick(mc);
            if (target == null) return;
            Direction dir = Direction.getApproximateNearest(eye.x - (target.getX() + 0.5), eye.y - (target.getY() + 0.5), eye.z - (target.getZ() + 0.5));
            mc.gameMode.continueDestroyBlock(target, dir);
            p.swing(InteractionHand.MAIN_HAND);
        }
    }

    // ================================================================ Mehr Sicht

    /** X-Ray ueber ein mitgeliefertes Ressourcenpaket: Steine unsichtbar, Erze sichtbar. */
    public static class XRay extends Module {
        XRay() {
            super("X-Ray", "Nur Erze sichtbar, Gestein unsichtbar. Laedt Ressourcen neu (kurz warten).", Category.SICHT);
        }

        private static String packId(PackRepository repo) {
            for (String id : repo.getAvailableIds()) {
                if (id.contains("xray")) return id;
            }
            return null;
        }

        private void apply(boolean on) {
            Minecraft mc = Minecraft.getInstance();
            PackRepository repo = mc.getResourcePackRepository();
            String id = packId(repo);
            if (id == null) {
                enabled = false;
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal(
                            "X-Ray-Paket nicht gefunden - aktiviere 'HackMod X-Ray' unter Ressourcenpakete"), true);
                }
                return;
            }
            List<String> sel = new ArrayList<>(repo.getSelectedIds());
            if (on) {
                if (!sel.contains(id)) sel.add(id);
            } else {
                sel.remove(id);
            }
            repo.setSelected(sel);
            mc.reloadResourcePacks();
        }

        @Override
        protected void onEnable() { apply(true); }

        @Override
        protected void onDisable() { apply(false); }
    }

    // ================================================================ Noch mehr Bewegung

    public static class HighJump extends Module {
        final Setting power = add(Setting.number("Kraft", 0.8, 0.5, 2.0, 0.1));

        HighJump() {
            super("HighJump", "Viel hoeher springen.", Category.FLUG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.options.keyJump.isDown() && p.onGround()) {
                Vec3 m = p.getDeltaMovement();
                p.setDeltaMovement(m.x, power.value, m.z);
            }
        }
    }

    public static class FastLadder extends Module {
        FastLadder() {
            super("FastLadder", "Leitern und Ranken schnell hochklettern.", Category.BEWEGUNG);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (p.onClimbable() && p.horizontalCollision && !mc.options.keyShift.isDown()) {
                Vec3 m = p.getDeltaMovement();
                p.setDeltaMovement(m.x, 0.3, m.z);
            }
        }
    }

    public static class AutoWalk extends Module {
        AutoWalk() {
            super("AutoWalk", "Laeuft automatisch geradeaus.", Category.BEWEGUNG);
        }

        @Override
        public void onTick(Minecraft mc) { mc.options.keyUp.setDown(true); }

        @Override
        protected void onDisable() { Minecraft.getInstance().options.keyUp.setDown(false); }
    }

    // ================================================================ Noch mehr Kampf

    public static class Aimbot extends Module {
        final Setting range = add(Setting.number("Reichweite", 5.0, 2.0, 8.0, 0.5));
        final Setting smooth = add(Setting.number("Glaettung", 0.35, 0.05, 1.0, 0.05));
        final Setting players = add(Setting.toggle("Spieler", true));
        final Setting mobs = add(Setting.toggle("Mobs", true));

        Aimbot() {
            super("Aimbot", "Zielt automatisch auf das naechste Ziel.", Category.KAMPF);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.screen != null) return;
            Entity best = null;
            double bestD = Double.MAX_VALUE;
            AABB area = p.getBoundingBox().inflate(range.value);
            for (Entity e : mc.level.getEntities((Entity) null, area, x -> true)) {
                if (!(e instanceof LivingEntity le) || le == p || !le.isAlive() || le.isSpectator()) continue;
                boolean isPlayer = le instanceof Player;
                if (isPlayer && !players.on) continue;
                if (!isPlayer && !mobs.on) continue;
                double d = p.distanceTo(le);
                if (d <= range.value && d < bestD) {
                    bestD = d;
                    best = le;
                }
            }
            if (best == null) return;
            Vec3 eye = p.getEyePosition();
            Vec3 t = best.getBoundingBox().getCenter();
            double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
            float yawT = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitchT = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            float k = (float) smooth.value;
            p.setYRot(p.getYRot() + Mth.wrapDegrees(yawT - p.getYRot()) * k);
            p.setXRot(Mth.clamp(p.getXRot() + (pitchT - p.getXRot()) * k, -90.0F, 90.0F));
        }
    }

    public static class TriggerBot extends Module {
        final Setting players = add(Setting.toggle("Spieler", true));
        final Setting mobs = add(Setting.toggle("Mobs", true));

        TriggerBot() {
            super("TriggerBot", "Schlaegt automatisch zu, sobald dein Fadenkreuz auf einem Ziel ist.", Category.KAMPF);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || mc.screen != null || p.getAttackStrengthScale(0.0F) < 1.0F) return;
            if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le && le.isAlive()) {
                boolean isPlayer = le instanceof Player;
                if (isPlayer && !players.on) return;
                if (!isPlayer && !mobs.on) return;
                mc.gameMode.attack(p, le);
                p.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    // ================================================================ Noch mehr Welt

    public static class AutoRespawn extends Module {
        AutoRespawn() {
            super("AutoRespawn", "Klickt nach dem Tod sofort auf Respawn.", Category.WELT);
        }

        @Override
        public void onTick(Minecraft mc) {
            if (mc.screen instanceof DeathScreen) mc.player.respawn();
        }
    }

    public static class AntiAfk extends Module {
        private int t;

        AntiAfk() {
            super("AntiAFK", "Bewegt sich ab und zu leicht, damit du nicht als AFK giltst.", Category.WELT);
        }

        @Override
        public void onTick(Minecraft mc) {
            if (++t >= 400) {
                t = 0;
                LocalPlayer p = mc.player;
                p.setYRot(p.getYRot() + 7.0F);
                p.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    public static class Info extends Module {
        Info() {
            super("Info", "Zeigt Koordinaten, Blickrichtung und Tempo oben links.", Category.HUD);
        }

        @Override
        public void onHud(GuiGraphics g, Minecraft mc) {
            LocalPlayer p = mc.player;
            Font f = mc.font;
            Vec3 m = p.getDeltaMovement();
            double speed = Math.sqrt(m.x * m.x + m.z * m.z) * 20.0;
            int y = Area.isSet() ? 27 : 16;
            g.drawString(f, "XYZ  " + (int) Math.floor(p.getX()) + "  " + (int) Math.floor(p.getY()) + "  " + (int) Math.floor(p.getZ()), 4, y, 0xFFFFFFFF);
            g.drawString(f, "Blick  " + p.getDirection().getName(), 4, y + 11, 0xFFC8C8D8);
            g.drawString(f, "Tempo  " + String.format("%.1f", speed) + " b/s", 4, y + 22, 0xFFC8C8D8);
        }
    }

    // ================================================================ Noch mehr Sicht

    public static class StorageEsp extends Module {
        final Setting range = add(Setting.choice("Radius", 64, 16, 32, 64, 128, 256, 512, 1000, 2000, 5000, 10000));
        private final ChunkScan scan = new ChunkScan();

        StorageEsp() {
            super("Kisten-ESP", "Zeigt Kisten, Fasser, Enderkisten und Shulker durch Waende - Radius bis 10000 (soweit geladen).", Category.SICHT);
        }

        @Override
        protected void onEnable() { scan.clear(); }

        @Override
        protected void onDisable() { scan.clear(); }

        private static int colorOf(BlockState s) {
            if (s.is(Blocks.CHEST)) return 0xFFFFB84D;
            if (s.is(Blocks.TRAPPED_CHEST)) return 0xFFFF5555;
            if (s.is(Blocks.ENDER_CHEST)) return 0xFFB050FF;
            if (s.is(Blocks.BARREL)) return 0xFFC08050;
            if (s.getBlock() instanceof ShulkerBoxBlock) return 0xFFFF77DD;
            return 0;
        }

        @Override
        public void onTick(Minecraft mc) {
            scan.tick(mc, (int) range.value, 3, 300, StorageEsp::colorOf);
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

    // ================================================================ Speer / Streitkolben

    /** Sucht in der Hotbar (0-8) einen Gegenstand, dessen Name auf suffix endet (z.B. "_spear" oder "mace"). */
    static int findHotbar(LocalPlayer p, String suffix) {
        for (int i = 0; i < 9; i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.isEmpty()) continue;
            if (BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().endsWith(suffix)) return i;
        }
        return -1;
    }

    /** Speer-Hack + Speer->Streitkolben-Swap: wechselt automatisch die Waffe und schlaegt zu. */
    public static class SpearMace extends Module {
        final Setting range = add(Setting.number("Reichweite", 4.5, 2.0, 6.0, 0.5));
        final Setting swap = add(Setting.toggle("Speer+Mace Swap", true));
        final Setting maceFall = add(Setting.toggle("Mace beim Fallen", true));
        final Setting back = add(Setting.toggle("Zurueckwechseln", true));
        final Setting charge = add(Setting.toggle("Charge (Rechtsklick)", false));
        final Setting oneshot = add(Setting.toggle("OneShot (Fake-Fall)", true));
        final Setting fallH = add(Setting.number("Fake-Fall Hoehe", 30, 8, 80, 4));
        final Setting players = add(Setting.toggle("Spieler", true));
        final Setting mobs = add(Setting.toggle("Mobs", true));
        private int restore = -1;
        private boolean charging;

        SpearMace() {
            super("Speer/Mace", "Wechselt automatisch Speer und Streitkolben (Mace) und greift Ziele an. Waffen in die Hotbar legen.", Category.COMBO);
        }

        @Override
        protected void onDisable() {
            Minecraft mc = Minecraft.getInstance();
            if (charging) mc.options.keyUse.setDown(false);
            charging = false;
            if (restore >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(restore);
            restore = -1;
        }

        private Entity target(Minecraft mc, double r) {
            LocalPlayer p = mc.player;
            Entity best = null;
            double bestD = Double.MAX_VALUE;
            AABB area = p.getBoundingBox().inflate(r);
            for (Entity e : mc.level.getEntities((Entity) null, area, x -> true)) {
                if (!(e instanceof LivingEntity le) || le == p || !le.isAlive() || le.isSpectator()) continue;
                boolean isPlayer = le instanceof Player;
                if (isPlayer && !players.on) continue;
                if (!isPlayer && !mobs.on) continue;
                double d = p.distanceTo(le);
                if (d <= r && d < bestD) {
                    bestD = d;
                    best = le;
                }
            }
            return best;
        }

        private void hit(Minecraft mc, int slot, Entity t) {
            LocalPlayer p = mc.player;
            p.getInventory().setSelectedSlot(slot);
            if (oneshot.on && slot == findHotbar(p, "mace")) Combo.fakeFall(mc, fallH.value);
            mc.gameMode.attack(p, t);
            p.swing(InteractionHand.MAIN_HAND);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || mc.screen != null) return;
            int spear = findHotbar(p, "_spear");
            int mace = findHotbar(p, "mace");

            // Charge: Rechtsklick halten, solange ein Ziel in der Naehe ist und ein Speer in der Hand liegt
            if (charge.on && spear >= 0) {
                Entity far = target(mc, range.value + 6.0);
                boolean want = far != null && p.getInventory().getSelectedSlot() == spear;
                if (want != charging) {
                    mc.options.keyUse.setDown(want);
                    charging = want;
                }
            } else if (charging) {
                mc.options.keyUse.setDown(false);
                charging = false;
            }

            Entity t = target(mc, range.value);
            if (t == null) {
                if (restore >= 0) {
                    p.getInventory().setSelectedSlot(restore);
                    restore = -1;
                }
                return;
            }
            if (spear < 0 && mace < 0) return;
            if (p.getAttackStrengthScale(0.0F) < 1.0F) return;
            if (restore < 0) restore = p.getInventory().getSelectedSlot();

            boolean falling = p.fallDistance > 1.5 && !p.onGround();
            if (maceFall.on && falling && mace >= 0) {
                hit(mc, mace, t); // Mace-Smash beim Fallen
            } else if (spear >= 0) {
                hit(mc, spear, t);
                if (swap.on && mace >= 0) hit(mc, mace, t); // Speer, dann sofort Mace
            } else {
                hit(mc, mace, t);
            }
            if (back.on) {
                p.getInventory().setSelectedSlot(restore);
                restore = -1;
            }
        }
    }

    // ================================================================ Shield-Break, AutoEat, AutoTool

    /** Hat ein Gegner den Schild oben, wechselt das Modul auf eine Axt und schlaegt zu (Schild wird deaktiviert). */
    public static class ShieldBreaker extends Module {
        final Setting range = add(Setting.number("Reichweite", 4.0, 2.0, 6.0, 0.5));
        final Setting back = add(Setting.toggle("Zurueckwechseln", true));

        ShieldBreaker() {
            super("ShieldBreaker", "Bricht Schilde: Axt aus der Hotbar nehmen und blockende Gegner schlagen.", Category.KAMPF);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.gameMode == null || mc.screen != null) return;
            if (p.getAttackStrengthScale(0.0F) < 0.95F) return;
            int axe = findHotbar(p, "_axe");
            if (axe < 0) return;
            Entity best = null;
            double bestD = Double.MAX_VALUE;
            AABB area = p.getBoundingBox().inflate(range.value);
            for (Entity e : mc.level.getEntities((Entity) null, area, x -> true)) {
                if (!(e instanceof LivingEntity le) || le == p || !le.isAlive() || !le.isBlocking()) continue;
                double d = p.distanceTo(le);
                if (d <= range.value && d < bestD) {
                    bestD = d;
                    best = le;
                }
            }
            if (best == null) return;
            int prev = p.getInventory().getSelectedSlot();
            p.getInventory().setSelectedSlot(axe);
            mc.gameMode.attack(p, best);
            p.swing(InteractionHand.MAIN_HAND);
            if (back.on) p.getInventory().setSelectedSlot(prev);
        }
    }

    /** Isst automatisch, wenn der Hunger unter den Wert faellt (Essen in die Hotbar legen). */
    public static class AutoEat extends Module {
        final Setting hunger = add(Setting.number("Hunger ab", 14, 4, 19, 1));
        private int restore = -1;
        private boolean eating;

        AutoEat() {
            super("AutoEat", "Isst automatisch Essen aus der Hotbar, wenn du hungrig wirst.", Category.SCHUTZ);
        }

        private static boolean badFood(String path) {
            return path.equals("rotten_flesh") || path.equals("spider_eye") || path.equals("pufferfish")
                    || path.equals("poisonous_potato") || path.equals("chorus_fruit") || path.contains("golden_apple");
        }

        private static int findFood(LocalPlayer p) {
            for (int i = 0; i < 9; i++) {
                ItemStack st = p.getInventory().getItem(i);
                if (st.isEmpty() || !st.has(DataComponents.FOOD)) continue;
                if (!badFood(BuiltInRegistries.ITEM.getKey(st.getItem()).getPath())) return i;
            }
            return -1;
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
            boolean hungry = !p.isCreative() && p.getFoodData().getFoodLevel() <= hunger.value;
            int slot = hungry ? findFood(p) : -1;
            if (slot < 0) {
                if (eating) stop(mc);
                return;
            }
            if (!eating) {
                restore = p.getInventory().getSelectedSlot();
                eating = true;
            }
            p.getInventory().setSelectedSlot(slot);
            mc.options.keyUse.setDown(true);
        }
    }

    /** Waehlt beim Abbauen automatisch das schnellste Werkzeug aus der Hotbar. */
    public static class AutoTool extends Module {
        AutoTool() {
            super("AutoTool", "Nimmt beim Abbauen automatisch das beste Werkzeug aus der Hotbar.", Category.BAUEN);
        }

        @Override
        public void onTick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (mc.screen != null || !mc.options.keyAttack.isDown()) return;
            if (!(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) return;
            BlockState s = mc.level.getBlockState(bhr.getBlockPos());
            int best = -1;
            float bestSpeed = p.getInventory().getItem(p.getInventory().getSelectedSlot()).getDestroySpeed(s);
            for (int i = 0; i < 9; i++) {
                float sp = p.getInventory().getItem(i).getDestroySpeed(s);
                if (sp > bestSpeed) {
                    bestSpeed = sp;
                    best = i;
                }
            }
            if (best >= 0) p.getInventory().setSelectedSlot(best);
        }
    }
}
