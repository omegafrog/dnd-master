package com.dndmaster.aigamemaster.localcodex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Properties;

/** Persists non-secret Codex connection flags in the user's local settings directory. */
public final class FileInstallationLinkStore implements InstallationLinkStore {
    private static final String KEY = "codex.connection.enabled";
    private static final String REAUTHENTICATION_REQUIRED_KEY = "codex.connection.reauthentication-required";
    private final Path settingsFile;

    public FileInstallationLinkStore(Path settingsFile) {
        this.settingsFile = settingsFile.toAbsolutePath().normalize();
    }

    public static FileInstallationLinkStore forCurrentUser() {
        return new FileInstallationLinkStore(Path.of(System.getProperty("user.home"), ".dnd-master", "settings.properties"));
    }

    @Override
    public synchronized Optional<Boolean> getEnabled() {
        if (!Files.isRegularFile(settingsFile)) return Optional.empty();
        Properties properties = load();
        String value = properties.getProperty(KEY);
        return value == null ? Optional.empty() : Optional.of(Boolean.parseBoolean(value));
    }

    @Override
    public synchronized void setEnabled(boolean enabled) {
        Properties properties = load();
        properties.setProperty(KEY, Boolean.toString(enabled));
        save(properties);
    }

    @Override
    public synchronized Optional<Boolean> getReauthenticationRequired() {
        if (!Files.isRegularFile(settingsFile)) return Optional.empty();
        String value = load().getProperty(REAUTHENTICATION_REQUIRED_KEY);
        return value == null ? Optional.empty() : Optional.of(Boolean.parseBoolean(value));
    }

    @Override
    public synchronized void setReauthenticationRequired(boolean required) {
        Properties properties = load();
        properties.setProperty(REAUTHENTICATION_REQUIRED_KEY, Boolean.toString(required));
        save(properties);
    }

    private void save(Properties properties) {
        try {
            Files.createDirectories(settingsFile.getParent());
            Path temporary = Files.createTempFile(settingsFile.getParent(), "settings-", ".tmp");
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "D&D Master local settings");
            }
            restrict(temporary);
            try {
                Files.move(temporary, settingsFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, settingsFile, StandardCopyOption.REPLACE_EXISTING);
            }
            restrict(settingsFile);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not save local connection setting");
        }
    }

    private Properties load() {
        Properties properties = new Properties();
        if (!Files.isRegularFile(settingsFile)) return properties;
        try (var input = Files.newInputStream(settingsFile)) {
            properties.load(input);
            return properties;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read local connection setting");
        }
    }

    private static void restrict(Path path) {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // The operating system may not expose POSIX permissions.
        }
    }
}
