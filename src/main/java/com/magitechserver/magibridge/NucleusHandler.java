package com.magitechserver.magibridge;

import com.magitechserver.magibridge.util.ReplacerUtil;
import flavor.pie.boop.BoopableChannel;
import io.github.nucleuspowered.nucleus.api.NucleusAPI;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Created by Frani on 24/08/2017.
 */
public class NucleusHandler {

    public static void handle(boolean isStaffChannel, String format, Map<String, String> placeholders, boolean hasAttachment, List<Message.Attachment> attachments) {
        MessageChannel messageChannel;
        if(!isStaffChannel) {
            if(Sponge.getPluginManager().getPlugin("boop").isPresent() && MagiBridge.getConfig().getBool("misc", "use-boop")) {
                messageChannel = new BoopableChannel(Sponge.getServer().getBroadcastChannel().getMembers());
            } else {
                messageChannel = Sponge.getServer().getBroadcastChannel();
            }
        } else {
            messageChannel = NucleusAPI.getStaffChatService().get().getStaffChat();
            format = MagiBridge.getConfig().getString("messages", "discord-to-server-staff-format");
        }

        String msg = ReplacerUtil.replaceEach(format, placeholders);

        if(messageChannel != null) {
            Text messageAsText = Text.of(msg);
            if(hasAttachment) {
                Text attachment = attachmentBuilder(attachments);
                messageChannel.getMembers().forEach(player -> player.sendMessage(Text.of(msg).concat(attachment)));
            } else {
                messageChannel.getMembers().forEach(player -> player.sendMessage(messageAsText));
            }
        }
    }

    private static Text attachmentBuilder(List<Message.Attachment> attachments) {
        Text text = Text.of();
        Text.Builder builder = Text.builder();
        Text hover = TextSerializers.FORMATTING_CODE.deserialize("&bAttachment: ").concat(Text.NEW_LINE);
        for(Message.Attachment attachment : attachments) {
            hover = hover.concat(Text.of(attachment.getFileName())).concat(Text.NEW_LINE);
        }
        hover = hover.concat(TextSerializers.FORMATTING_CODE.deserialize("&bClick to open the attachment!"));
        URL url = null;
        try {
            url = new URL(attachments.get(0).getUrl());
        } catch (MalformedURLException exception) {}
        text = Text.builder(MagiBridge.getConfig().getString("messages", "attachment-name"))
                .color(TextColors.AQUA)
                .onHover(TextActions.showText(hover))
                .onClick(TextActions.openUrl(url))
                .build();
        return text;
    }

}