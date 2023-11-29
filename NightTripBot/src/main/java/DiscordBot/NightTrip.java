package DiscordBot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.voice.AudioProvider;

/**
 * Main class for the Discord Bot 'Night Trip'.
 */
public class NightTrip {
  // Holds the commands to be read and executed
  private static final Map<String, Command> commands = new HashMap<>();

  public static void main(String[] args) {
    // Creates AudioPlayer instances and translates URLs to AudioTrack instances
    final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
    // This is an optimization strategy that Discord4J can utilize
    playerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
    // Allow playerManager to parse remote sources like YouTube links
    AudioSourceManagers.registerRemoteSources(playerManager);
    // Create an AudioPlayer so Discord4J can receive audio data
    final AudioPlayer player = playerManager.createPlayer();

    AudioProvider provider = new LavaPlayerAudioProvider(player);

    commands.put("join", event -> {
      final Member member = event.getMember().orElse(null);
      if (member != null) {
        final VoiceState voiceState = member.getVoiceState().block();
        if (voiceState != null) {
          final VoiceChannel channel = voiceState.getChannel().block();
          if (channel != null) {
            // join returns a VoiceConnection which would be required if we were
            // adding disconnection features, but for now we are just ignoring it.
            channel.join(spec -> spec.setProvider(provider)).block();
          }
        }
      }
    });

    final TrackScheduler scheduler = new TrackScheduler(player);
    commands.put("play", event -> {
      final String content = event.getMessage().getContent();
      final List<String> command = Arrays.asList(content.split(" "));
      playerManager.loadItem(command.get(1), scheduler);
    });

    final GatewayDiscordClient client = DiscordClientBuilder.create(args[0]).build()
            .login()
            .block();

    client.getEventDispatcher().on(MessageCreateEvent.class)
            // subscribe is like block, in that it will *request* for action
            // to be done, but instead of blocking the thread, waiting for it
            // to finish, it will just execute the results asynchronously.
            .subscribe(event -> {
              final String content = event.getMessage().getContent(); // 3.1 Message.getContent() is a String
              for (final Map.Entry<String, Command> entry : commands.entrySet()) {
                // We will be using ! as our "prefix" to any command in the system.
                if (content.startsWith('!' + entry.getKey())) {
                  entry.getValue().execute(event);
                  break;
                }
              }
            });

    client.onDisconnect().block();
  }

  // First command added to check it is working
  static {
    commands.put("hello", event -> event.getMessage()
            .getChannel().block()
            .createMessage("Hello user!").block());
  }
}
