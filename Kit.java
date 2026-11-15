package de.hackmod;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /kit <name> - gibt dir ein Set Items. Einzelspieler / LAN-Host: ueber den eingebauten Server.
 * Ersetzt dein ganzes Inventar (Ruestung, Zweithand, Hotbar, 27 Slots). Auf Servern als /item-Befehl, braucht Op-Rechte. /kit list zeigt alle Kits.
 */
public final class Kit {
    private Kit() {}

    private static final String HELMET = "netherite_helmet[enchantments={protection:4,unbreaking:3,mending:1,respiration:3,aqua_affinity:1}]";
    private static final String CHEST = "netherite_chestplate[enchantments={protection:4,unbreaking:3,mending:1}]";
    private static final String LEGS = "netherite_leggings[enchantments={protection:4,unbreaking:3,mending:1}]";
    private static final String BOOTS = "netherite_boots[enchantments={protection:4,unbreaking:3,mending:1,feather_falling:4,depth_strider:3}]";
    private static final String SWORD = "netherite_sword[enchantments={sharpness:5,unbreaking:3,mending:1,looting:3,fire_aspect:2,sweeping_edge:3}]";
    private static final String AXE = "netherite_axe[enchantments={sharpness:5,efficiency:5,unbreaking:3,mending:1}]";
    private static final String PICK = "netherite_pickaxe[enchantments={efficiency:5,fortune:3,unbreaking:3,mending:1}]";
    private static final String SHOVEL = "netherite_shovel[enchantments={efficiency:5,unbreaking:3,mending:1}]";
    private static final String BOW = "bow[enchantments={power:5,infinity:1,flame:1,unbreaking:3,mending:1}]";
    private static final String MACE = "mace[enchantments={density:5,wind_burst:3,unbreaking:3,mending:1}]";
    private static final String SPEAR = "netherite_spear[enchantments={unbreaking:3,mending:1}]";
    private static final String ELYTRA = "elytra[enchantments={unbreaking:3,mending:1}]";

    /** Ein Kit: Ruestung (Kopf, Brust, Beine, Fuesse), Zweithand, Hotbar (9) und Inventar (27, wird mit "bulk" aufgefuellt). */
    private record K(String[] armor, String off, String[] hot, String[] inv, String[] bulk) {}

    private static final Map<String, K> KITS = new LinkedHashMap<>();

    private static String[] a(String... x) {
        return x;
    }

    private static final String[] FULL = {HELMET, CHEST, LEGS, BOOTS};
    private static final String[] NOARMOR = {null, null, null, null};

    private static void kit(String name, String[] armor, String off, String[] hot, String[] inv, String[] bulk) {
        KITS.put(name, new K(armor, off, hot, inv, bulk));
    }

    static {
        kit("crystal", FULL, "totem_of_undying",
                a(SWORD, "end_crystal 64", "obsidian 64", "enchanted_golden_apple 64", "ender_pearl 16", "totem_of_undying", "shield", "cobweb 64", "water_bucket"),
                a(), a("end_crystal 64", "obsidian 64", "totem_of_undying", "enchanted_golden_apple 64", "ender_pearl 16", "end_crystal 64", "obsidian 64"));
        kit("anker", FULL, "totem_of_undying",
                a(SWORD, "respawn_anchor 64", "glowstone 64", "obsidian 64", "ender_pearl 16", "totem_of_undying", "shield", "enchanted_golden_apple 64", "water_bucket"),
                a(), a("respawn_anchor 64", "glowstone 64", "totem_of_undying", "enchanted_golden_apple 64", "obsidian 64", "ender_pearl 16"));
        kit("mace", FULL, "totem_of_undying",
                a(MACE, SWORD, "wind_charge 64", "ender_pearl 16", "enchanted_golden_apple 64", "totem_of_undying", "firework_rocket 64", "water_bucket", "cobweb 64"),
                a(ELYTRA), a("wind_charge 64", "totem_of_undying", "firework_rocket 64", "enchanted_golden_apple 64", "ender_pearl 16"));
        kit("speer", FULL, "totem_of_undying",
                a(SPEAR, MACE, SWORD, "wind_charge 64", "ender_pearl 16", "enchanted_golden_apple 64", "totem_of_undying", "firework_rocket 64", "water_bucket"),
                a(ELYTRA), a("wind_charge 64", "totem_of_undying", "ender_pearl 16", "enchanted_golden_apple 64", "firework_rocket 64"));
        kit("pvp", FULL, "shield",
                a(SWORD, AXE, BOW, "arrow 64", "enchanted_golden_apple 64", "ender_pearl 16", "totem_of_undying", "cobweb 64", "water_bucket"),
                a(), a("totem_of_undying", "enchanted_golden_apple 64", "golden_apple 64", "arrow 64", "ender_pearl 16", "cooked_beef 64", "cobweb 64"));
        kit("fernkampf", FULL, "totem_of_undying",
                a(BOW, "crossbow[enchantments={quick_charge:3,multishot:1,unbreaking:3,mending:1}]", "arrow 64", "spectral_arrow 64", "firework_rocket 64", "enchanted_golden_apple 64", "ender_pearl 16", "totem_of_undying", "water_bucket"),
                a(), a("arrow 64", "spectral_arrow 64", "firework_rocket 64", "totem_of_undying", "enchanted_golden_apple 64"));
        kit("mining", NOARMOR, "totem_of_undying",
                a(PICK, SHOVEL, AXE, "water_bucket", "lava_bucket", "torch 64", "cooked_beef 64", "tnt 64", "ender_chest"),
                a("shulker_box"), a("torch 64", "cooked_beef 64", "tnt 64", "cobblestone 64", "oak_planks 64", "water_bucket", "ladder 64"));
        kit("bauen", NOARMOR, "totem_of_undying",
                a(PICK, AXE, SHOVEL, "water_bucket", "torch 64", "scaffolding 64", "ladder 64", "glass 64", "cooked_beef 64"),
                a(), a("stone_bricks 64", "glass 64", "oak_planks 64", "smooth_stone 64", "quartz_block 64", "obsidian 64", "iron_block 64", "spruce_planks 64", "white_concrete 64"));
        kit("flug", a(null, ELYTRA, null, null), "totem_of_undying",
                a("firework_rocket 64", "firework_rocket 64", "firework_rocket 64", "water_bucket", "enchanted_golden_apple 64", "cooked_beef 64", "ender_pearl 16", "totem_of_undying", "wind_charge 64"),
                a(), a("firework_rocket 64"));
        kit("essen", NOARMOR, "totem_of_undying",
                a("enchanted_golden_apple 64", "golden_apple 64", "golden_carrot 64", "cooked_beef 64", "cooked_porkchop 64", "bread 64", "cooked_salmon 64", "baked_potato 64", "pumpkin_pie 64"),
                a(), a("enchanted_golden_apple 64", "golden_apple 64", "golden_carrot 64", "cooked_beef 64", "cooked_porkchop 64", "bread 64", "cooked_salmon 64"));
        kit("totem", NOARMOR, "totem_of_undying",
                a("totem_of_undying", "totem_of_undying", "totem_of_undying", "totem_of_undying", "totem_of_undying", "totem_of_undying", "totem_of_undying", "totem_of_undying", "totem_of_undying"),
                a(), a("totem_of_undying"));
    }

    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static int cd;

    static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = ClientCommandManager.literal("kit");
            for (String name : KITS.keySet()) {
                root.then(ClientCommandManager.literal(name).executes(ctx -> {
                    give(ctx.getSource()::sendFeedback, name);
                    return 1;
                }));
            }
            root.then(ClientCommandManager.literal("list").executes(ctx -> {
                ctx.getSource().sendFeedback(Component.literal("Kits: " + String.join(", ", KITS.keySet())));
                return 1;
            }));
            dispatcher.register(root);
        });

        // Auf Servern: ein /give pro paar Ticks, damit nichts als Spam gilt
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (QUEUE.isEmpty() || mc.player == null) return;
            if (cd-- > 0) return;
            cd = 2;
            Extra5.exec(mc, QUEUE.poll());
        });
    }

    private static void slot(List<String> out, String who, String slot, String item) {
        out.add("item replace entity " + who + " " + slot + " with minecraft:" + item);
    }

    private static void give(java.util.function.Consumer<Component> out, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Extra5.online(mc)) return;
        String who = Extra5.me(mc);
        K k = KITS.get(name);
        List<String> cmds = new ArrayList<>();
        String[] slots = {"armor.head", "armor.chest", "armor.legs", "armor.feet"};
        for (int i = 0; i < 4; i++) if (k.armor()[i] != null) slot(cmds, who, slots[i], k.armor()[i]);
        slot(cmds, who, "weapon.offhand", k.off());
        for (int i = 0; i < k.hot().length && i < 9; i++) slot(cmds, who, "hotbar." + i, k.hot()[i]);
        // Inventar (27 Slots): erst die feste Liste, den Rest mit "bulk" auffuellen
        for (int i = 0; i < 27; i++) {
            String item = i < k.inv().length ? k.inv()[i] : k.bulk()[(i - k.inv().length) % k.bulk().length];
            slot(cmds, who, "inventory." + i, item);
        }
        boolean local = mc.getSingleplayerServer() != null;
        for (String c : cmds) {
            if (local) Extra5.exec(mc, c);
            else QUEUE.add(c);
        }
        out.accept(Component.literal("Kit '" + name + "': Inventar komplett ersetzt" + (local ? "." : " - Befehle werden gesendet (braucht Op-Rechte).")));
    }
}
