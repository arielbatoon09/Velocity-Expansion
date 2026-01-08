package com.maestdev.velocityexpansion;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.clip.placeholderapi.expansion.Configurable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Taskable;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Velocity Expansion for PlaceholderAPI
 * Optimized for fast updates and Folia compatibility
 * 
 * @author MaestDev
 * @version 1.0.0
 */
public final class VelocityExpansion extends PlaceholderExpansion 
        implements PluginMessageListener, Configurable, Taskable {

    private static final String BUNGEECORD_CHANNEL = "BungeeCord";
    private static final String GET_SERVERS = "GetServers";
    private static final String PLAYER_COUNT = "PlayerCount";
    private static final String CONFIG_INTERVAL = "update_interval";

    // Thread-safe map for Folia
    private final Map<String, Integer> serverCounts = new ConcurrentHashMap<>();
    private final AtomicReference<Object> scheduledTask = new AtomicReference<>();

    // Folia detection
    private static final boolean IS_FOLIA = checkFolia();

    private static boolean checkFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public @NotNull String getIdentifier() {
        return "velocity";
    }

    @Override
    public @NotNull String getAuthor() {
        return "MaestDev";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public void start() {
        final Object prev = scheduledTask.get();
        if (prev == null) {
            try {
                Bukkit.getMessenger().registerOutgoingPluginChannel(getPlaceholderAPI(), BUNGEECORD_CHANNEL);
                Bukkit.getMessenger().registerIncomingPluginChannel(getPlaceholderAPI(), BUNGEECORD_CHANNEL, this);
                getPlaceholderAPI().getLogger().info("[Velocity] Registered plugin messaging channel");
            } catch (Exception e) {
                getPlaceholderAPI().getLogger().log(Level.SEVERE, "[Velocity] Failed to register channel", e);
                return;
            }
        }

        startUpdateTask();
    }

    @Override
    public void stop() {
        stopUpdateTask();
        
        try {
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(getPlaceholderAPI(), BUNGEECORD_CHANNEL);
            Bukkit.getMessenger().unregisterIncomingPluginChannel(getPlaceholderAPI(), BUNGEECORD_CHANNEL, this);
        } catch (Exception ignored) {
        }

        serverCounts.clear();
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        switch (params.toLowerCase()) {
            case "total":
            case "all":
                return String.valueOf(serverCounts.values().stream().mapToInt(Integer::intValue).sum());
            default:
                return String.valueOf(serverCounts.getOrDefault(params.toLowerCase(), 0));
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!BUNGEECORD_CHANNEL.equals(channel)) {
            return;
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String subchannel = in.readUTF();

            switch (subchannel) {
                case PLAYER_COUNT:
                    handlePlayerCount(in);
                    break;
                case GET_SERVERS:
                    handleServerList(in);
                    break;
            }
        } catch (IOException e) {
            getPlaceholderAPI().getLogger().log(Level.WARNING, 
                "[Velocity] Error reading plugin message", e);
        }
    }

    private void handlePlayerCount(DataInputStream in) throws IOException {
        if (in.available() == 0) return;
        
        String server = in.readUTF();
        if (in.available() == 0) {
            serverCounts.put(server.toLowerCase(), 0);
            return;
        }

        int count = in.readInt();
        serverCounts.put(server.toLowerCase(), count);
    }

    private void handleServerList(DataInputStream in) throws IOException {
        if (in.available() == 0) return;

        String serverList = in.readUTF();
        String[] servers = serverList.split(", ");

        for (String server : servers) {
            serverCounts.putIfAbsent(server.toLowerCase(), 0);
        }
    }

    private void startUpdateTask() {
        long interval = getLong(CONFIG_INTERVAL, 10); // 10 seconds default
        long intervalTicks = interval * 20L;
        long initialDelay = 20L;

        final Object task;
        if (IS_FOLIA) {
            task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                getPlaceholderAPI(),
                scheduledTask -> updateCounts(),
                initialDelay,
                intervalTicks
            );
        } else {
            task = Bukkit.getScheduler().runTaskTimer(
                getPlaceholderAPI(),
                this::updateCounts,
                initialDelay,
                intervalTicks
            );
        }

        final Object prev = scheduledTask.getAndSet(task);
        if (prev != null) {
            cancelTask(prev);
        }
    }

    private void stopUpdateTask() {
        final Object task = scheduledTask.getAndSet(null);
        if (task != null) {
            cancelTask(task);
        }
    }

    private void cancelTask(Object task) {
        try {
            if (task instanceof io.papermc.paper.threadedregions.scheduler.ScheduledTask) {
                ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) task).cancel();
            } else if (task instanceof org.bukkit.scheduler.BukkitTask) {
                ((org.bukkit.scheduler.BukkitTask) task).cancel();
            }
        } catch (Exception e) {
            getPlaceholderAPI().getLogger().log(Level.WARNING, 
                "[Velocity] Error cancelling task", e);
        }
    }

    private void updateCounts() {
        Player player = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (player == null) {
            return;
        }

        if (serverCounts.isEmpty()) {
            sendPluginMessage(player, GET_SERVERS, out -> {});
        } else {
            for (String server : serverCounts.keySet()) {
                sendPluginMessage(player, PLAYER_COUNT, out -> out.writeUTF(server));
            }
        }
    }

    private void sendPluginMessage(Player player, String subchannel, 
                                  java.util.function.Consumer<ByteArrayDataOutput> writer) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(subchannel);
        writer.accept(out);

        try {
            player.sendPluginMessage(getPlaceholderAPI(), BUNGEECORD_CHANNEL, out.toByteArray());
        } catch (Exception e) {
            getPlaceholderAPI().getLogger().log(Level.WARNING,
                "[Velocity] Failed to send plugin message", e);
        }
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Collections.singletonMap(CONFIG_INTERVAL, 10);
    }
}