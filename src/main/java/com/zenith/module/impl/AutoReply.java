package com.zenith.module.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.zenith.Proxy;
import com.zenith.event.proxy.ServerChatReceivedEvent;
import com.zenith.module.Module;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.zenith.Shared.*;
import static java.util.Objects.isNull;

public class AutoReply extends Module {
    private Cache<String, String> repliedPlayersCache = CacheBuilder.newBuilder()
            .expireAfterWrite(CONFIG.client.extra.autoReply.cooldownSeconds, TimeUnit.SECONDS)
            .build();
    private Instant lastReply = Instant.now();

    @Override
    public void subscribeEvents() {
        EVENT_BUS.subscribe(this, ServerChatReceivedEvent.class, this::handleServerChatReceivedEvent);
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.autoReply.enabled;
    }

    public void updateCooldown(final int newCooldown) {
        CONFIG.client.extra.autoReply.cooldownSeconds = newCooldown;
        Cache<String, String> newCache = CacheBuilder.newBuilder()
                .expireAfterWrite(newCooldown, TimeUnit.SECONDS)
                .build();
        newCache.putAll(this.repliedPlayersCache.asMap());
        this.repliedPlayersCache = newCache;
    }


    public void handleServerChatReceivedEvent(ServerChatReceivedEvent event) {
        if (Proxy.getInstance().hasActivePlayer()) return;
        try {
            if (event.isIncomingWhisper()) {
                if (!event.sender().get().getName().equalsIgnoreCase(CONFIG.authentication.username)
                        && Instant.now().minus(Duration.ofSeconds(1)).isAfter(lastReply)
                        && (DISCORD.lastRelaymessage.isEmpty()
                            || Instant.now().minus(Duration.ofSeconds(CONFIG.client.extra.autoReply.cooldownSeconds)).isAfter(DISCORD.lastRelaymessage.get()))) {
                    if (isNull(repliedPlayersCache.getIfPresent(event.sender().get().getName()))) {
                        repliedPlayersCache.put(event.sender().get().getName(), event.sender().get().getName());
                        // 236 char max ( 256 - 4(command) - 16(max name length) )
                        sendClientPacketAsync(new ServerboundChatPacket("/w " + event.sender().get().getName() + " " + CONFIG.client.extra.autoReply.message.substring(0, Math.min(CONFIG.client.extra.autoReply.message.length(), 236))));
                        this.lastReply = Instant.now();
                    }
                }
            }
        } catch (final Throwable e) {
            CLIENT_LOG.error("AutoReply Failed", e);
        }
    }
}
