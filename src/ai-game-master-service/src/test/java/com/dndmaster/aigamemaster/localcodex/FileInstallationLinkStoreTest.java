package com.dndmaster.aigamemaster.localcodex;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileInstallationLinkStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void storesReauthenticationRequirementAlongsideTheInstallationLinkWithoutChangingLinkState() {
        Path settings = temporaryDirectory.resolve("settings.properties");
        var first = new FileInstallationLinkStore(settings);
        first.setEnabled(true);
        first.setReauthenticationRequired(true);

        var reloaded = new FileInstallationLinkStore(settings);

        assertThat(reloaded.getEnabled()).isEqualTo(Optional.of(true));
        assertThat(reloaded.getReauthenticationRequired()).isEqualTo(Optional.of(true));
        reloaded.setReauthenticationRequired(false);
        assertThat(new FileInstallationLinkStore(settings).getReauthenticationRequired()).isEqualTo(Optional.of(false));
    }
}
