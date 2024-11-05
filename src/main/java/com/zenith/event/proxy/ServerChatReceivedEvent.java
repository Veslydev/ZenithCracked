package com.zenith.event.proxy;

import com.zenith.feature.deathmessages.DeathMessageParseResult;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;

import java.util.Optional;

import static com.zenith.Shared.CONFIG;

public record ServerChatReceivedEvent(
    Optional<PlayerListEntry> sender,
    Component messageComponent,
    String message,
    Optional<PlayerListEntry> whisperTarget,
    Optional<DeathMessageParseResult> deathMessage
) {

    public boolean isDeathMessage() {
        return deathMessage.isPresent();
    }

    public boolean isPublicChat() {
        return !isWhisper() && !isDeathMessage() && sender.isPresent() && message.startsWith("<" + sender.get().getName() + ">");
    }

    // throws on error extracting content
    public String publicChatContent() {
        return message.substring(message.indexOf(">") + 2);
    }

    public boolean isWhisper() {
        return whisperTarget.isPresent();
    }

    public boolean isOutgoingWhisper() {
        return isWhisper() && sender.isPresent() && sender.get().getName().equalsIgnoreCase(CONFIG.authentication.username);
    }

    public boolean isIncomingWhisper() {
        return isWhisper() && sender.isPresent() && !sender.get().getName().equalsIgnoreCase(CONFIG.authentication.username);
    }
}
