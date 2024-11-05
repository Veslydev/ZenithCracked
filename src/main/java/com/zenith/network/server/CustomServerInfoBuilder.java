package com.zenith.network.server;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.zenith.Proxy;
import com.zenith.feature.queue.Queue;
import com.zenith.util.ComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.data.status.PlayerInfo;
import org.geysermc.mcprotocollib.protocol.data.status.ServerStatusInfo;
import org.geysermc.mcprotocollib.protocol.data.status.VersionInfo;
import org.geysermc.mcprotocollib.protocol.data.status.handler.ServerInfoBuilder;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.zenith.Shared.CONFIG;
import static com.zenith.Shared.SERVER_LOG;

public class CustomServerInfoBuilder implements ServerInfoBuilder {

    private final Cache<String, ServerStatusInfo> infoCache = CacheBuilder.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(CONFIG.server.ping.responseCacheSeconds))
        .maximumSize(10)
        .build();

    @Override
    public @Nullable ServerStatusInfo buildInfo(@Nullable Session session) {
        if (!CONFIG.server.ping.enabled) return null;
        if (CONFIG.server.ping.responseCaching) {
            var cacheKey = getSessionCacheKey(session);
            try {
                // building the server status here can be expensive
                // due to accessing player caches, active connections, etc
                // its possible someone could DoS a server pretty easily
                return infoCache.get(cacheKey, () -> createServerStatusInfo(session));
            } catch (ExecutionException e) {
                SERVER_LOG.debug("Failed to build server info for {}", cacheKey, e);
                return null;
            }
        } else return createServerStatusInfo(session);
    }

    private String getSessionCacheKey(@Nullable Session session) {
        if (session != null && CONFIG.server.viaversion.enabled) { // our response has a different protocol version for each connection (mirroring them)
            String ip = session.getRemoteAddress().toString();
            if (ip.contains("/")) ip = ip.substring(ip.indexOf("/") + 1);
            if (ip.contains(":")) ip = ip.substring(0, ip.indexOf(":"));
            return ip;
        }
        return "";
    }

    private ServerStatusInfo createServerStatusInfo(@Nullable Session session) {
        return new ServerStatusInfo(
            getMotd(),
            getPlayerInfo(),
            getVersionInfo(session),
            Proxy.getInstance().getServerIcon(),
            false
        );
    }

    private VersionInfo getVersionInfo(@Nullable Session session) {
        if (CONFIG.server.viaversion.enabled && session instanceof ServerSession)
            return new VersionInfo("ZenithProxy", ((ServerSession) session).getProtocolVersion());
        return new VersionInfo(MinecraftCodec.CODEC.getMinecraftVersion(), MinecraftCodec.CODEC.getProtocolVersion());
    }

    private PlayerInfo getPlayerInfo() {
        var onlinePlayerCount = CONFIG.server.ping.onlinePlayerCount
            ? Proxy.getInstance().getActiveConnections().size()
            : 0;
        if (CONFIG.server.ping.onlinePlayers) {
            return new PlayerInfo(
                CONFIG.server.ping.maxPlayers,
                onlinePlayerCount,
                List.of(getOnlinePlayerProfiles())
            );
        } else {
            return new PlayerInfo(
                CONFIG.server.ping.maxPlayers,
                onlinePlayerCount,
                Collections.emptyList()
            );
        }
    }

    public GameProfile[] getOnlinePlayerProfiles() {
        try {
            var connections = Proxy.getInstance().getActiveConnections().getArray();
            var result = new GameProfile[connections.length];
            for (int i = 0; i < connections.length; i++) {
                var connection = connections[i];
                result[i] = connection.profileCache.getProfile();
            }
            return result;
        } catch (final Throwable e) {
            return new GameProfile[0];
        }
    }

    private static final String motdMM = """
        <white>[<aqua><username><white>] <reset>- <motd_body>
        """;
    private static final String motdDisconnectedBody = "<red>Disconnected";
    private static final String motdConnectedBody = """
        <motd_status><reset>
        <aqua>Online for: <white>[<reset><online_time><white>]
        """;
    private static final String motdStatusInGame = "<green>In Game";
    private static final String motdStatusInQueue = """
        <in_queue> <white>[<aqua><queue_pos><white>] <queue_eta>
        """;
    private static final String motdInPrioQueue = "<red>In Prio Queue";
    private static final String motdInQueue = "<red>In Queue";
    private static final String motdQueuePosGeneric = "Queueing";
    private static final String motdQueueEta = "<reset>- <red>ETA <white>[<aqua><eta><white>]";

    public Component getMotd() {
        var prio = Proxy.getInstance().isPrio();
        var qPos = Proxy.getInstance().getQueuePosition();
        var qUndefined = qPos == Integer.MAX_VALUE;
        return ComponentSerializer.minimessage(
            motdMM,
            Placeholder.unparsed("username", CONFIG.authentication.username),
            Placeholder.parsed("motd_body", Proxy.getInstance().isConnected() ? motdConnectedBody : motdDisconnectedBody),
            Placeholder.parsed("motd_status", Proxy.getInstance().isInQueue() ? motdStatusInQueue : motdStatusInGame),
            Placeholder.unparsed("online_time", Proxy.getInstance().getOnlineTimeString()),
            Placeholder.parsed("in_queue", prio ? motdInPrioQueue : motdInQueue),
            Placeholder.unparsed("queue_pos", qUndefined ? motdQueuePosGeneric : qPos + " / " + (prio ? Queue.getQueueStatus().prio() : Queue.getQueueStatus().regular())),
            Placeholder.unparsed("queue_eta", qUndefined ? "" : motdQueueEta),
            Placeholder.parsed("eta", Queue.getQueueEta(qPos))
        );
    }
}
