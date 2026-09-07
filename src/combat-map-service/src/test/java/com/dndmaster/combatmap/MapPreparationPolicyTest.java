package com.dndmaster.combatmap;

import com.dndmaster.combatmap.application.view.*;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.LayerVisibility;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class MapPreparationPolicyTest {
    @Test
    void trimsOnlyLowInformationBorderAndKeepsInteriorWhiteArea() {
        BufferedImage image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0, 0, 100, 80);
        g.setColor(Color.DARK_GRAY); g.fillRect(20, 15, 60, 50); g.setColor(Color.WHITE); g.fillRect(35, 25, 25, 20); g.dispose();
        var bounds = new MapContentBoundsDetector().detect(image);
        assertEquals(new MapContentBounds(20, 15, 60, 50, bounds.confidence()), bounds);
    }

    @Test
    void lowConfidenceBoundsRemainFullSource() {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        assertEquals(new MapContentBounds(0, 0, 40, 30, 0.0), new MapContentBoundsDetector().detect(image));
    }

    @Test
    void fallbackIsDeterministicSquareAndExplicitlyMarked() {
        var policy = new FallbackGridPolicy(20);
        var first = policy.create(new MapContentBounds(0, 0, 1000, 500, 1.0));
        assertEquals(first, policy.create(new MapContentBounds(0, 0, 1000, 500, 1.0)));
        assertEquals(GridSource.FALLBACK, first.source());
        assertEquals(first.grid().cellSize(), first.grid().cellSize());
        assertEquals(20, first.grid().height());
    }

    @Test
    void unreadableSourceFailsClosed() {
        var pipeline = new MapPreparationPipeline(new MapContentBoundsDetector(), new MapGridDetector(), new FallbackGridPolicy(20));
        assertThrows(MapSourceUnreadableException.class, () -> pipeline.prepare(new UploadedMapSource("broken.png", new byte[]{1, 2, 3})));
    }

    @Test
    void preparationCarriesGridSourceAsAiOnlyMetadata() throws Exception {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", bytes);
        var pipeline = new MapPreparationPipeline(new MapContentBoundsDetector(), new MapGridDetector(), new FallbackGridPolicy(20));
        var prepared = pipeline.prepare(new UploadedMapSource("map.png", bytes.toByteArray()));
        var metadata = prepared.layers().stream().filter(layer -> layer.type().equals("GRID_META")).findFirst().orElseThrow();
        assertEquals(LayerVisibility.AI_ONLY, metadata.visibility());
        assertTrue(metadata.value().contains("source=FALLBACK"));
    }
}
