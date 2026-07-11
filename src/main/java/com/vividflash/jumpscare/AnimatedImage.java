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

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * An image with one or more frames. Static formats (PNG, JPG, BMP, ...) load as
 * a single frame; animated GIFs are composited into full frames with per-frame
 * delays so a renderer can pick the frame for any elapsed time. Decoding uses
 * only the JRE's ImageIO — no third-party codecs.
 */
@Slf4j
final class AnimatedImage
{
    /**
     * Hard caps so a pathological GIF cannot eat the client's heap: decoded
     * frames are uncompressed ARGB (4 bytes per pixel), so a long high-res
     * animation multiplies out quickly. Decoding stops at whichever cap is
     * hit first and the animation simply loops over the frames kept.
     */
    private static final int MAX_FRAMES = 20;
    private static final long MAX_TOTAL_BYTES = 24L * 1024 * 1024;

    /**
     * GIFs commonly declare 0 delay; browsers render those at ~100 ms per
     * frame, so match that rather than spinning through frames instantly.
     */
    private static final int MIN_DELAY_MS = 20;
    private static final int DEFAULT_DELAY_MS = 100;

    private final BufferedImage[] frames;
    /** Cumulative delay up to and including frame i, for elapsed-time lookup. */
    private final int[] frameEndTimesMs;
    private final int totalDurationMs;

    private AnimatedImage(List<BufferedImage> frameList, List<Integer> delaysMs)
    {
        frames = frameList.toArray(new BufferedImage[0]);
        frameEndTimesMs = new int[frames.length];
        int total = 0;
        for (int i = 0; i < frames.length; i++)
        {
            total += delaysMs.get(i);
            frameEndTimesMs[i] = total;
        }
        totalDurationMs = total;
    }

    private AnimatedImage(BufferedImage single)
    {
        frames = new BufferedImage[]{single};
        frameEndTimesMs = new int[]{0};
        totalDurationMs = 0;
    }

    /**
     * Wrap an already-decoded static image as a single-frame animation.
     */
    static AnimatedImage of(BufferedImage image)
    {
        return image == null ? null : new AnimatedImage(image);
    }

    /**
     * Load an image file, decoding all frames if it is an animated GIF.
     * Animated frames are downscaled to {@code maxAnimDimension} on their
     * longest side, static images to {@code maxStaticDimension}; pass 0 to
     * leave the respective size untouched. Returns null when no ImageIO
     * reader recognises the file.
     */
    static AnimatedImage load(File file, int maxAnimDimension, int maxStaticDimension) throws IOException
    {
        try (ImageInputStream input = ImageIO.createImageInputStream(file))
        {
            if (input == null)
            {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext())
            {
                return null;
            }
            ImageReader reader = readers.next();
            try
            {
                reader.setInput(input);
                int frameCount = "gif".equalsIgnoreCase(reader.getFormatName())
                    ? reader.getNumImages(true) : 1;
                if (frameCount <= 1)
                {
                    return of(downscale(reader.read(0), maxStaticDimension));
                }
                return decodeGif(reader, frameCount, maxAnimDimension);
            }
            finally
            {
                reader.dispose();
            }
        }
    }

    /**
     * The frame to show {@code elapsedMs} after the animation started; the
     * animation loops. Static images always return their only frame.
     */
    BufferedImage frameAt(long elapsedMs)
    {
        if (frames.length == 1 || totalDurationMs <= 0)
        {
            return frames[0];
        }
        long t = elapsedMs % totalDurationMs;
        if (t < 0)
        {
            t += totalDurationMs;
        }
        for (int i = 0; i < frames.length; i++)
        {
            if (t < frameEndTimesMs[i])
            {
                return frames[i];
            }
        }
        return frames[frames.length - 1];
    }

    int getWidth()
    {
        return frames[0].getWidth();
    }

    int getHeight()
    {
        return frames[0].getHeight();
    }

    /**
     * Composite a multi-frame GIF into standalone full frames. GIF frames are
     * often partial diffs positioned inside a logical screen, so each frame is
     * drawn onto a persistent canvas and the canvas snapshotted, honouring the
     * frame's disposal method before the next one.
     */
    private static AnimatedImage decodeGif(ImageReader reader, int frameCount, int maxDimension)
        throws IOException
    {
        int canvasWidth = 0;
        int canvasHeight = 0;
        IIOMetadata streamMetadata = reader.getStreamMetadata();
        if (streamMetadata != null)
        {
            Node screen = findNode(streamMetadata.getAsTree("javax_imageio_gif_stream_1.0"),
                "LogicalScreenDescriptor");
            canvasWidth = intAttribute(screen, "logicalScreenWidth", 0);
            canvasHeight = intAttribute(screen, "logicalScreenHeight", 0);
        }
        if (canvasWidth <= 0 || canvasHeight <= 0)
        {
            canvasWidth = reader.getWidth(0);
            canvasHeight = reader.getHeight(0);
        }

        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight,
            BufferedImage.TYPE_INT_ARGB);
        List<BufferedImage> frames = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        long totalBytes = 0;

        for (int i = 0; i < frameCount; i++)
        {
            BufferedImage frame = reader.read(i);
            Node tree = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
            Node descriptor = findNode(tree, "ImageDescriptor");
            Node control = findNode(tree, "GraphicControlExtension");
            int x = intAttribute(descriptor, "imageLeftPosition", 0);
            int y = intAttribute(descriptor, "imageTopPosition", 0);
            int delayMs = intAttribute(control, "delayTime", 0) * 10;
            if (delayMs < MIN_DELAY_MS)
            {
                delayMs = DEFAULT_DELAY_MS;
            }
            String disposal = stringAttribute(control, "disposalMethod", "none");

            BufferedImage restore = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;

            Graphics2D g = canvas.createGraphics();
            g.drawImage(frame, x, y, null);
            g.dispose();

            BufferedImage snapshot = snapshot(canvas, maxDimension);
            totalBytes += (long) snapshot.getWidth() * snapshot.getHeight() * 4;
            frames.add(snapshot);
            delays.add(delayMs);

            if (frames.size() >= MAX_FRAMES || totalBytes > MAX_TOTAL_BYTES)
            {
                if (i + 1 < frameCount)
                {
                    log.warn("Animated GIF truncated to {} of {} frames to bound memory use",
                        frames.size(), frameCount);
                }
                break;
            }

            if ("restoreToBackgroundColor".equals(disposal))
            {
                Graphics2D clear = canvas.createGraphics();
                clear.setComposite(AlphaComposite.Clear);
                clear.fillRect(x, y, frame.getWidth(), frame.getHeight());
                clear.dispose();
            }
            else if (restore != null)
            {
                canvas = restore;
            }
        }

        return new AnimatedImage(frames, delays);
    }

    /**
     * An independent copy of the canvas, downscaled to {@code maxDimension}
     * on its longest side (0 = keep size). Always allocates so later canvas
     * mutations cannot bleed into stored frames.
     */
    private static BufferedImage snapshot(BufferedImage canvas, int maxDimension)
    {
        int longest = Math.max(canvas.getWidth(), canvas.getHeight());
        if (maxDimension <= 0 || longest <= maxDimension)
        {
            return copy(canvas);
        }
        double scale = (double) maxDimension / longest;
        int w = Math.max(1, (int) Math.round(canvas.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(canvas.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(canvas, 0, 0, w, h, null);
        g.dispose();
        return scaled;
    }

    private static BufferedImage downscale(BufferedImage image, int maxDimension)
    {
        if (image == null)
        {
            return null;
        }
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (maxDimension <= 0 || longest <= maxDimension)
        {
            return image;
        }
        return snapshot(image, maxDimension);
    }

    private static BufferedImage copy(BufferedImage source)
    {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return out;
    }

    private static Node findNode(Node root, String name)
    {
        for (Node child = root == null ? null : root.getFirstChild();
            child != null; child = child.getNextSibling())
        {
            if (name.equals(child.getNodeName()))
            {
                return child;
            }
        }
        return null;
    }

    private static int intAttribute(Node node, String attribute, int fallback)
    {
        String value = stringAttribute(node, attribute, null);
        if (value == null)
        {
            return fallback;
        }
        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private static String stringAttribute(Node node, String attribute, String fallback)
    {
        if (node == null)
        {
            return fallback;
        }
        NamedNodeMap attributes = node.getAttributes();
        Node value = attributes == null ? null : attributes.getNamedItem(attribute);
        return value == null ? fallback : value.getNodeValue();
    }
}
