package com.magitechserver.magibridge.discord;

import com.magitechserver.magibridge.MagiBridge;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;

public class DiscordHandler {
  private static JDA jda = MagiBridge.getInstance().getJDA();

  public static void sendMessageToChannel(String channelId, Object message) {
    if (jda == null || jda.getStatus() != JDA.Status.CONNECTED)
      return;

    TextChannel textChannel = jda.getTextChannelById(channelId);
    if (textChannel == null)
      return;

    if (message instanceof EmbedBuilder)
      message = ((EmbedBuilder) message).build();

    if (!(message instanceof String || message instanceof MessageEmbed))
      return;

    try {
      textChannel.sendMessage((String) message).queue();
    } catch (Exception error) {
      error.printStackTrace();
    }
  }

  public static void sendMessageToChannel(String channelId, String[] messages) {
    sendMessageToChannel(channelId, String.join(" ", messages));
  }

  public static void sendMessageToChannel(Long channelId, String message) {
    sendMessageToChannel(channelId.toString(), message);
  }
}
