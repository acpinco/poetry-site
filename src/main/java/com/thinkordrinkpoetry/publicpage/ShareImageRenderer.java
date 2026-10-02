package com.thinkordrinkpoetry.publicpage;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Draws the preview images (Open Graph {@code og:image}) that Facebook, LinkedIn, and other sites
 * show when someone shares a link. The fonts are bundled so the images look the same on any server.
 */
@Component
class ShareImageRenderer {
    static final int WIDTH = 1200;
    static final int HEIGHT = 630;

    private static final int MARGIN = 80;
    private static final int TEXT_WIDTH = WIDTH - 2 * MARGIN;
    private static final int FOOTER_TOP = HEIGHT - 120;

    // The site palette from frontend/src/index.css.
    private static final Color NIGHT = new Color(0x080a0f);
    private static final Color TWILIGHT = new Color(0x12102a);
    private static final Color GOLD = new Color(0xc9a84c);
    private static final Color CREAM = new Color(0xe4ddd0);
    private static final Color PARCHMENT = new Color(0xc8c0b0);
    private static final Color MUTED = new Color(0x8b8992);
    private static final Color LINE = new Color(0x2a2840);

    private final Font display;
    private final Font body;
    private final Font italic;
    private final String siteHost;

    ShareImageRenderer(PublicLinks links) {
        display = font("PlayfairDisplay-Bold.ttf");
        body = font("Lora-Regular.ttf");
        italic = font("Lora-Italic.ttf");
        siteHost = URI.create(links.site()).getHost();
    }

    /** A poem's title, poet, and opening lines, keeping the poem's own line breaks. */
    byte[] poem(String title, String poetName, String poem) {
        return render(g -> {
            int y = heading(g, "A poem by " + poetName, title);
            Font font = italic.deriveFont(28f);
            List<String> lines = new ArrayList<>();
            for (String line : poem.strip().split("\\R")) {
                String trimmed = line.strip();
                // Keep a single blank line between stanzas, never a run of them.
                if (!trimmed.isEmpty() || (!lines.isEmpty() && !lines.getLast().isEmpty())) {
                    lines.add(trimmed);
                }
            }
            drawLines(g, lines, font, PARCHMENT, y + 64, 42);
        });
    }

    /** A poet's name, poem count, and the start of their bio. */
    byte[] poet(String name, int poemCount, String bio) {
        return render(g -> {
            int y = heading(g, poemCount == 1 ? "Poet · 1 poem" : "Poet · " + poemCount + " poems", name);
            String text = bio == null || bio.isBlank() ? "Read poems by " + name + " on Think or Drink Poetry." : bio;
            Font font = italic.deriveFont(28f);
            drawLines(g, wrap(g, text.replaceAll("\\s+", " ").strip(), font, 99), font, PARCHMENT, y + 64, 42);
        });
    }

    /** The site's own card, for pages that are not about one poem or poet. */
    byte[] site() {
        return render(g -> {
            int y = heading(
                    g, "Original poetry from independent voices", "Original poems, shared in shadow and light.");
            Font font = italic.deriveFont(30f);
            String text = "Read a poem, find a poet, and share your own.";
            drawLines(g, wrap(g, text, font, 2), font, PARCHMENT, y + 70, 44);
        });
    }

    private byte[] render(Consumer<Graphics2D> content) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setPaint(new GradientPaint(0, 0, NIGHT, WIDTH, HEIGHT, TWILIGHT));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(LINE);
            g.drawRect(24, 24, WIDTH - 49, HEIGHT - 49);
            content.accept(g);
            footer(g);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", png);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return png.toByteArray();
    }

    /** Draws the small gold label, the title (up to two lines), and a short rule; returns the rule's y. */
    private int heading(Graphics2D g, String label, String title) {
        Font labelFont = body.deriveFont(Map.of(TextAttribute.SIZE, 22f, TextAttribute.TRACKING, 0.12f));
        g.setFont(labelFont);
        g.setColor(GOLD);
        g.drawString(fit(g.getFontMetrics(), label.toUpperCase(Locale.ROOT), TEXT_WIDTH), MARGIN, 118);

        Font titleFont = display.deriveFont(62f);
        List<String> titleLines = wrap(g, title, titleFont, 2);
        int y = drawLines(g, titleLines, titleFont, CREAM, 200, 76);

        g.setColor(GOLD);
        g.fillRect(MARGIN, y - 36, 72, 3);
        return y - 36;
    }

    private void footer(Graphics2D g) {
        g.setColor(LINE);
        g.drawLine(MARGIN, FOOTER_TOP, WIDTH - MARGIN, FOOTER_TOP);
        g.setFont(display.deriveFont(28f));
        g.setColor(GOLD);
        g.drawString("Think or Drink Poetry", MARGIN, HEIGHT - 66);
        if (siteHost != null) {
            g.setFont(body.deriveFont(22f));
            g.setColor(MUTED);
            int width = g.getFontMetrics().stringWidth(siteHost);
            g.drawString(siteHost, WIDTH - MARGIN - width, HEIGHT - 68);
        }
    }

    /**
     * Draws lines from {@code baseline} down until the footer, shortening any too-wide line. When
     * lines are left over, the last one drawn ends in an ellipsis. Returns the next free baseline.
     */
    private static int drawLines(Graphics2D g, List<String> lines, Font font, Color color, int baseline, int step) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics metrics = g.getFontMetrics();
        int bottom = FOOTER_TOP - 36;
        int y = baseline;
        for (int i = 0; i < lines.size() && y <= bottom; i++) {
            boolean lastThatFits = y + step > bottom && i < lines.size() - 1;
            String line = lastThatFits ? withEllipsis(metrics, lines.get(i)) : fit(metrics, lines.get(i), TEXT_WIDTH);
            g.drawString(line, MARGIN, y);
            y += line.isEmpty() ? step / 2 : step;
        }
        return y;
    }

    /** Word-wraps text to the text width, ending in an ellipsis if it needs more than {@code maxLines}. */
    private static List<String> wrap(Graphics2D g, String text, Font font, int maxLines) {
        FontMetrics metrics = g.getFontMetrics(font);
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.strip().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (line.isEmpty() || metrics.stringWidth(candidate) <= TEXT_WIDTH) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (lines.size() == maxLines - 1) {
                lines.add(withEllipsis(metrics, line.toString()));
                return lines;
            }
            lines.add(fit(metrics, line.toString(), TEXT_WIDTH));
            line.setLength(0);
            line.append(word);
        }
        lines.add(fit(metrics, line.toString(), TEXT_WIDTH));
        return lines;
    }

    /** The text, shortened with an ellipsis if it is wider than {@code width}. */
    private static String fit(FontMetrics metrics, String text, int width) {
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        String shortened = text;
        while (!shortened.isEmpty() && metrics.stringWidth(shortened + "…") > width) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened.stripTrailing() + "…";
    }

    /** The text with an ellipsis added, shortened if needed so it still fits. */
    private static String withEllipsis(FontMetrics metrics, String text) {
        String stripped = text.replaceAll("[\\s.,;:!?…-]+$", "");
        return fit(metrics, stripped + "…", TEXT_WIDTH);
    }

    private static Font font(String file) {
        try (InputStream in = ShareImageRenderer.class.getResourceAsStream("/fonts/" + file)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled font " + file);
            }
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (FontFormatException e) {
            throw new IllegalStateException("Unreadable bundled font " + file, e);
        }
    }
}
