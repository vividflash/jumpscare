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
import net.runelite.client.config.Units;

@ConfigGroup("jumpscare")
public interface JumpscareConfig extends Config
{
    @ConfigItem(
        keyName = "testMode",
        name = "Test Mode",
        description = "Try a scare without the ::stest command. Fires one right away, then at 1 in 10 with sound. Turns off on restart.",
        position = -1
    )
    default boolean testMode()
    {
        return false;
    }

    // ------------------------------------------------------------------
    // General
    // ------------------------------------------------------------------

    @ConfigSection(
        name = "General",
        description = "",
        position = 0
    )
    String generalSection = "general";

    @ConfigItem(
        keyName = "chanceDenominator",
        name = "Chance (1 in N)",
        description = "Rolled every game tick (0.6s).",
        section = generalSection,
        position = 0
    )
    @Range(min = 1)
    default int chanceDenominator()
    {
        return 6000;
    }

    @ConfigItem(
        keyName = "durationMs",
        name = "Duration",
        description = "",
        section = generalSection,
        position = 1
    )
    @Range(min = 1, max = 10000)
    @Units(Units.MILLISECONDS)
    default int durationMs()
    {
        return 1000;
    }

    // ------------------------------------------------------------------
    // Flash mode
    // ------------------------------------------------------------------

    @ConfigSection(
        name = "Flash mode",
        description = "Epilepsy warning: flash mode rapidly flashes bright colours, which can trigger seizures in photosensitive people.",
        position = 1,
        closedByDefault = true
    )
    String flashSection = "flash";

    @ConfigItem(
        keyName = "flashMode",
        name = "Enable flash (epilepsy warning)",
        description = "Replaces the scare image with rapidly flashing colours.<br><br>"
            + "Epilepsy warning: rapid flashing can trigger seizures in photosensitive people."
            + "<br><br>Asks for confirmation. If you decline, reopen these settings to "
            + "see the box unticked.",
        section = flashSection,
        position = 0
    )
    default boolean flashMode()
    {
        return false;
    }

    // ------------------------------------------------------------------
    // Appearance
    // ------------------------------------------------------------------

    @ConfigSection(
        name = "Appearance",
        description = "",
        position = 2
    )
    String appearanceSection = "appearance";

    @ConfigItem(
        keyName = "imageSource",
        name = "Image",
        description = "Default is a creepy face, Happy a friendly sun. Custom falls back to Default if the file can't be loaded.",
        section = appearanceSection,
        position = 0
    )
    default AssetSource imageSource()
    {
        return AssetSource.DEFAULT;
    }

    @ConfigItem(
        keyName = "customImagePath",
        name = "Custom image file",
        description = "File name of an image in .runelite/plugin-data/jumpscare. PNG, JPG, GIF or BMP; animated GIFs play.",
        section = appearanceSection,
        position = 1
    )
    default String customImageFile()
    {
        return "";
    }

    // ------------------------------------------------------------------
    // Sound
    // ------------------------------------------------------------------

    @ConfigSection(
        name = "Sound",
        description = "",
        position = 3
    )
    String soundSection = "sound";

    @ConfigItem(
        keyName = "soundEnabled",
        name = "Play sound",
        description = "",
        section = soundSection,
        position = 0
    )
    default boolean soundEnabled()
    {
        return true;
    }

    @ConfigItem(
        keyName = "soundSource",
        name = "Sound",
        description = "Default is a scream, Happy a cheerful jingle. Custom falls back to Default if the file can't be loaded.",
        section = soundSection,
        position = 1
    )
    default AssetSource soundSource()
    {
        return AssetSource.DEFAULT;
    }

    @ConfigItem(
        keyName = "volume",
        name = "Volume",
        description = "Independent of the in-game volume sliders.",
        section = soundSection,
        position = 2
    )
    @Range(min = 0, max = 100)
    @Units(Units.PERCENT)
    default int volume()
    {
        return 50;
    }

    @ConfigItem(
        keyName = "customSoundPath",
        name = "Custom sound file",
        description = "File name of a WAV in .runelite/plugin-data/jumpscare.",
        section = soundSection,
        position = 3
    )
    default String customSoundFile()
    {
        return "";
    }
}
