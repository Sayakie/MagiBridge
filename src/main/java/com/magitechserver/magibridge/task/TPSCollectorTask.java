package com.magitechserver.magibridge.task;

import com.magitechserver.magibridge.MagiBridge;
import com.magitechserver.magibridge.config.categories.ConfigCategory;
import com.magitechserver.magibridge.util.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.scheduler.Task;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.function.Consumer;

public class TPSCollectorTask implements Consumer<Task> {
  private final JDA jda = MagiBridge.getInstance().getJDA();
  private final ConfigCategory config = MagiBridge.getInstance().getConfig();
  private final Logger logger = MagiBridge.getLogger();

  private final DateFormat format = new SimpleDateFormat("MM-dd HH:mm:ss");

  public static HashMap<String, Double> tpsSet = new HashMap<>();

  @Override
  public void accept(Task task) {
    Double currentTps = Sponge.getServer().getTicksPerSecond();

    tpsSet.put(format.format(Calendar.getInstance().getTime()), currentTps);

    String channelId = config.CHANNELS.TPS_CHANNEL;
    if (channelId.isEmpty())
      return;

    TextChannel textChannel = jda.getTextChannelById(channelId);
    if (textChannel == null) {
      logger.error("The tps-channel is invalid.");
      return;
    }

    EmbedBuilder embedBuilder = Utils.ofEmbedBuilder();
    tpsSet.entrySet()
        .stream()
        .skip(tpsSet.size() - 6)
        .forEach(entry ->
          embedBuilder.addField(entry.getKey(), entry.getValue().toString(), true)
        );

    textChannel.sendMessage(embedBuilder.build()).queue();
  }
}
