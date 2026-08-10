package com.dndmaster.ruleknowledge.domain.document.anchor;

import java.util.Locale;
import java.util.List;
import java.util.OptionalInt;

/** Maps printed locator tokens only when a mapping policy can explain them. */
public final class PageLocatorResolver {
    public LocatorMapping direct() { return locator -> resolve(locator, 0, "direct"); }
    public LocatorMapping offset(int offset) { return locator -> resolve(locator, offset, "offset=" + offset); }
    public SegmentedLocatorMapping segmented(List<LocatorSegment> segments) {
        List<LocatorSegment> copy = segments == null ? List.of() : List.copyOf(segments);
        return (locator, segmentIndex) -> {
            Integer logical = parse(locator);
            if (logical == null || segmentIndex < 0 || segmentIndex >= copy.size() || !copy.get(segmentIndex).contains(logical)) {
                return new ResolvedLocation(locator, OptionalInt.empty(), 0, "unresolved");
            }
            LocatorSegment segment = copy.get(segmentIndex);
            return new ResolvedLocation(locator, OptionalInt.of(logical + segment.physicalOffset()), 0.8, "segment=" + segmentIndex);
        };
    }

    private ResolvedLocation resolve(String raw, int offset, String strategy) {
        Integer logical = parse(raw);
        return logical == null ? new ResolvedLocation(raw, OptionalInt.empty(), 0, "unresolved")
                : new ResolvedLocation(raw, OptionalInt.of(logical + offset), 0.8, strategy);
    }

    private Integer parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.matches("[0-9]+")) {
            try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return null; }
        }
        if (!value.matches("[ivxlcdm]+")) return null;
        int total = 0, previous = 0;
        for (int index = value.length() - 1; index >= 0; index--) {
            int current = switch (value.charAt(index)) { case 'i' -> 1; case 'v' -> 5; case 'x' -> 10; case 'l' -> 50; case 'c' -> 100; case 'd' -> 500; case 'm' -> 1000; default -> 0; };
            total += current < previous ? -current : current;
            previous = Math.max(previous, current);
        }
        return total > 0 ? total : null;
    }

    @FunctionalInterface
    public interface LocatorMapping { ResolvedLocation resolve(String rawLocator); }
    @FunctionalInterface
    public interface SegmentedLocatorMapping { ResolvedLocation resolve(String rawLocator, int segmentIndex); }
}
