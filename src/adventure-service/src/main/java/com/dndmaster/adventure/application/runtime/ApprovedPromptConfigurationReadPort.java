package com.dndmaster.adventure.application.runtime;

import java.util.Optional;

/** Read-only boundary to gm-eval's approved active runtime projection. */
public interface ApprovedPromptConfigurationReadPort {
    Optional<ApprovedPromptConfiguration> current(String role);
}
