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
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class AnimatedImageTest
{
    private static final int RED = 0xFF0000;
    private static final int GREEN = 0x00FF00;
    private static final int BLUE = 0x0000FF;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void staticImageLoadsAsSingleFrame() throws IOException
    {
        File file = folder.newFile("static.png");
        ImageIO.write(solidFrame(RED, 10), "png", file);

        AnimatedImage image = AnimatedImage.load(file, 0, 0);
        assertNotNull(image);
        assertEquals(10, image.getWidth());
        assertEquals(10, image.getHeight());
        // A static image returns its only frame for any elapsed time.
        assertSame(image.frameAt(0), image.frameAt(123456));
        assertEquals(RED, rgbAt(image.frameAt(0)));
    }

    @Test
    public void animatedGifPlaysFramesByElapsedTime() throws IOException
    {
        // Delays: 50ms, 200ms, and 0 (which browsers and this decoder clamp
        // to 100ms) -> cumulative frame end times 50, 250, 350.
        File file = folder.newFile("anim.gif");
        writeGif(file, 10, new int[]{RED, GREEN, BLUE}, new int[]{5, 20, 0});

        AnimatedImage image = AnimatedImage.load(file, 0, 0);
        assertNotNull(image);
        assertEquals(10, image.getWidth());

        assertEquals(RED, rgbAt(image.frameAt(0)));
        assertEquals(RED, rgbAt(image.frameAt(49)));
        assertEquals(GREEN, rgbAt(image.frameAt(50)));
        assertEquals(GREEN, rgbAt(image.frameAt(249)));
        assertEquals(BLUE, rgbAt(image.frameAt(250)));
        assertEquals(BLUE, rgbAt(image.frameAt(349)));
        // The animation loops: 350ms wraps back to the first frame.
        assertEquals(RED, rgbAt(image.frameAt(350)));
        assertEquals(RED, rgbAt(image.frameAt(360)));
    }

    @Test
    public void animatedFramesAreDownscaled() throws IOException
    {
        File file = folder.newFile("big.gif");
        writeGif(file, 10, new int[]{RED, GREEN}, new int[]{10, 10});

        AnimatedImage image = AnimatedImage.load(file, 5, 0);
        assertNotNull(image);
        assertEquals(5, image.getWidth());
        assertEquals(5, image.getHeight());
        assertEquals(RED, rgbAt(image.frameAt(0)));
    }

    private static BufferedImage solidFrame(int rgb, int size)
    {
        BufferedImage frame = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = frame.createGraphics();
        g.setColor(new Color(rgb));
        g.fillRect(0, 0, size, size);
        g.dispose();
        return frame;
    }

    private static int rgbAt(BufferedImage frame)
    {
        return frame.getRGB(frame.getWidth() / 2, frame.getHeight() / 2) & 0xFFFFFF;
    }

    /**
     * Write an animated GIF with one solid-colour frame per entry, delays in
     * hundredths of a second (raw GIF units).
     */
    private static void writeGif(File file, int size, int[] colors, int[] delaysCs) throws IOException
    {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file))
        {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < colors.length; i++)
            {
                BufferedImage frame = solidFrame(colors[i], size);
                ImageWriteParam param = writer.getDefaultWriteParam();
                IIOMetadata metadata = writer.getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromRenderedImage(frame), param);
                String format = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                IIOMetadataNode control = new IIOMetadataNode("GraphicControlExtension");
                control.setAttribute("disposalMethod", "none");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", String.valueOf(delaysCs[i]));
                control.setAttribute("transparentColorIndex", "0");
                root.appendChild(control);
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(frame, null, metadata), param);
            }
            writer.endWriteSequence();
        }
        finally
        {
            writer.dispose();
        }
    }
}
