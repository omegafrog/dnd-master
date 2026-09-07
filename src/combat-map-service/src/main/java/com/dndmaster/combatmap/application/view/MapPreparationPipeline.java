package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Set;

public final class MapPreparationPipeline {
    private final MapContentBoundsDetector boundsDetector;
    private final MapGridDetectionPort gridDetector;
    private final FallbackGridPolicy fallback;
    public MapPreparationPipeline(MapContentBoundsDetector boundsDetector, MapGridDetectionPort gridDetector, FallbackGridPolicy fallback) {
        this.boundsDetector = boundsDetector; this.gridDetector = gridDetector; this.fallback = fallback;
    }
    public PreparedMapData prepare(UploadedMapSource source) {
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(source.content()));
            if (decoded == null && source.filename().toLowerCase().endsWith(".pdf")) {
                try (var document = org.apache.pdfbox.Loader.loadPDF(source.content())) {
                    decoded = new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(0, 144);
                }
            }
            if (decoded == null) throw new MapSourceUnreadableException("map source cannot be decoded");
            MapContentBounds bounds = boundsDetector.detect(decoded);
            BufferedImage normalized = decoded.getSubimage(bounds.x(), bounds.y(), bounds.width(), bounds.height());
            GridCalibration calibration = gridDetector.detect(normalized)
                    .map(grid -> new GridCalibration(new GridSpec(grid.width(), grid.height(), grid.cellSize(), 5), grid.originX(), grid.originY(), bounds, GridSource.PRINTED, grid.confidence()))
                    .orElseGet(() -> fallback.create(bounds));
            var png = new ByteArrayOutputStream(); ImageIO.write(normalized, "png", png);
            String image = "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
            String gridBounds = calibration.originX() + "," + calibration.originY() + "," + calibration.grid().width() * calibration.grid().cellSize() + "," + calibration.grid().height() * calibration.grid().cellSize() + "," + normalized.getWidth() + "," + normalized.getHeight();
            return new PreparedMapData(calibration.grid(), List.of(), Set.of(), List.of(new MapLayer("MAP_IMAGE", image, LayerVisibility.PLAYER_VISIBLE), new MapLayer("GRID_BOUNDS", gridBounds, LayerVisibility.PLAYER_VISIBLE), new MapLayer("GRID_META", calibration.metadataValue(), LayerVisibility.AI_ONLY)));
        } catch (MapSourceUnreadableException e) { throw e; }
        catch (Exception e) { throw new MapSourceUnreadableException("map source cannot be prepared", e); }
    }
}
