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
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
import net.runelite.client.RuneLite;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
    name = "Jumpscare",
    description = "Random rare jumpscare: full-screen scare image and scream sound.",
    tags = {"jumpscare", "scare", "prank", "fun"}
)
public class JumpscarePlugin extends Plugin
{
    /**
     * All file I/O is restricted to this plugin-specific subfolder under
     * .runelite (Plugin Hub requirement). Created on startup so users can
     * drop their custom image/WAV into it.
     */
    private static final File PLUGIN_DIR = new File(RuneLite.RUNELITE_DIR, "jumpscare");

    private static final String CONFIG_GROUP = "jumpscare";
    private static final String FLASH_MODE_KEY = "flashMode";
    private static final String LAST_SEEN_VERSION_KEY = "lastSeenVersion";

    /**
     * Release discipline: bump VERSION and UPDATE_MESSAGE together on every
     * release (alongside build.gradle and runelite-plugin.properties). Minor
     * releases describe that release; a major x.0 release summarises the
     * important changes since the previous major.
     */
    private static final String VERSION = "1.5";
    private static final String UPDATE_MESSAGE =
        "Jumpscare v1.5: your scare chance was on an outdated default and is now updated. "
            + "Fixed sources for v1.0, v1.1 and v1.3 users with migration.";

    /** Near-black dark red for the one-time update notice. */
    private static final Color UPDATE_MESSAGE_COLOR = new Color(0x480000);
    private static final String CUSTOM_IMAGE_KEY = "customImagePath";
    private static final String CUSTOM_SOUND_KEY = "customSoundPath";
    private static final String IMAGE_SOURCE_KEY = "imageSource";
    private static final String SOUND_SOURCE_KEY = "soundSource";
    private static final String CHANCE_KEY = "chanceDenominator";
    private static final String DURATION_KEY = "durationMs";

    /**
     * v1.4's migration flag, recorded for every install while its migration
     * did nothing (it tested keys the framework had already written).
     */
    private static final String DEAD_V14_MIGRATION_KEY = "customSourceMigrated";

    /** The pre-v1.2 Theme dropdown, read once by the source migration. */
    private static final String OLD_THEME_KEY = "theme";

    /**
     * Keys retired by earlier versions, cleared from the profile once so
     * nobody keeps carrying settings that nothing reads — including testers
     * who ran pre-1.0 builds straight from git. "mode" was a config item
     * before v1.0, "theme" until v1.2 replaced it with the per-asset
     * sources, and the third is v1.4's spent flag. The config framework only
     * ever re-creates {@code @ConfigItem} defaults, so once these are gone
     * they stay gone. REMOVE IN v1.6 — see RELEASE-TODO.md.
     */
    private static final String[] DEAD_KEYS = {"mode", OLD_THEME_KEY, DEAD_V14_MIGRATION_KEY};

    /**
     * Chance defaults shipped by earlier releases. RuneLite writes every
     * config default into the user's profile the first time a plugin loads,
     * so raising the default in a later release never reaches an existing
     * install — they stay on whatever their first version wrote (1 in
     * 100000 for v1.0, 1 in 10000 for v1.1). A value equal to one of these
     * is indistinguishable from an inherited default and gets cleared once;
     * anything else is treated as a deliberate choice and left alone.
     */
    private static final int[] STALE_CHANCE_DEFAULTS = {100_000, 10_000};

    /**
     * Hidden flag recording that the one-time migration ran, so it never
     * overrides a choice the user makes later. v1.4 used its own key with a
     * null check that could not work (the framework had already written the
     * keys it tested), so that flag is set for everyone with nothing done —
     * this release needs a fresh one.
     */
    private static final String MIGRATION_KEY = "migratedV15";

    /**
     * Hard ceiling on the scare duration, enforced in code as well as via
     * {@code @Range} so a stale out-of-range config value can never leave a
     * full-screen overlay covering the client indefinitely.
     */
    private static final int MAX_DURATION_MS = 10_000;

    /**
     * Hidden flag (never shown in the config panel) recording that the user
     * accepted the epilepsy warning once, so startup doesn't re-prompt.
     */
    private static final String FLASH_ACK_KEY = "flashWarningAcknowledged";

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

    @Inject
    private ConfigManager configManager;

    @Inject
    private ScheduledExecutorService executor;

    @Inject
    private ChatMessageManager chatMessageManager;

    /** One update check per session; reset on startUp. */
    private boolean updateChecked;

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
    private static final int MAX_ANIMATED_DIMENSION = 512;

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
     * The configured custom image and sound, preloaded on the executor at
     * startup and whenever their config keys change, so the trigger path
     * (client thread) never touches the disk. Null when unset or unloadable —
     * the trigger falls back to the bundled assets.
     */
    private volatile AnimatedImage customImage;
    private volatile byte[] customSoundBytes;

    /**
     * Human-readable outcome of the last custom image/sound load attempt
     * ("loaded", "file not found in ...", ...), shown by plain ::stest so
     * users can see why a custom file isn't used without digging through
     * client.log. Null when no file name is configured (or none loaded yet).
     */
    private volatile String customImageStatus;
    private volatile String customSoundStatus;

    /**
     * lastModified/length fingerprint of the custom file at its last load
     * attempt (-1 when the file was missing). Each trigger re-stats the
     * configured files on the executor and reloads on mismatch, so late
     * file drops and same-name replacements heal by the next scare without
     * a plugin toggle.
     */
    private volatile long customImageStamp;
    private volatile long customSoundStamp;

    /**
     * Load generations: each (re)load bumps its counter and only the newest
     * load may publish its result, so a slow decode can't overwrite a newer
     * config edit — and results arriving after shutDown are dropped.
     */
    private final AtomicInteger imageLoadGen = new AtomicInteger();
    private final AtomicInteger soundLoadGen = new AtomicInteger();

    @Override
    protected void startUp()
    {
        updateChecked = false;
        if (!PLUGIN_DIR.exists() && !PLUGIN_DIR.mkdirs())
        {
            log.warn("Could not create plugin folder {}", PLUGIN_DIR);
        }
        bundledScary = loadBundledImage("scare.png");
        bundledHappy = loadBundledImage("happy.png");
        migrateOnce();
        reloadCustomImage();
        reloadCustomSound();
        overlayManager.add(overlay);

        // Flash mode may have been switched on while the plugin was disabled
        // (a stopped plugin gets no ConfigChanged events), so the enable-time
        // warning can be bypassed. Catch up here unless already acknowledged.
        if (config.flashMode() && !Boolean.parseBoolean(
            configManager.getConfiguration(CONFIG_GROUP, FLASH_ACK_KEY)))
        {
            confirmFlashMode();
        }
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        scareEndTime = null;
        scareStartTime = null;
        activeImage = null;
        // Release all decoded frames so a disabled plugin pins no heap; the
        // generation bumps also invalidate any load still in flight.
        imageLoadGen.incrementAndGet();
        soundLoadGen.incrementAndGet();
        bundledScary = null;
        bundledHappy = null;
        customImage = null;
        customSoundBytes = null;
        customImageStatus = null;
        customSoundStatus = null;
        customImageStamp = 0;
        customSoundStamp = 0;
    }

    /**
     * One-time repair of settings that earlier releases left in a state the
     * user cannot have intended, gated so it never overrides a later choice.
     */
    private void migrateOnce()
    {
        if (Boolean.parseBoolean(configManager.getConfiguration(CONFIG_GROUP, MIGRATION_KEY)))
        {
            return;
        }
        configManager.setConfiguration(CONFIG_GROUP, MIGRATION_KEY, true);
        migrateStaleChanceDefault();
        migrateOversizeDuration();
        migrateAssetSources();

        // Last, so the migrations above can still read what they retire.
        for (String dead : DEAD_KEYS)
        {
            configManager.unsetConfiguration(CONFIG_GROUP, dead);
        }
    }

    /**
     * Bring a duration above the cap back in range. v1.0 and v1.1 shipped no
     * upper bound, so an early user can still hold a value the plugin has
     * clamped at trigger time ever since v1.3 added one — the panel promises
     * a length they never actually get. Writing the cap changes no behaviour,
     * it just makes the setting say what already happens.
     */
    private void migrateOversizeDuration()
    {
        if (config.durationMs() > MAX_DURATION_MS)
        {
            configManager.setConfiguration(CONFIG_GROUP, DURATION_KEY, MAX_DURATION_MS);
        }
    }

    /**
     * Move a chance still sitting on a default shipped by an earlier release
     * onto the current default. Unsetting the key alone is not enough: the
     * framework only re-persists a default at client start, before this
     * migration runs, so within the update session the key stays null — and
     * the config panel renders an unset int as its range minimum (1). So
     * unset to read today's default through the proxy, then write it back
     * explicitly, leaving the panel correct immediately.
     */
    private void migrateStaleChanceDefault()
    {
        int chance = config.chanceDenominator();
        for (int stale : STALE_CHANCE_DEFAULTS)
        {
            if (chance == stale)
            {
                configManager.unsetConfiguration(CONFIG_GROUP, CHANCE_KEY);
                configManager.setConfiguration(CONFIG_GROUP, CHANCE_KEY, config.chanceDenominator());
                return;
            }
        }
    }

    /**
     * v1.2 replaced the Theme dropdown and always-on custom files with
     * per-asset source dropdowns left at Default, silently dropping the
     * happy theme and custom files of everyone who installed before it.
     * The tell is a configured file name whose source is still Default:
     * that name does nothing, so it cannot be what the user wanted. A
     * pre-v1.2 Happy theme is restored the same way. Sources the user has
     * since pointed somewhere themselves are left alone.
     */
    private void migrateAssetSources()
    {
        boolean wasHappy = "HAPPY".equals(configManager.getConfiguration(CONFIG_GROUP, OLD_THEME_KEY));

        if (config.imageSource() == AssetSource.DEFAULT)
        {
            if (wasHappy)
            {
                configManager.setConfiguration(CONFIG_GROUP, IMAGE_SOURCE_KEY, AssetSource.HAPPY.name());
            }
            else if (!trimmed(config.customImageFile()).isEmpty())
            {
                configManager.setConfiguration(CONFIG_GROUP, IMAGE_SOURCE_KEY, AssetSource.CUSTOM.name());
            }
        }
        if (config.soundSource() == AssetSource.DEFAULT)
        {
            if (wasHappy)
            {
                configManager.setConfiguration(CONFIG_GROUP, SOUND_SOURCE_KEY, AssetSource.HAPPY.name());
            }
            else if (!trimmed(config.customSoundFile()).isEmpty())
            {
                configManager.setConfiguration(CONFIG_GROUP, SOUND_SOURCE_KEY, AssetSource.CUSTOM.name());
            }
        }
    }

    @Provides
    JumpscareConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(JumpscareConfig.class);
    }

    /**
     * One-time post-update notice: on the first logged-in tick after the
     * plugin version changes, summarise the update in chat. The version is
     * recorded immediately, so it prints once after an update and not on
     * later restarts or plugin toggles. Fresh installs (and updates from
     * versions predating this notice) record the version silently.
     */
    private void maybeAnnounceUpdate()
    {
        if (updateChecked)
        {
            return;
        }
        updateChecked = true;

        String lastSeen = configManager.getConfiguration(CONFIG_GROUP, LAST_SEEN_VERSION_KEY);
        if (VERSION.equals(lastSeen))
        {
            return;
        }
        configManager.setConfiguration(CONFIG_GROUP, LAST_SEEN_VERSION_KEY, VERSION);
        if (lastSeen == null || lastSeen.isEmpty())
        {
            return;
        }

        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.CONSOLE)
            .runeLiteFormattedMessage(new ChatMessageBuilder()
                .append(UPDATE_MESSAGE_COLOR, UPDATE_MESSAGE)
                .build())
            .build());
    }

    /**
     * Confirm enabling flash mode with an epilepsy warning, reverting the
     * toggle if declined. Done here rather than via the ConfigItem warning
     * attribute because that fires on every change — disabling flash again
     * must not prompt.
     */
    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!CONFIG_GROUP.equals(event.getGroup()))
        {
            return;
        }

        if (FLASH_MODE_KEY.equals(event.getKey()) && Boolean.parseBoolean(event.getNewValue()))
        {
            confirmFlashMode();
        }
        else if (CUSTOM_IMAGE_KEY.equals(event.getKey()))
        {
            reloadCustomImage();
        }
        else if (CUSTOM_SOUND_KEY.equals(event.getKey()))
        {
            reloadCustomSound();
        }
    }

    /**
     * (Re)load the configured custom image on the executor, publishing into
     * {@link #customImage}. Runs off the client thread so neither game ticks
     * nor config edits ever wait on disk or GIF decoding.
     */
    private void reloadCustomImage()
    {
        int gen = imageLoadGen.incrementAndGet();
        String configured = config.customImageFile();
        String name = configured == null ? "" : configured.trim();
        executor.execute(() ->
        {
            AnimatedImage loaded = null;
            String status = null;
            long stamp = 0;
            if (!name.isEmpty())
            {
                stamp = stampOf(name);
                try
                {
                    File imageFile = resolvePluginFile(name);
                    if (imageFile == null || !imageFile.isFile())
                    {
                        status = "file not found in " + PLUGIN_DIR;
                    }
                    else
                    {
                        loaded = AnimatedImage.load(imageFile, MAX_ANIMATED_DIMENSION, 0);
                        status = loaded != null ? "loaded"
                            : "not a supported image format (PNG, JPG, GIF, BMP)";
                    }
                    if (loaded == null)
                    {
                        log.warn("Could not load custom image from {}, the default will be used: {}", PLUGIN_DIR, name);
                    }
                }
                catch (IOException e)
                {
                    status = "could not be read";
                    log.warn("Failed to read custom image, the default will be used: {}", name, e);
                }
            }
            if (gen == imageLoadGen.get())
            {
                customImage = loaded;
                customImageStatus = status;
                customImageStamp = stamp;
            }
        });
    }

    /**
     * (Re)load the configured custom WAV's raw bytes on the executor,
     * publishing into {@link #customSoundBytes}. Raw rather than
     * volume-scaled so the cache survives volume changes; scaling happens
     * in memory per trigger.
     */
    private void reloadCustomSound()
    {
        int gen = soundLoadGen.incrementAndGet();
        String configured = config.customSoundFile();
        String name = configured == null ? "" : configured.trim();
        executor.execute(() ->
        {
            byte[] loaded = null;
            String status = null;
            long stamp = 0;
            if (!name.isEmpty())
            {
                stamp = stampOf(name);
                try
                {
                    File soundFile = resolvePluginFile(name);
                    if (soundFile != null && soundFile.isFile())
                    {
                        loaded = Files.readAllBytes(soundFile.toPath());
                        status = "loaded";
                    }
                    else
                    {
                        status = "file not found in " + PLUGIN_DIR;
                        log.warn("Custom sound file not found in {}, the default scream will be used: {}", PLUGIN_DIR, name);
                    }
                }
                catch (IOException e)
                {
                    status = "could not be read";
                    log.warn("Failed to read custom sound, the default scream will be used: {}", name, e);
                }
            }
            if (gen == soundLoadGen.get())
            {
                customSoundBytes = loaded;
                customSoundStatus = status;
                customSoundStamp = stamp;
            }
        });
    }

    /**
     * Re-stat the configured custom files and reload any whose file changed
     * (or appeared) since the last load attempt. Runs on the executor per
     * trigger — the trigger path itself never touches the disk, and an
     * unchanged broken file is not re-decoded every scare.
     */
    private void refreshCustomAssetsIfChanged()
    {
        String imageName = trimmed(config.customImageFile());
        if (!imageName.isEmpty() && stampOf(imageName) != customImageStamp)
        {
            reloadCustomImage();
        }
        String soundName = trimmed(config.customSoundFile());
        if (!soundName.isEmpty() && stampOf(soundName) != customSoundStamp)
        {
            reloadCustomSound();
        }
    }

    /**
     * lastModified/length fingerprint of a plugin-folder file; -1 when it
     * is missing. Collisions only delay a reload until the next config
     * change or plugin toggle, so mixing the two values is good enough.
     */
    private static long stampOf(String name)
    {
        File file = resolvePluginFile(name);
        if (file == null || !file.isFile())
        {
            return -1;
        }
        return file.lastModified() ^ (file.length() << 20);
    }

    private static String trimmed(String value)
    {
        return value == null ? "" : value.trim();
    }

    /**
     * Show the epilepsy warning: accepting records the acknowledgement,
     * declining switches flash mode back off.
     */
    private void confirmFlashMode()
    {
        SwingUtilities.invokeLater(() ->
        {
            // Parented to the client canvas so the dialog opens on the client
            // window instead of centred on the monitor (or behind the client).
            int choice = JOptionPane.showConfirmDialog(client.getCanvas(),
                "Flash mode rapidly flashes bright colours, which can trigger seizures\n"
                    + "in people with photosensitive epilepsy.\n\nEnable flash mode anyway?",
                "Epilepsy warning",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION)
            {
                configManager.setConfiguration(CONFIG_GROUP, FLASH_ACK_KEY, true);
            }
            else
            {
                configManager.setConfiguration(CONFIG_GROUP, FLASH_MODE_KEY, false);
            }
        });
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        maybeAnnounceUpdate();

        // Don't stack scares while one is still showing.
        if (isActive())
        {
            return;
        }

        int denominator = Math.max(1, config.chanceDenominator());
        if (random.nextInt(denominator) == 0)
        {
            triggerJumpscare(null);
        }
    }

    @Subscribe
    public void onCommandExecuted(CommandExecuted event)
    {
        if (!"stest".equals(event.getCommand()))
        {
            return;
        }

        // Plain ::stest previews the configured image/sound sources; an
        // argument forces the full bundled scary or happy set instead.
        JumpscareTheme forced = null;
        String[] args = event.getArguments();
        if (args != null && args.length > 0)
        {
            if ("happy".equalsIgnoreCase(args[0]) || "h".equalsIgnoreCase(args[0]))
            {
                forced = JumpscareTheme.HAPPY;
            }
            else if ("scary".equalsIgnoreCase(args[0]) || "s".equalsIgnoreCase(args[0]))
            {
                forced = JumpscareTheme.SCARY;
            }
        }
        triggerJumpscare(forced);
        if (forced == null)
        {
            reportTestStatus();
        }
    }

    /**
     * Chat summary of what plain ::stest just used, so users can see why a
     * custom file isn't playing without reading client.log. Only for the
     * plain form — the forced variants always use the bundled sets.
     */
    private void reportTestStatus()
    {
        String sound;
        if (!config.soundEnabled())
        {
            sound = "off (Play sound unticked)";
        }
        else if (config.volume() <= 0)
        {
            sound = "muted (volume 0)";
        }
        else
        {
            sound = describeSource(config.soundSource(), config.customSoundFile(), customSoundStatus);
        }

        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.CONSOLE)
            .runeLiteFormattedMessage(new ChatMessageBuilder()
                .append("Jumpscare test — image: ")
                .append(describeSource(config.imageSource(), config.customImageFile(), customImageStatus))
                .append(", sound: ")
                .append(sound)
                .build())
            .build());
    }

    private static String describeSource(AssetSource source, String file, String status)
    {
        if (source != AssetSource.CUSTOM)
        {
            return source.toString();
        }
        String name = trimmed(file);
        if (name.isEmpty())
        {
            return "Custom, but no file name is set (using Default)";
        }
        if (status == null)
        {
            return "Custom (" + name + ": still loading, run ::stest again)";
        }
        if ("loaded".equals(status))
        {
            return "Custom (" + name + ")";
        }
        return "Custom (" + name + ": " + status + " — using Default)";
    }

    /**
     * Fire a jumpscare now: resolve the image to show, arm the timing window, and
     * play the sound. Safe to call from any client-thread event handler; never throws.
     *
     * @param forced force the full bundled scary/happy set (::stest args);
     *               null uses the configured image and sound sources.
     */
    void triggerJumpscare(JumpscareTheme forced)
    {
        executor.execute(this::refreshCustomAssetsIfChanged);
        int duration = Math.min(MAX_DURATION_MS, Math.max(1, config.durationMs()));
        activeTheme = forced != null ? forced
            : (config.imageSource() == AssetSource.HAPPY
                ? JumpscareTheme.HAPPY : JumpscareTheme.SCARY);
        activeImage = resolveImage(forced);
        scareStartTime = Instant.now();
        scareEndTime = scareStartTime.plusMillis(duration);

        if (config.soundEnabled())
        {
            playScream(forced);
        }
    }

    private void playScream(JumpscareTheme forced)
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
            byte[] wav = loadScreamBytes(forced);
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

    private byte[] loadScreamBytes(JumpscareTheme forced) throws IOException
    {
        AssetSource source = forced == JumpscareTheme.HAPPY ? AssetSource.HAPPY
            : forced == JumpscareTheme.SCARY ? AssetSource.DEFAULT
            : config.soundSource();

        if (source == AssetSource.CUSTOM)
        {
            // Preloaded by reloadCustomSound(); null (unset/unloadable, already
            // logged at load time) falls through to the bundled scream.
            byte[] custom = customSoundBytes;
            if (custom != null)
            {
                return custom;
            }
        }

        String resource = source == AssetSource.HAPPY ? "happy.wav" : "scream.wav";
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
     * Resolve which image to draw for this trigger from the configured source
     * (or the forced bundled set for ::stest args). Custom falls back to
     * the default image when it was missing or unreadable at preload time.
     */
    private AnimatedImage resolveImage(JumpscareTheme forced)
    {
        AssetSource source = forced == JumpscareTheme.HAPPY ? AssetSource.HAPPY
            : forced == JumpscareTheme.SCARY ? AssetSource.DEFAULT
            : config.imageSource();

        if (source == AssetSource.HAPPY)
        {
            return bundledHappy;
        }

        if (source == AssetSource.CUSTOM)
        {
            AnimatedImage custom = customImage;
            if (custom != null)
            {
                return custom;
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
