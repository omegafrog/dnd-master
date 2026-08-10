package com.dndmaster.ruleknowledge.domain.document.anchor;

/** One logical-page range after a printed pagination reset. */
public record LocatorSegment(int logicalStart, int logicalEnd, int physicalOffset) {
    public LocatorSegment {
        if (logicalStart < 1 || logicalEnd < logicalStart) throw new IllegalArgumentException("invalid logical locator segment");
    }

    boolean contains(int logicalPage) { return logicalPage >= logicalStart && logicalPage <= logicalEnd; }
}
