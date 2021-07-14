package com.magitechserver.magibridge.task;

import com.magitechserver.magibridge.MagiBridge;
import com.magitechserver.magibridge.config.FormatType;
import com.magitechserver.magibridge.config.categories.ConfigCategory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.TextChannel;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.scheduler.Task;

import java.lang.management.ManagementFactory;
import java.util.function.Consumer;
import java.util.function.Function;

public class TopicUpdaterTask implements Consumer<Task> {
  private final JDA jda = MagiBridge.getInstance().getJDA();
  private final ConfigCategory config = MagiBridge.getInstance().getConfig();
  private final Logger logger = MagiBridge.getLogger();

  @Override
  public void accept(Task task) {
    String channelId = config.CHANNELS.TOPIC_UPDATER_CHANNEL;
    if (channelId.isEmpty())
      channelId = config.CHANNELS.MAIN_CHANNEL;

    TextChannel channel = jda.getTextChannelById(channelId);
    if (channel == null) {
      logger.error("The main-discord-channel is INVALID, replace it with a valid one and restart the server!");
      return;
    }

    Function<String, String> replace = s ->
        s.replace("%players%", "" + Sponge.getServer().getOnlinePlayers().stream().filter(p -> !p.get(Keys.VANISH).orElse(false)).count())
            .replace("%maxplayers%", Integer.valueOf(Sponge.getServer().getMaxPlayers()).toString())
            .replace("%tps%", Long.valueOf(Math.round(Sponge.getServer().getTicksPerSecond())).toString())
            .replace("%daysonline%", Long.valueOf(ManagementFactory.getRuntimeMXBean().getUptime() / (24 * 60 * 60 * 1000)).toString())
            .replace("%hoursonline%", Long.valueOf((ManagementFactory.getRuntimeMXBean().getUptime() / (60 * 60 * 1000)) % 24).toString())
            .replace("%minutesonline%", Long.valueOf((ManagementFactory.getRuntimeMXBean().getUptime() / (60 * 1000)) % 60).toString());

    String topic = replace.apply(FormatType.TOPIC_FORMAT.get());

    channel.getManager().setTopic(topic).queue();

    if (!config.MESSAGES.BOT_GAME_STATUS.isEmpty()) {
      String msg = replace.apply(config.MESSAGES.BOT_GAME_STATUS);

      Activity activity = jda.getPresence().getActivity();
      if (activity != null && activity.getName().equals(msg))
        return;

      jda.getPresence().setActivity(Activity.playing(msg));
    }
  }
}
