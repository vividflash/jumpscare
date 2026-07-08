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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("jumpscare")
public interface JumpscareConfig extends Config
{
    // ------------------------------------------------------------------
    // General
    // ------------------------------------------------------------------

    @ConfigSection(
        name = "General",
        description = "Odds, duration and mode of the jumpscare",
        position = 0
    )
    String generalSection = "general";

    @ConfigItem(
        keyName = "chanceDenominator",
        name = "Chance (1 in N)",
        description = "A 1 in N chance to trigger per game tick (a tick is 0.6s). Higher = rarer. Default 100000 is roughly one scare per ~16.7 hours of play.",
        section = generalSection,
        position = 0
    )
    @Range(min = 1)
    default int chanceDenominator()
    {
        return 100000;
    }

    @ConfigItem(
        keyName = "durationMs",
        name = "Duration (ms)",
        description = "How long the jumpscare stays on screen, in milliseconds",
        section = generalSection,
        position = 1
    )
    @Range(min = 1)
    default int durationMs()
    {
        return 1000;
    }

    @ConfigItem(
        keyName = "mode",
        name = "Mode",
        description = "Image shows a full-screen scare picture. Flash rapidly flashes colours (photosensitivity warning).",
        section = generalSection,
        position = 2
    )
    default JumpscareMode mode()
    {
        return JumpscareMode.IMAGE;
    }

    // ------------------------------------------------------------------
    // Appearance
    // ------------------------------------------------------------------

    @ConfigSection(
        name = "Appearance",
        description = "Custom image for the jumpscare",
        position = 1
    )
    String appearanceSection = "appearance";

    @ConfigItem(
        keyName = "customImagePath",
        name = "Custom image path",
        description = "Absolute path to a custom PNG/JPG image to show instead of the bundled one. Leave blank to use the bundled scare image.",
        section = appearanceSection,
        position = 0
    )
    default String customImagePath()
    {
        return "";
    }

    // ------------------------------------------------------------------
    // Sound
    // ------------------------------------------------------------------

    @ConfigSection(
        name = "Sound",
        description = "Scream sound settings",
        position = 2
    )
    String soundSection = "sound";

    @ConfigItem(
        keyName = "soundEnabled",
        name = "Play sound",
        description = "Play a scream sound when the jumpscare triggers",
        section = soundSection,
        position = 0
    )
    default boolean soundEnabled()
    {
        return true;
    }

    @ConfigItem(
        keyName = "volume",
        name = "Volume",
        description = "Playback volume (0-100). Plays through the client audio subsystem, independent of the in-game music/sound-effect sliders.",
        section = soundSection,
        position = 1
    )
    @Range(min = 0, max = 100)
    default int volume()
    {
        return 80;
    }

    @ConfigItem(
        keyName = "customSoundPath",
        name = "Custom sound path",
        description = "Absolute path to a custom WAV file (WAV only) to play instead of the bundled scream. Leave blank to use the bundled sound.",
        section = soundSection,
        position = 2
    )
    default String customSoundPath()
    {
        return "";
    }
}
