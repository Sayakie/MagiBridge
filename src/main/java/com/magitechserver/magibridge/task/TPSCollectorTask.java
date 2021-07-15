package com.magitechserver.magibridge.task;

import com.magitechserver.magibridge.MagiBridge;
import com.magitechserver.magibridge.config.FormatType;
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
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class TPSCollectorTask implements Consumer<Task> {
  private final JDA jda = MagiBridge.getInstance().getJDA();
  private final ConfigCategory config = MagiBridge.getInstance().getConfig();
  private final Logger logger = MagiBridge.getLogger();

  private final DateFormat format = new SimpleDateFormat(FormatType.TPS_FORMAT.get());
  private final DateFormat titleFormat = new SimpleDateFormat(FormatType.TPS_TITLE_FORMAT.get());

  private Byte stack = 0;

  public static HashMap<String, Double> tpsSet = new LinkedHashMap<>(6);

  @Override
  public void accept(Task task) {
    Date currentTime = Calendar.getInstance().getTime();
    Double currentTps = Sponge.getServer().getTicksPerSecond();

    stack++;
    tpsSet.put(format.format(currentTime), currentTps);

    String channelId = config.CHANNELS.TPS_CHANNEL;
    if (channelId.isEmpty())
      return;

    TextChannel textChannel = jda.getTextChannelById(channelId);
    if (textChannel == null) {
      logger.error("The tps-channel is invalid.");
      return;
    }

    // Yes, I know it is mess up so will be refactor this after I have enough time.
    if (stack < 6)
      return;

    stack = 0;

    Stream<Map.Entry<String, Double>> latestTpsSet = tpsSet.entrySet()
        .stream()
        .skip(tpsSet.size() - 6);
    OptionalDouble tpsAverage = latestTpsSet
        .mapToDouble(Map.Entry::getValue)
        .average();

    int statusColor;
    if (!tpsAverage.isPresent())
      statusColor = 6533493;
    else
      if (tpsAverage.getAsDouble() >= 19)
        statusColor = 6533493;
      else if (tpsAverage.getAsDouble() >= 14)
        statusColor = 16693308;
      else
        statusColor = 13711421;

    EmbedBuilder embedBuilder = Utils.ofEmbedBuilder()
        .setTitle(titleFormat.format(currentTime))
        .setColor(statusColor);
    latestTpsSet
        .forEach(entry -> {
              if (entry == null)
                return;

              embedBuilder.addField(entry.getKey(), entry.getValue().toString(), true);
            }
        );

    textChannel.sendMessage(embedBuilder.build()).queue();
  }
}
