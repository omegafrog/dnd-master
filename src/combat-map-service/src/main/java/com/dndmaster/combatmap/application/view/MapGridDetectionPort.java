package com.dndmaster.combatmap.application.view;

import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * Preprocessing pipeline seam for extracting printed tactical-grid geometry.
 *
 * <p>The Python preprocessing agent currently exposes document/PDF extraction,
 * not map geometry. Combat-map therefore owns this adapter until a cross-service
 * map-image contract is available; callers must depend on this port rather than
 * constructing a detector directly.</p>
 */
@FunctionalInterface
public interface MapGridDetectionPort {
    Optional<DetectedMapGrid> detect(BufferedImage image);
}
