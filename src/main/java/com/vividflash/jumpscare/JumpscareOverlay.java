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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
public class JumpscareOverlay extends Overlay
{
    private static final Color[] FLASH_COLORS = {Color.RED, Color.WHITE, Color.BLACK};
    private static final long FLASH_INTERVAL_MS = 100L;

    private final Client client;
    private final JumpscarePlugin plugin;
    private final JumpscareConfig config;

    @Inject
    JumpscareOverlay(Client client, JumpscarePlugin plugin, JumpscareConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(Overlay.PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.isActive())
        {
            return null;
        }

        if (client.getCanvas() == null)
        {
            return null;
        }

        Dimension canvas = client.getCanvas().getSize();
        int width = canvas.width;
        int height = canvas.height;
        if (width <= 0 || height <= 0)
        {
            return null;
        }

        if (config.mode() == JumpscareMode.FLASH)
        {
            renderFlash(graphics, width, height);
        }
        else
        {
            renderImage(graphics, width, height);
        }

        return new Dimension(width, height);
    }

    private void renderImage(Graphics2D graphics, int width, int height)
    {
        BufferedImage image = plugin.getActiveImage();
        if (image == null)
        {
            // Fallback so the scare is still visible even if no image loaded.
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, width, height);
            return;
        }
        graphics.drawImage(image, 0, 0, width, height, null);
    }

    private void renderFlash(Graphics2D graphics, int width, int height)
    {
        Instant start = plugin.getScareStartTime();
        long elapsed = start == null ? 0L : Duration.between(start, Instant.now()).toMillis();
        int index = (int) ((elapsed / FLASH_INTERVAL_MS) % FLASH_COLORS.length);
        graphics.setColor(FLASH_COLORS[index]);
        graphics.fillRect(0, 0, width, height);
    }
}
