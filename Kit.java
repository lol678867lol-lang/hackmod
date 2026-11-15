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
 * Auf Servern: als /give-Befehl, braucht Op-Rechte. /kit list zeigt alle Kits.
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

    private static final Map<String, List<String>> KITS = new LinkedHashMap<>();

    private static List<String> armor() {
        return new ArrayList<>(List.of(HELMET, CHEST, LEGS, BOOTS));
    }

    private static void kit(String name, List<String> base, String... extra) {
        List<String> l = new ArrayList<>(base);
        l.addAll(List.of(extra));
        KITS.put(name, l);
    }

    static {
        List<String> none = List.of();
        kit("crystal", armor(), SWORD, "end_crystal 64", "end_crystal 64", "end_crystal 64", "obsidian 64", "obsidian 64",
                "totem_of_undying", "totem_of_undying", "totem_of_undying", "enchanted_golden_apple 16", "ender_pearl 16", "shield");
        kit("anker", armor(), SWORD, "respawn_anchor 64", "glowstone 64", "glowstone 64", "totem_of_undying", "totem_of_undying",
                "enchanted_golden_apple 16", "ender_pearl 16", "shield");
        kit("mace", armor(), MACE, SWORD, "wind_charge 64", "wind_charge 64", ELYTRA, "firework_rocket 64", "totem_of_undying",
                "totem_of_undying", "enchanted_golden_apple 16", "ender_pearl 16");
        kit("speer", armor(), SPEAR, MACE, SWORD, "wind_charge 64", "ender_pearl 16", "totem_of_undying", "totem_of_undying",
                "enchanted_golden_apple 16", ELYTRA, "firework_rocket 64");
        kit("pvp", armor(), SWORD, AXE, BOW, "arrow 64", "shield", "totem_of_undying", "totem_of_undying", "totem_of_undying",
                "enchanted_golden_apple 32", "golden_apple 64", "ender_pearl 16", "cooked_beef 64", "water_bucket", "cobweb 64");
        kit("mining", none, PICK, SHOVEL, AXE, "water_bucket", "lava_bucket", "torch 64", "cooked_beef 64", "tnt 64", "ender_chest 4",
                "shulker_box 4");
        kit("bauen", none, "stone_bricks 64", "stone_bricks 64", "glass 64", "oak_planks 64", "smooth_stone 64", "quartz_block 64",
                "torch 64", "scaffolding 64", "ladder 64", "water_bucket", "obsidian 64", "iron_block 64", PICK, AXE, SHOVEL);
        kit("flug", none, ELYTRA, CHEST, "firework_rocket 64", "firework_rocket 64", "firework_rocket 64", "water_bucket");
        kit("essen", none, "enchanted_golden_apple 64", "golden_apple 64", "golden_carrot 64", "cooked_beef 64", "cooked_porkchop 64",
                "bread 64", "totem_of_undying", "totem_of_undying", "totem_of_undying");
        kit("totem", none, "totem_of_undying", "totem_of_undying", "totem_of_undying", "totem_of_undying", "totem_of_undying",
                "totem_of_undying", "totem_of_undying", "totem_of_undying", "totem_of_undying");
        kit("fernkampf", armor(), BOW, "arrow 64", "arrow 64", "crossbow[enchantments={quick_charge:3,multishot:1,unbreaking:3,mending:1}]",
                "firework_rocket 64", "spectral_arrow 64", "totem_of_undying", "enchanted_golden_apple 16");
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
            cd = 4;
            Extra5.exec(mc, QUEUE.poll());
        });
    }

    private static void give(java.util.function.Consumer<Component> out, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Extra5.online(mc)) return;
        String who = Extra5.me(mc);
        boolean local = mc.getSingleplayerServer() != null;
        int n = 0;
        for (String item : KITS.get(name)) {
            String cmd = "give " + who + " minecraft:" + item;
            if (local) Extra5.exec(mc, cmd);
            else QUEUE.add(cmd);
            n++;
        }
        out.accept(Component.literal("Kit '" + name + "': " + n + " Gegenstaende" + (local ? " erhalten." : " werden gesendet (braucht Op-Rechte).")));
    }
}
