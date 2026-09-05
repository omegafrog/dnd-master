package com.dndmaster.combatmap.application.view;

import java.util.List;

public final class PrintedGridAcceptancePolicy {
    public boolean accepts(List<Integer> vertical, List<Integer> horizontal, int width, int height) {
        if (vertical.size() < 4 || horizontal.size() < 4) return false;
        int x = medianGap(vertical), y = medianGap(horizontal);
        return x >= 2 && y >= 2 && Math.abs(x - y) <= Math.max(2, Math.round(Math.min(x, y) * .15f))
                && regular(vertical, x) && regular(horizontal, y);
    }
    private static int medianGap(List<Integer> lines) {
        var gaps = new java.util.ArrayList<Integer>();
        for (int i = 1; i < lines.size(); i++) gaps.add(lines.get(i) - lines.get(i - 1));
        gaps.sort(Integer::compareTo); return gaps.get(gaps.size() / 2);
    }
    private static boolean regular(List<Integer> lines, int period) {
        for (int i = 1; i < lines.size(); i++)
            if (Math.abs(lines.get(i) - lines.get(i - 1) - period) > Math.max(2, Math.round(period * .15f))) return false;
        return true;
    }
}
