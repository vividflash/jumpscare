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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
import net.runelite.client.RuneLite;
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
    /**
     * All file I/O is restricted to this plugin-specific subfolder under
     * .runelite (Plugin Hub requirement). Created on startup so users can
     * drop their custom image/WAV into it.
     */
    private static final File PLUGIN_DIR = new File(RuneLite.RUNELITE_DIR, "jumpscare");

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
     * Animated custom images keep frames this large at most (longest side);
     * the overlay upscales to the canvas, and uncompressed frames at full
     * screen resolution would multiply the heap cost for little visible gain
     * during a sub-second scare.
     */
    private static final int MAX_ANIMATED_DIMENSION = 640;

    /**
     * The image the overlay should draw for the current scare (bundled or custom).
     */
    private volatile AnimatedImage activeImage;

    /**
     * The theme of the currently active scare (drives flash colours).
     */
    private volatile JumpscareTheme activeTheme = JumpscareTheme.SCARY;

    /**
     * The bundled default images, loaded once at start-up.
     */
    private AnimatedImage bundledScary;
    private AnimatedImage bundledHappy;

    /**
     * Cache of the last custom image loaded, keyed by its path, to avoid re-reading
     * the same file from disk on every trigger.
     */
    private String cachedCustomPath;
    private AnimatedImage cachedCustomImage;

    @Override
    protected void startUp()
    {
        if (!PLUGIN_DIR.exists() && !PLUGIN_DIR.mkdirs())
        {
            log.warn("Could not create plugin folder {}", PLUGIN_DIR);
        }
        bundledScary = loadBundledImage("scare.png");
        bundledHappy = loadBundledImage("happy.png");
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
            triggerJumpscare(config.theme());
        }
    }

    @Subscribe
    public void onCommandExecuted(CommandExecuted event)
    {
        if (!"stest".equals(event.getCommand()))
        {
            return;
        }

        JumpscareTheme theme = config.theme();
        String[] args = event.getArguments();
        if (args != null && args.length > 0)
        {
            if ("happy".equalsIgnoreCase(args[0]) || "h".equalsIgnoreCase(args[0]))
            {
                theme = JumpscareTheme.HAPPY;
            }
            else if ("scary".equalsIgnoreCase(args[0]) || "s".equalsIgnoreCase(args[0]))
            {
                theme = JumpscareTheme.SCARY;
            }
        }
        triggerJumpscare(theme);
    }

    /**
     * Fire a jumpscare now: resolve the image to show, arm the timing window, and
     * play the sound. Safe to call from any client-thread event handler; never throws.
     */
    void triggerJumpscare(JumpscareTheme theme)
    {
        int duration = Math.max(1, config.durationMs());
        activeTheme = theme;
        activeImage = resolveImage(theme);
        scareStartTime = Instant.now();
        scareEndTime = scareStartTime.plusMillis(duration);

        if (config.soundEnabled())
        {
            playScream(theme);
        }
    }

    private void playScream(JumpscareTheme theme)
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
            byte[] wav = loadScreamBytes(theme);
            // AudioPlayer's gain parameter relies on the mixer exposing a MASTER_GAIN
            // control and is silently ignored where it doesn't; scaling the samples
            // ourselves works on every system, so prefer that and pass gain 0.
            byte[] scaled = scaleWavPcm16(wav, amplitude);
            if (scaled != null)
            {
                audioPlayer.play(new ByteArrayInputStream(scaled), 0f);
            }
            else
            {
                // Unrecognized WAV flavour (custom file): let AudioPlayer decode it
                // and fall back to best-effort mixer gain.
                float gainDb = Math.max(-80f, (float) (20.0 * Math.log10(volume / 100.0)));
                audioPlayer.play(new ByteArrayInputStream(wav), gainDb);
            }
        }
        catch (Exception e)
        {
            // Includes IOException, UnsupportedAudioFileException, LineUnavailableException.
            log.warn("Failed to play jumpscare sound", e);
        }
    }

    private byte[] loadScreamBytes(JumpscareTheme theme) throws IOException
    {
        // The custom sound only replaces the scary sound; happy always uses the bundled jingle.
        if (theme == JumpscareTheme.SCARY)
        {
            String customSound = config.customSoundFile();
            if (customSound != null && !customSound.trim().isEmpty())
            {
                File soundFile = resolvePluginFile(customSound.trim());
                if (soundFile != null && soundFile.isFile())
                {
                    return Files.readAllBytes(soundFile.toPath());
                }
                log.warn("Custom sound file not found in {}, falling back to bundled scream: {}", PLUGIN_DIR, customSound);
            }
        }

        String resource = theme == JumpscareTheme.HAPPY ? "happy.wav" : "scream.wav";
        try (InputStream in = getClass().getResourceAsStream(resource))
        {
            if (in == null)
            {
                throw new IOException("Bundled " + resource + " resource not found on classpath");
            }
            return in.readAllBytes();
        }
    }

    /**
     * Multiply every sample of a 16-bit PCM RIFF/WAVE by {@code amplitude}, returning
     * a scaled copy with the original headers intact. Returns null when the bytes are
     * not a WAV flavour this parser understands (caller falls back to mixer gain).
     * Parsed by hand because the plugin hub disallows javax.sound.* in plugin code.
     */
    private static byte[] scaleWavPcm16(byte[] wav, float amplitude)
    {
        if (amplitude >= 0.999f)
        {
            return wav;
        }
        if (wav.length < 44
            || wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F'
            || wav[8] != 'W' || wav[9] != 'A' || wav[10] != 'V' || wav[11] != 'E')
        {
            return null;
        }

        byte[] out = wav.clone();
        boolean pcm16 = false;
        int pos = 12;
        while (pos + 8 <= out.length)
        {
            int size = (out[pos + 4] & 0xFF) | (out[pos + 5] & 0xFF) << 8
                | (out[pos + 6] & 0xFF) << 16 | (out[pos + 7] & 0xFF) << 24;
            if (size < 0 || pos + 8 + size > out.length)
            {
                return null;
            }

            if (out[pos] == 'f' && out[pos + 1] == 'm' && out[pos + 2] == 't' && out[pos + 3] == ' ')
            {
                if (size < 16)
                {
                    return null;
                }
                int audioFormat = (out[pos + 8] & 0xFF) | (out[pos + 9] & 0xFF) << 8;
                int bitsPerSample = (out[pos + 22] & 0xFF) | (out[pos + 23] & 0xFF) << 8;
                if (audioFormat != 1 || bitsPerSample != 16)
                {
                    return null;
                }
                pcm16 = true;
            }
            else if (out[pos] == 'd' && out[pos + 1] == 'a' && out[pos + 2] == 't' && out[pos + 3] == 'a')
            {
                if (!pcm16)
                {
                    return null;
                }
                int end = pos + 8 + size;
                for (int i = pos + 8; i + 1 < end; i += 2)
                {
                    int sample = (short) ((out[i] & 0xFF) | out[i + 1] << 8);
                    sample = Math.max(Short.MIN_VALUE,
                        Math.min(Short.MAX_VALUE, Math.round(sample * amplitude)));
                    out[i] = (byte) sample;
                    out[i + 1] = (byte) (sample >> 8);
                }
                return out;
            }

            // Chunks are word-aligned; odd sizes are padded with one byte.
            pos += 8 + size + (size & 1);
        }
        return null;
    }

    /**
     * Resolve which image to draw for this trigger: the custom image if configured and
     * loadable, otherwise the bundled default.
     */
    private AnimatedImage resolveImage(JumpscareTheme theme)
    {
        // The custom image only replaces the scary image; happy always uses the bundled one.
        if (theme == JumpscareTheme.HAPPY)
        {
            return bundledHappy;
        }

        String customName = config.customImageFile();
        if (customName != null && !customName.trim().isEmpty())
        {
            String name = customName.trim();
            if (name.equals(cachedCustomPath) && cachedCustomImage != null)
            {
                return cachedCustomImage;
            }

            try
            {
                File imageFile = resolvePluginFile(name);
                if (imageFile != null && imageFile.isFile())
                {
                    AnimatedImage loaded = AnimatedImage.load(imageFile, MAX_ANIMATED_DIMENSION, 0);
                    if (loaded != null)
                    {
                        cachedCustomPath = name;
                        cachedCustomImage = loaded;
                        return loaded;
                    }
                }
                log.warn("Could not load custom image from {}, falling back to bundled: {}", PLUGIN_DIR, name);
            }
            catch (IOException e)
            {
                log.warn("Failed to read custom image, falling back to bundled: {}", name, e);
            }
        }

        return bundledScary;
    }

    /**
     * Resolve a configured file name inside the plugin's .runelite subfolder.
     * Only files within that folder are ever read; a name that escapes it
     * (e.g. via "..") resolves to null.
     */
    private static File resolvePluginFile(String name)
    {
        try
        {
            File file = new File(PLUGIN_DIR, name);
            String base = PLUGIN_DIR.getCanonicalPath() + File.separator;
            return file.getCanonicalPath().startsWith(base) ? file : null;
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private AnimatedImage loadBundledImage(String resource)
    {
        try (InputStream in = getClass().getResourceAsStream(resource))
        {
            if (in == null)
            {
                log.warn("Bundled {} resource not found on classpath", resource);
                return null;
            }
            return AnimatedImage.of(ImageIO.read(in));
        }
        catch (IOException e)
        {
            log.warn("Failed to load bundled {}", resource, e);
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

    AnimatedImage getActiveImage()
    {
        return activeImage;
    }

    JumpscareTheme getActiveTheme()
    {
        return activeTheme;
    }
}
