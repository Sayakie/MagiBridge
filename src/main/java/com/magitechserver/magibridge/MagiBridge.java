package com.magitechserver.magibridge;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.magitechserver.magibridge.bridge.nucleus.NucleusBridgeImpl;
import com.magitechserver.magibridge.config.ConfigManager;
import com.magitechserver.magibridge.config.FormatType;
import com.magitechserver.magibridge.config.categories.ConfigCategory;
import com.magitechserver.magibridge.discord.DiscordMessageBuilder;
import com.magitechserver.magibridge.discord.MessageListener;
import com.magitechserver.magibridge.listeners.UChatListeners;
import com.magitechserver.magibridge.listeners.VanillaListeners;
import com.magitechserver.magibridge.task.NicknameUpdaterTask;
import com.magitechserver.magibridge.task.TPSCollectorTask;
import com.magitechserver.magibridge.task.TopicUpdaterTask;
import com.magitechserver.magibridge.util.ConsoleHandler;
import com.magitechserver.magibridge.util.Utils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameStartingServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.Tristate;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Frani on 22/06/2017.
 */

@Plugin(id = "magibridge",
        name = "MagiBridge",
        description = "A utility Discord <-> Minecraft chat relay plugin",
        authors = {"Eufranio", "Sayakie"},
        dependencies = {
                @Dependency(id = "ultimatechat", optional = true),
                @Dependency(id = "nucleus", optional = true),
                @Dependency(id = "boop", version = "[1.5.0,)", optional = true)})

public class MagiBridge {

    static MagiBridge instance = null;

    @Inject
    @ConfigDir(sharedRoot = false)
    public File configDir;

    @Inject
    private Logger logger;

    JDA jda;
    Task nickUpdaterTask;
    Task topicUpdaterTask;
    Task tpsCollectorTask;
    ConsoleHandler consoleHandler;
    ConfigCategory config;
    Executor executor;
    boolean useVanillaChat = false;

    @Listener
    public void onStartingServer(GameStartingServerEvent event) {
        instance = this;
        this.executor = Sponge.getScheduler().createAsyncExecutor(this);
        this.init().thenRun(() -> {
            String startChannel = config.CHANNELS.START_MESSAGES_CHANNEL;
            if (startChannel.isEmpty())
                startChannel = config.CHANNELS.MAIN_CHANNEL;

            DiscordMessageBuilder.forChannel(startChannel)
                    .format(FormatType.SERVER_STARTING)
                    .useWebhook(false)
                    .send();

            CommandSpec reloadCommand = CommandSpec.builder()
                .description(Text.of("Reloads MagiBridge."))
                .permission("magibridge.admin.reload")
                .executor((src, args) -> {
                    this.reload();

                    src.sendMessage(Text.of(TextColors.GREEN, "Magibridge is reloaded successfully!"));
                    return CommandResult.success();
                })
                .build();

            CommandSpec cs = CommandSpec.builder()
                    .description(Text.of("Broadcasts a message to the specified Discord channel name"))
                    .permission("magibridge.admin.broadcast")
                    .arguments(
                            GenericArguments.string(Text.of("channel")),
                            GenericArguments.remainingJoinedStrings(Text.of("message"))
                    )
                    .executor((src, args) -> {
                        String channel = args.requireOne("channel");
                        String message = args.requireOne("message");

                        List<TextChannel> channels = jda.getTextChannelsByName(channel, true);
                        if (channels.isEmpty()) {
                            channels = Lists.newArrayList(jda.getTextChannelById(channel));
                            if (channels.isEmpty())
                                throw new CommandException(Text.of("Could not send message! Are you sure a channel with this name/id exists?"));
                        }

                        channels.forEach(c -> DiscordMessageBuilder.forChannel(c.getId())
                                .format(FormatType.of(() -> message))
                                .useWebhook(false)
                                .send()
                        );
                        src.sendMessage(Text.of(TextColors.GREEN, "Message sent!"));

                        return CommandResult.success();
                    })
                    .build();

            Sponge.getCommandManager().register(MagiBridge.getInstance(), reloadCommand, "magibridgereload");
            Sponge.getCommandManager().register(MagiBridge.getInstance(), cs, "mbroadcast", "mb");

            if (config.CORE.ENABLE_CONSOLE_LOGGING && !config.CHANNELS.CONSOLE_CHANNEL.isEmpty()) {
                consoleHandler = new ConsoleHandler(this);
                consoleHandler.start();
                ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).addAppender(consoleHandler);
            }
        }).exceptionally(throwable -> {
            logger.error("Error loading MagiBridge: ", throwable.getCause());
            return null;
        });

        Sponge.getServiceManager().provide(PermissionService.class).ifPresent(svc -> {
            svc.getDefaults().getTransientSubjectData().setPermission(Sets.newHashSet(), "magibridge.chat", Tristate.TRUE);
            svc.getDefaults().getTransientSubjectData().setPermission(Sets.newHashSet(), "magibridge.mention", Tristate.TRUE);
        });
    }

    @Listener
    public void onStoppingServer(GameStoppingServerEvent event) {
        if (jda != null) {
            String startChannel = config.CHANNELS.START_MESSAGES_CHANNEL;
            if (startChannel.isEmpty())
                startChannel = config.CHANNELS.MAIN_CHANNEL;

            DiscordMessageBuilder.forChannel(startChannel)
                    .format(FormatType.SERVER_STOPPING)
                    .useWebhook(false)
                    .send();

            if (this.nickUpdaterTask != null)
                this.nickUpdaterTask.cancel();
            if (this.topicUpdaterTask != null)
                this.topicUpdaterTask.cancel();
            if (this.tpsCollectorTask != null)
                this.tpsCollectorTask.cancel();

            // TextChannel channel = jda.getTextChannelById(config.CHANNELS.MAIN_CHANNEL);
            // if (channel != null)
            //     channel.getManager().setTopic(FormatType.OFFLINE_TOPIC_FORMAT.get()).complete();

            jda.shutdown();
            long time = System.currentTimeMillis();
            while (System.currentTimeMillis() - time < 10000 && jda.getStatus() != JDA.Status.SHUTDOWN) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Listener
    public void reload(GameReloadEvent event) {
        this.reload();
    }

    CompletableFuture<Void> init() {
        return CompletableFuture.runAsync(() -> {
            config = new ConfigManager(instance).loadConfig();
            // needed because of parsing issues
            Utils.turnAllConfigChannelsNumeric();

            boolean throwException = false;
            try {
                jda = JDABuilder.create(
                        config.CORE.BOT_TOKEN,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_EMOJIS,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_VOICE_STATES,
                        GatewayIntent.DIRECT_MESSAGES
                )
                .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS)
                .build()
                .awaitReady();
                jda.addEventListener(new MessageListener());
            } catch (LoginException e) {
                throwException = true;
                logger.error("ERROR STARTING THE PLUGIN: THE TOKEN IN THE CONFIG IS INVALID!");
                logger.error("You probably didn't set the token yet, edit your config!");
            } catch (IllegalStateException e) {
                throwException = true;
                if (e.getMessage().contains("Was shutdown trying to await status")) {
                    logger.error("JDA couldn't start and didn't throw any errors. Make sure your bot " +
                            "has the SERVER MEMBERS INTENT enabled in the application page! See https://github.com/Eufranio/MagiBridge#how-to-magibridge");
                } else {
                    logger.error("Error connecting to discord. This is NOT a plugin error: " + e.getMessage());
                }
            } catch (Exception e) {
                throwException = true;
                logger.error("Error connecting to discord. This is NOT a plugin error: " + e.getMessage());
            }

            if (throwException) {
                RuntimeException exception = new RuntimeException("MagiBridge errored and could not start, check the logs for the error!");
                exception.setStackTrace(new StackTraceElement[0]);
                throw exception;
            }
        }, executor).thenRun(() -> {
            this.registerListeners();
            if (jda.getStatus() == JDA.Status.CONNECTED) {
                if (config.CORE.ENABLE_NICKNAME_UPDATER) {
                    this.nickUpdaterTask = Task.builder()
                        .interval(Math.max(config.CORE.NICKNAME_UPDATER_INTERVAL, 1), TimeUnit.SECONDS)
                        .execute(new NicknameUpdaterTask())
                        .submit(this);
                }
                if (config.CORE.ENABLE_TOPIC_UPDATER) {
                    this.topicUpdaterTask = Task.builder()
                            .interval(Math.max(config.CORE.TOPIC_UPDATER_INTERVAL, 10), TimeUnit.MINUTES)
                            .execute(new TopicUpdaterTask())
                            .submit(this);
                }
                if (config.CORE.ENABLE_TPS_COLLECTOR) {
                    this.tpsCollectorTask = Task.builder()
                        .interval(5, TimeUnit.MINUTES)
                        .execute(new TPSCollectorTask())
                        .submit(this);
                }
            }
        });
    }

    CompletableFuture<Void> stop() {
        if (this.nickUpdaterTask != null) {
            this.nickUpdaterTask.cancel();

            String guildId = config.CHANNELS.NICKNAME_UPDATER_GUILD_ID;
            String voiceChannelId = config.CHANNELS.NICKNAME_INDICATOR_VOICE_ID;
            if (!guildId.isEmpty() && !voiceChannelId.isEmpty()) {
                Guild guild = jda.getGuildById(guildId);
                if (guild != null && guild.getAudioManager().isConnected())
                    guild.getAudioManager().closeAudioConnection();
            }
        }
        if (this.topicUpdaterTask != null)
            this.topicUpdaterTask.cancel();
        if (this.tpsCollectorTask != null)
            this.tpsCollectorTask.cancel();

        // Unregistering listeners
        Sponge.getEventManager().unregisterPluginListeners(this);
        Sponge.getEventManager().registerListeners(this, this);

        return CompletableFuture.runAsync(() -> {
            logger.info("Disconnecting from Discord...");
            if (jda != null && jda.getStatus() != JDA.Status.SHUTDOWN && jda.getStatus() != JDA.Status.SHUTTING_DOWN) {
                jda.shutdownNow();
            }

            config = null;
        }).exceptionally(throwable -> {
            logger.error("Error stopping MagiBridge: ", throwable.getCause());
            return null;
        });
    }

    public void reload() {
        this.stop().thenCompose(v -> this.init())
            .thenRun(() -> logger.info("Plugin reloaded successfully!"))
            .exceptionally(throwable -> {
                logger.error("Error reloading MagiBridge: ", throwable.getCause());
                return null;
            });
    }

    private void registerListeners() {
        NucleusBridgeImpl.init(this);
        if (config.CHANNELS.USE_NUCLEUS) {
            useVanillaChat = true;
            if (Sponge.getPluginManager().getPlugin("nucleus").isPresent()) {
                logger.info("Hooking into Nucleus");
            } else {
                logger.error(" ");
                logger.error(" MagiBridge is configured to hook into Nucleus, but it isn't loaded! Please disable using-nucleus or load Nucleus on your server!");
                logger.error(" ");
            }
        } else if (config.CHANNELS.USE_UCHAT) {
            if (Sponge.getPluginManager().getPlugin("ultimatechat").isPresent()) {
                Sponge.getEventManager().registerListeners(this, new UChatListeners(this));
                logger.info("Hooking into UltimateChat");
            } else {
                logger.error(" ");
                logger.error(" MagiBridge is configured to hook into UltimateChat, but it isn't loaded! Please disable using-ultimatechat or load UltimateChat on your server!");
                logger.error(" ");
                useVanillaChat = true;
            }
        } else {
            logger.info(" No Chat Hook enabled, hooking into the vanilla chat system");
            logger.info(" Some features may not work, and there will be no staff chat. If you want a more complete chat handling, use either Nucleus or UltimateChat.");
            useVanillaChat = true;
        }

        Sponge.getEventManager().registerListeners(this, new VanillaListeners(this));
    }

    public List<String> getListeningChannels() {
        List<String> channels = Lists.newArrayList();

        if (config.CORE.ENABLE_CONSOLE_LOGGING && !config.CHANNELS.CONSOLE_CHANNEL.isEmpty())
            channels.add(config.CHANNELS.CONSOLE_CHANNEL);

        if (config.CHANNELS.USE_UCHAT)
            channels.addAll(config.CHANNELS.UCHAT.UCHAT_CHANNELS.keySet());


        if (config.CHANNELS.USE_NUCLEUS) {
            channels.add(config.CHANNELS.NUCLEUS.GLOBAL_CHANNEL);
            channels.add(config.CHANNELS.NUCLEUS.STAFF_CHANNEL);
            if (!config.CHANNELS.NUCLEUS.HELPOP_CHANNEL.isEmpty())
                channels.add(config.CHANNELS.NUCLEUS.HELPOP_CHANNEL);
            if (!config.CHANNELS.NUCLEUS.AFK_MESSAGES_CHANNEL.isEmpty())
                channels.add(config.CHANNELS.NUCLEUS.AFK_MESSAGES_CHANNEL);
        }

        if (!config.CHANNELS.MAIN_CHANNEL.isEmpty())
            channels.add(config.CHANNELS.MAIN_CHANNEL);

        if (!config.CHANNELS.JOIN_MESSAGES_CHANNEL.isEmpty())
            channels.add(config.CHANNELS.JOIN_MESSAGES_CHANNEL);

        if (!config.CHANNELS.ADVANCEMENT_MESSAGES_CHANNEL.isEmpty())
            channels.add(config.CHANNELS.ADVANCEMENT_MESSAGES_CHANNEL);

        if (!config.CHANNELS.DEATH_MESSAGES_CHANNEL.isEmpty())
            channels.add(config.CHANNELS.DEATH_MESSAGES_CHANNEL);

        if (!config.CHANNELS.WELCOME_MESSAGES_CHANNEL.isEmpty())
            channels.add(config.CHANNELS.WELCOME_MESSAGES_CHANNEL);

        if (!config.CHANNELS.TOPIC_UPDATER_CHANNEL.isEmpty())
            channels.add(config.CHANNELS.TOPIC_UPDATER_CHANNEL);

        if (!config.CHANNELS.START_MESSAGES_CHANNEL.isEmpty())
            channels.add(config.CHANNELS.START_MESSAGES_CHANNEL);

        return channels;
    }

    public boolean enableVanillaChat() {
        return this.useVanillaChat;
    }

    public ConfigCategory getConfig() {
        return this.config;
    }

    public JDA getJDA() {
        return this.jda;
    }

    public static MagiBridge getInstance() {
        return instance;
    }

    public static Logger getLogger() {
        return instance.logger;
    }
}
