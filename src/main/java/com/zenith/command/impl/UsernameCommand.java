package com.zenith.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.Command;
import com.zenith.command.CommandUsage;
import com.zenith.command.brigadier.CommandCategory;
import com.zenith.command.brigadier.CommandContext;
import com.zenith.discord.Embed;

import static com.zenith.Shared.CONFIG;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static java.util.Arrays.asList;

public class UsernameCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.args(
            "user",
            CommandCategory.MANAGE,
            "Change the username in the configuration.",
            asList(
                "<username>"
            )
        );
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("user").requires(Command::validateAccountOwner)
            .then(argument("username", wordWithChars()).executes(c -> {
                final String username = StringArgumentType.getString(c, "username");
                CONFIG.authentication.username = username;
                c.getSource().getEmbed()
                    .title("Username Updated!")
                    .description("New username: " + username);
                return OK;
            }))
            .then(literal("username").requires(Command::validateAccountOwner)
                .then(argument("username", wordWithChars()).executes(c -> {
                    final String username = StringArgumentType.getString(c, "username");
                    CONFIG.authentication.username = username;
                    c.getSource().getEmbed()
                        .title("Username Updated!")
                        .description("New username: " + username);
                    return OK;
                })))
            .then(literal("changeuser").requires(Command::validateAccountOwner)
                .then(argument("username", wordWithChars()).executes(c -> {
                    final String username = StringArgumentType.getString(c, "username");
                    CONFIG.authentication.username = username;
                    c.getSource().getEmbed()
                        .title("Username Updated!")
                        .description("New username: " + username);
                    return OK;
                })));
    }

    @Override
    public void postPopulate(final Embed builder) {
        builder
            .addField("Username", CONFIG.authentication.username, false)
            .primaryColor();
    }
}
