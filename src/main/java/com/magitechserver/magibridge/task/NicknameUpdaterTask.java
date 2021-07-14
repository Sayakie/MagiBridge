package com.magitechserver.magibridge.task;

import com.magitechserver.magibridge.MagiBridge;
import com.magitechserver.magibridge.config.FormatType;
import com.magitechserver.magibridge.config.categories.ConfigCategory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.scheduler.Task;

import java.lang.management.ManagementFactory;
import java.util.function.Consumer;
import java.util.function.Function;

public class NicknameUpdaterTask implements Consumer<Task> {
  private final JDA jda = MagiBridge.getInstance().getJDA();
  private final ConfigCategory config = MagiBridge.getInstance().getConfig();
  private final Logger logger = MagiBridge.getLogger();

  @Override
  public void accept(Task task) {
    String guildId = config.CHANNELS.NICKNAME_UPDATER_GUILD_ID;
    if (guildId.isEmpty())
      return;

    Guild guild = jda.getGuildById(guildId);
    if (guild == null) {
      logger.error("The nickname-updater-guild-id is invalid, replace it with a valid one and restart the server!");
      return;
    }

    Function<String, String> replace = s ->
        s.replace("%players%", "" + Sponge.getServer().getOnlinePlayers().stream().filter(p -> !p.get(Keys.VANISH).orElse(false)).count())
            .replace("%maxplayers%", Integer.valueOf(Sponge.getServer().getMaxPlayers()).toString())
            .replace("%tps%", Long.valueOf(Math.round(Sponge.getServer().getTicksPerSecond())).toString())
            .replace("%daysonline%", Long.valueOf(ManagementFactory.getRuntimeMXBean().getUptime() / (24 * 60 * 60 * 1000)).toString())
            .replace("%hoursonline%", Long.valueOf((ManagementFactory.getRuntimeMXBean().getUptime() / (60 * 60 * 1000)) % 24).toString())
            .replace("%minutesonline%", Long.valueOf((ManagementFactory.getRuntimeMXBean().getUptime() / (60 * 1000)) % 60).toString());

    String nickname = replace.apply(FormatType.NICKNAME_FORMAT.get());

    if (guild.getSelfMember().getEffectiveName().equals(nickname))
      return;

    guild.getSelfMember().modifyNickname(nickname).queue();
  }
}
