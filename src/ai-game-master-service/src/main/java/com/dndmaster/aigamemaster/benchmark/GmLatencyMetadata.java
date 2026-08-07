package com.dndmaster.aigamemaster.benchmark;

/** Reproducibility and deadline declaration for a latency benchmark artifact. */
public record GmLatencyMetadata(String hardwareProfile, long totalDeadlineMs, long retrievalDeadlineMs,
                                int sampleCount) {
    public GmLatencyMetadata {
        if (hardwareProfile == null || hardwareProfile.isBlank()
                || totalDeadlineMs < 1 || retrievalDeadlineMs < 1 || retrievalDeadlineMs > totalDeadlineMs
                || sampleCount < 3) {
            throw new IllegalArgumentException("latency metadata requires hardware, deadlines, and 3 samples");
        }
        hardwareProfile = hardwareProfile.trim();
    }

    static GmLatencyMetadata defaults(int sampleCount) {
        return new GmLatencyMetadata(System.getProperty("os.name", "unknown") + ":" +
                System.getProperty("os.arch", "unknown"), 90_000, 30_000, sampleCount);
    }
}
