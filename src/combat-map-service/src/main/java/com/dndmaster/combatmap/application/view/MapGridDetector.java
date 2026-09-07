package com.dndmaster.combatmap.application.view;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Detects the regular printed grid without relying on map-specific labels. */
public final class MapGridDetector implements MapGridDetectionPort {
    private final PrintedGridAcceptancePolicy acceptance = new PrintedGridAcceptancePolicy();
    @Override
    public java.util.Optional<DetectedMapGrid> detect(BufferedImage image) {
        if (image == null) throw new IllegalArgumentException("map image required");
        List<Integer> vertical = peaks(image, true);
        List<Integer> horizontal = peaks(image, false);
        if (!acceptance.accepts(vertical, horizontal, image.getWidth(), image.getHeight())) return java.util.Optional.empty();
        int dx = medianGap(vertical), dy = medianGap(horizontal);
        int cell = Math.max(1, Math.round((dx + dy) / 2f));
        int ox = vertical.get(0), oy = horizontal.get(0);
        int width = Math.max(1, Math.round((vertical.get(vertical.size() - 1) - ox) / (float) cell));
        int height = Math.max(1, Math.round((horizontal.get(horizontal.size() - 1) - oy) / (float) cell));
        double confidence = Math.min(1d, (vertical.size() + horizontal.size()) / 80d);
        return java.util.Optional.of(new DetectedMapGrid(width, height, cell, ox, oy, confidence));
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

    private static int medianGap(List<Integer> peaks) {
        List<Integer> gaps = new ArrayList<>();
        for (int i = 1; i < peaks.size(); i++) {
            int gap = peaks.get(i) - peaks.get(i - 1);
            if (gap >= 2) gaps.add(gap);
        }
        gaps.sort(Integer::compareTo);
        return gaps.get(gaps.size() / 2);
    }
}
