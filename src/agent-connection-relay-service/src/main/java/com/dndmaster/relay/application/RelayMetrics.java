package com.dndmaster.relay.application;

import java.time.Duration;

public interface RelayMetrics {
    void finished(long requestBytes, long responseBytes, Duration duration, RelayFailureType failureType);
    void activeConnections(int count);
    static RelayMetrics noop() { return new RelayMetrics() {
        public void finished(long requestBytes, long responseBytes, Duration duration, RelayFailureType failureType) {}
        public void activeConnections(int count) {}
    }; }
}
