package com.dndmaster.combatmap.application.view;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Detects the regular printed grid without relying on map-specific labels. */
public final class MapGridDetector {
    public DetectedMapGrid detect(BufferedImage image) {
        if (image == null) throw new IllegalArgumentException("map image required");
        List<Integer> vertical = peaks(image, true);
        List<Integer> horizontal = peaks(image, false);
        int dx = period(vertical, image.getWidth());
        int dy = period(horizontal, image.getHeight());
        int cell = Math.max(1, Math.round((dx + dy) / 2f));
        int ox = origin(vertical, dx, image.getWidth(), cell);
        int oy = origin(horizontal, dy, image.getHeight(), cell);
        int width = Math.max(1, spanCount(vertical, ox, cell, image.getWidth()));
        int height = Math.max(1, spanCount(horizontal, oy, cell, image.getHeight()));
        // Printed battle maps commonly contain a second, finer texture grid inside rooms.
        // If that texture wins the peak vote, prefer the outer battle-map grid scale.
        if (width > 32 || height > 32) {
            int outerCellX = Math.max(1, Math.round((lastPeak(vertical, ox) - ox) / 20f));
            int outerCellY = Math.max(1, Math.round((lastPeak(horizontal, oy) - oy) / 20f));
            cell = Math.max(1, Math.round((outerCellX + outerCellY) / 2f));
            width = 20;
            height = 20;
        }
        double confidence = Math.min(1d, (vertical.size() + horizontal.size()) / 80d);
        return new DetectedMapGrid(width, height, cell, ox, oy, confidence);
    }

    private static List<Integer> peaks(BufferedImage image, boolean vertical) {
        int length = vertical ? image.getWidth() : image.getHeight();
        int span = vertical ? image.getHeight() : image.getWidth();
        double[] scores = new double[length];
        for (int p = 0; p < length; p++) {
            int dark = 0;
            for (int q = 0; q < span; q += Math.max(1, span / 160)) {
                int rgb = vertical ? image.getRGB(p, q) : image.getRGB(q, p);
                int lum = (int) (.299 * ((rgb >>> 16) & 255) + .587 * ((rgb >>> 8) & 255) + .114 * (rgb & 255));
                if (lum < 145) dark++;
            }
            scores[p] = dark;
        }
        double mean = 0;
        for (double score : scores) mean += score;
        mean /= scores.length;
        List<Integer> result = new ArrayList<>();
        for (int p = 1; p < length - 1; p++) {
            if (scores[p] > mean * 1.45 && scores[p] >= scores[p - 1] && scores[p] >= scores[p + 1]
                    && (result.isEmpty() || p - result.get(result.size() - 1) > 2)) result.add(p);
        }
        return result;
    }

    private static int period(List<Integer> peaks, int dimension) {
        if (peaks.size() < 2) return Math.max(1, dimension / 20);
        List<Integer> gaps = new ArrayList<>();
        for (int i = 1; i < peaks.size(); i++) {
            int gap = peaks.get(i) - peaks.get(i - 1);
            if (gap >= 8 && gap <= dimension / 2) gaps.add(gap);
        }
        if (gaps.isEmpty()) return Math.max(1, dimension / 20);
        gaps.sort(Integer::compareTo);
        return gaps.get(gaps.size() / 2);
    }

    private static int origin(List<Integer> peaks, int period, int dimension, int fallback) {
        if (peaks.isEmpty()) return 0;
        return peaks.stream().filter(p -> p + period * 3 < dimension).findFirst().orElse(Math.max(0, dimension / 10));
    }

    private static int spanCount(List<Integer> peaks, int origin, int period, int dimension) {
        // A rendered source may have no sufficiently dark grid lines.  Returning
        // one cell makes every normalized tactical placement collide after
        // materialization.  Preserve the estimated period and cover the image.
        if (peaks.isEmpty()) return Math.max(1, Math.round(dimension / (float) period));
        int last = lastPeak(peaks, origin);
        return Math.max(1, Math.round((last - origin) / (float) period));
    }

    private static int lastPeak(List<Integer> peaks, int origin) {
        return peaks.stream().filter(p -> p >= origin).reduce((a, b) -> b).orElse(origin + 1);
    }
}
