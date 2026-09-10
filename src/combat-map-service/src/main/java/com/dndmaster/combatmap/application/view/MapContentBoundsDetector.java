package com.dndmaster.combatmap.application.view;

import java.awt.image.BufferedImage;

/** Finds only border-connected, low-information strips; interior blank areas are content. */
public final class MapContentBoundsDetector {
    public MapContentBounds detect(BufferedImage image) {
        if (image == null) throw new IllegalArgumentException("map image required");
        int w = image.getWidth(), h = image.getHeight();
        if (!hasNonBlackPixel(image)) return new MapContentBounds(0, 0, w, h, 0.0);
        int left = 0, right = w - 1, top = 0, bottom = h - 1;
        while (left < right && lowInformationColumn(image, left)) left++;
        while (right > left && lowInformationColumn(image, right)) right--;
        while (top < bottom && lowInformationRow(image, top)) top++;
        while (bottom > top && lowInformationRow(image, bottom)) bottom--;
        int area = (right - left + 1) * (bottom - top + 1);
        double confidence = area == w * h ? 0.0 : Math.min(1.0, 1.0 - (double) area / (w * h));
        if (confidence < 0.10) return new MapContentBounds(0, 0, w, h, confidence);
        return new MapContentBounds(left, top, right - left + 1, bottom - top + 1, confidence);
    }

    private static boolean lowInformationColumn(BufferedImage image, int x) {
        int dark = 0, transparentOrBlack = 0, varied = 0, samples = 0, previous = -1;
        for (int y = 0; y < image.getHeight(); y += Math.max(1, image.getHeight() / 100)) {
            int rgb = image.getRGB(x, y), lum = luminance(rgb); samples++;
            if (lum < 220) dark++;
            if (((rgb >>> 24) & 255) == 0 || lum < 8) transparentOrBlack++;
            if (previous >= 0 && Math.abs(lum - previous) > 12) varied++;
            previous = lum;
        }
        return transparentOrBlack == samples || (dark == 0 && varied < image.getHeight() / 20);
    }
    private static boolean lowInformationRow(BufferedImage image, int y) {
        int dark = 0, transparentOrBlack = 0, varied = 0, samples = 0, previous = -1;
        for (int x = 0; x < image.getWidth(); x += Math.max(1, image.getWidth() / 100)) {
            int rgb = image.getRGB(x, y), lum = luminance(rgb); samples++;
            if (lum < 220) dark++;
            if (((rgb >>> 24) & 255) == 0 || lum < 8) transparentOrBlack++;
            if (previous >= 0 && Math.abs(lum - previous) > 12) varied++;
            previous = lum;
        }
        return transparentOrBlack == samples || (dark == 0 && varied < image.getWidth() / 20);
    }
    private static int luminance(int rgb) {
        return (int) (.299 * ((rgb >>> 16) & 255) + .587 * ((rgb >>> 8) & 255) + .114 * (rgb & 255));
    }
    private static boolean hasNonBlackPixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y += Math.max(1, image.getHeight() / 100))
            for (int x = 0; x < image.getWidth(); x += Math.max(1, image.getWidth() / 100)) {
                int rgb = image.getRGB(x, y);
                if (((rgb >>> 24) & 255) > 0 && luminance(rgb) >= 8) return true;
            }
        return false;
    }
}
