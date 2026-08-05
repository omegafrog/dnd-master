package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.checkpoint.ExactTail;

public record ResumedGmContext(String summary, ExactTail exactTail, String characterSnapshot, String mapSnapshot,
                               String factSnapshot, String clockSnapshot) { }
