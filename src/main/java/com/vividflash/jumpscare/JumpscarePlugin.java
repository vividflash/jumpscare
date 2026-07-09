/*
 * Copyright (c) 2026, vividflash
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.vividflash.jumpscare;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
    name = "Jumpscare"
)
public class JumpscarePlugin extends Plugin
{
    private final Random random = new Random();

    @Inject
    private Client client;

    @Inject
    private JumpscareConfig config;

    @Inject
    private JumpscareOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AudioPlayer audioPlayer;

    /**
     * When the current scare should stop being drawn. Null when no scare is active.
     */
    private volatile Instant scareEndTime;

    /**
     * When the current scare started (used by FLASH mode to compute elapsed time).
     */
    private volatile Instant scareStartTime;

    /**
     * The image the overlay should draw for the current scare (bundled or custom).
     */
    private volatile BufferedImage activeImage;

    /**
     * The bundled default scare image, loaded once at start-up.
     */
    private BufferedImage bundledImage;

    /**
     * Cache of the last custom image loaded, keyed by its path, to avoid re-reading
     * the same file from disk on every trigger.
     */
    private String cachedCustomPath;
    private BufferedImage cachedCustomImage;

    @Override
    protected void startUp()
    {
        bundledImage = loadBundledImage();
        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        scareEndTime = null;
        scareStartTime = null;
        activeImage = null;
    }

    @Provides
    JumpscareConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(JumpscareConfig.class);
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        // Don't stack scares while one is still showing.
        if (isActive())
        {
            return;
        }

        int denominator = Math.max(1, config.chanceDenominator());
        if (random.nextInt(denominator) == 0)
        {
            triggerJumpscare();
        }
    }

    @Subscribe
    public void onCommandExecuted(CommandExecuted event)
    {
        if ("scaretest".equals(event.getCommand()))
        {
            triggerJumpscare();
        }
    }

    /**
     * Fire a jumpscare now: resolve the image to show, arm the timing window, and
     * play the sound. Safe to call from any client-thread event handler; never throws.
     */
    void triggerJumpscare()
    {
        int duration = Math.max(1, config.durationMs());
        activeImage = resolveImage();
        scareStartTime = Instant.now();
        scareEndTime = scareStartTime.plusMillis(duration);

        if (config.soundEnabled())
        {
            playScream();
        }
    }

    private void playScream()
    {
        int volume = config.volume();
        if (volume <= 0)
        {
            return;
        }

        // Squared for a perceptual-feeling curve: 50 ≈ quarter amplitude, 20 clearly quiet.
        float amplitude = (volume / 100f) * (volume / 100f);

        try
        {
            byte[] wav = loadScreamBytes();
            // AudioPlayer's gain parameter relies on the mixer exposing a MASTER_GAIN
            // control and is silently ignored where it doesn't; scaling the samples
            // ourselves works on every system, so always pass gain 0.
            audioPlayer.play(new ByteArrayInputStream(scaleWavVolume(wav, amplitude)), 0f);
        }
        catch (Exception e)
        {
            // Includes IOException, UnsupportedAudioFileException, LineUnavailableException.
            log.warn("Failed to play jumpscare sound", e);
        }
    }

    private byte[] loadScreamBytes() throws IOException
    {
        String customSound = config.customSoundPath();
        if (customSound != null && !customSound.trim().isEmpty())
        {
            File soundFile = new File(customSound.trim());
            if (soundFile.isFile())
            {
                return Files.readAllBytes(soundFile.toPath());
            }
            log.warn("Custom sound path does not point to a file, falling back to bundled scream: {}", customSound);
        }

        try (InputStream in = getClass().getResourceAsStream("scream.wav"))
        {
            if (in == null)
            {
                throw new IOException("Bundled scream.wav resource not found on classpath");
            }
            return in.readAllBytes();
        }
    }

    /**
     * Decode a WAV to 16-bit signed PCM, multiply every sample by {@code amplitude},
     * and re-encode as WAV bytes.
     */
    private static byte[] scaleWavVolume(byte[] wavBytes, float amplitude) throws Exception
    {
        if (amplitude >= 0.999f)
        {
            return wavBytes;
        }

        try (AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wavBytes)))
        {
            AudioFormat src = in.getFormat();
            AudioFormat pcm16 = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                src.getSampleRate(), 16, src.getChannels(), src.getChannels() * 2,
                src.getSampleRate(), false);
            try (AudioInputStream converted = AudioSystem.getAudioInputStream(pcm16, in))
            {
                byte[] data = converted.readAllBytes();
                for (int i = 0; i + 1 < data.length; i += 2)
                {
                    int sample = (short) ((data[i] & 0xFF) | (data[i + 1] << 8));
                    sample = Math.max(Short.MIN_VALUE,
                        Math.min(Short.MAX_VALUE, Math.round(sample * amplitude)));
                    data[i] = (byte) sample;
                    data[i + 1] = (byte) (sample >> 8);
                }

                AudioInputStream scaled = new AudioInputStream(
                    new ByteArrayInputStream(data), pcm16, data.length / pcm16.getFrameSize());
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                AudioSystem.write(scaled, AudioFileFormat.Type.WAVE, out);
                return out.toByteArray();
            }
        }
    }

    /**
     * Resolve which image to draw for this trigger: the custom image if configured and
     * loadable, otherwise the bundled default.
     */
    private BufferedImage resolveImage()
    {
        String customPath = config.customImagePath();
        if (customPath != null && !customPath.trim().isEmpty())
        {
            String path = customPath.trim();
            if (path.equals(cachedCustomPath) && cachedCustomImage != null)
            {
                return cachedCustomImage;
            }

            try
            {
                File imageFile = new File(path);
                if (imageFile.isFile())
                {
                    BufferedImage loaded = ImageIO.read(imageFile);
                    if (loaded != null)
                    {
                        cachedCustomPath = path;
                        cachedCustomImage = loaded;
                        return loaded;
                    }
                }
                log.warn("Could not load custom image, falling back to bundled: {}", path);
            }
            catch (IOException e)
            {
                log.warn("Failed to read custom image, falling back to bundled: {}", path, e);
            }
        }

        return bundledImage;
    }

    private BufferedImage loadBundledImage()
    {
        try (InputStream in = getClass().getResourceAsStream("scare.png"))
        {
            if (in == null)
            {
                log.warn("Bundled scare.png resource not found on classpath");
                return null;
            }
            return ImageIO.read(in);
        }
        catch (IOException e)
        {
            log.warn("Failed to load bundled scare.png", e);
            return null;
        }
    }

    /**
     * @return true if a scare is currently within its display window.
     */
    boolean isActive()
    {
        Instant end = scareEndTime;
        return end != null && Instant.now().isBefore(end);
    }

    Instant getScareStartTime()
    {
        return scareStartTime;
    }

    BufferedImage getActiveImage()
    {
        return activeImage;
    }
}
