package de.hackmod;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * /bot combat  - spawnt einen Test-Gegner (gepanzerter Husk) mit Totem, der nie ohne Totem bleibt.
 * /bot kill    - entfernt alle Test-Gegner.
 * Der Totem-Nachschub laeuft ueber den eingebauten Server und geht deshalb nur im Einzelspieler / als LAN-Host.
 */
public final class Bot {
    private Bot() {}

    private static final String TAG = "hackbot";

    private static final String SUMMON = "execute as @a[limit=1] at @s run summon minecraft:husk ~3 ~ ~ "
            + "{Tags:[\"" + TAG + "\"],PersistenceRequired:1b,CanPickUpLoot:0b,CustomName:\"Bot\","
            + "equipment:{"
            + "head:{id:\"minecraft:netherite_helmet\",count:1},"
            + "chest:{id:\"minecraft:netherite_chestplate\",count:1},"
            + "legs:{id:\"minecraft:netherite_leggings\",count:1},"
            + "feet:{id:\"minecraft:netherite_boots\",count:1},"
            + "mainhand:{id:\"minecraft:netherite_sword\",count:1},"
            + "offhand:{id:\"minecraft:totem_of_undying\",count:1}}}";

    private static final String REFILL = "execute as @e[tag=" + TAG + "] unless items entity @s weapon.offhand "
            + "minecraft:totem_of_undying run item replace entity @s weapon.offhand with minecraft:totem_of_undying";

    private static final String KILL = "kill @e[tag=" + TAG + "]";

    private static boolean active;

    private static void run(MinecraftServer server, String cmd, boolean quiet) {
        var src = server.createCommandSourceStack();
        if (quiet) src = src.withSuppressedOutput();
        server.getCommands().performPrefixedCommand(src, cmd);
    }

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("bot")
                        .then(ClientCommandManager.literal("combat").executes(ctx -> {
                            spawn(ctx.getSource());
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("kill").executes(ctx -> {
                            kill(ctx.getSource());
                            return 1;
                        }))));

        // Jeden Tick: Totem nachlegen, solange es Bots gibt (nur Einzelspieler / LAN-Host)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (active) run(server, REFILL, true);
        });
    }

    private static void spawn(FabricClientCommandSource src) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                run(server, SUMMON, false);
                active = true;
            });
            src.sendFeedback(Component.literal("Bot gespawnt: Husk mit Netherite-Ruestung und Totem - bekommt sofort einen neuen Totem. Entfernen: /bot kill"));
        } else if (mc.getConnection() != null) {
            mc.getConnection().sendCommand(SUMMON.substring(SUMMON.indexOf("run ") + 4));
            src.sendFeedback(Component.literal("Bot per /summon gesendet (braucht Op-Rechte). Unendlich Totems gibt es nur im Einzelspieler oder als LAN-Host."));
        }
    }

    private static void kill(FabricClientCommandSource src) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        active = false;
        if (server != null) {
            server.execute(() -> run(server, KILL, false));
            src.sendFeedback(Component.literal("Alle Bots entfernt."));
        } else if (mc.getConnection() != null) {
            mc.getConnection().sendCommand(KILL);
            src.sendFeedback(Component.literal("Kill-Befehl gesendet (braucht Op-Rechte)."));
        }
    }
}
