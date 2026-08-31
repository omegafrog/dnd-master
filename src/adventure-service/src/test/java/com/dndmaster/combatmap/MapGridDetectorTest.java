package com.dndmaster.combatmap;

import com.dndmaster.combatmap.application.view.MapGridDetector;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapGridDetectorTest {
    @Test
    void detectsPrintedGridPeriodAndBounds() {
        BufferedImage image = new BufferedImage(220, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 220, 180);
        graphics.setColor(Color.BLACK);
        for (int x = 20; x <= 200; x += 20) graphics.drawLine(x, 20, x, 160);
        for (int y = 20; y <= 160; y += 20) graphics.drawLine(20, y, 200, y);
        graphics.dispose();
        var detected = new MapGridDetector().detect(image);
        assertEquals(20, detected.cellSize());
        assertEquals(20, detected.originX());
        assertEquals(20, detected.originY());
        assertEquals(9, detected.width());
        assertEquals(7, detected.height());
    }

    @Test
    void estimatesMultipleCellsWhenRenderedMapHasNoDarkGridPeaks() {
        BufferedImage image = new BufferedImage(3180, 2262, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();

        var detected = new MapGridDetector().detect(image);

        assertTrue(detected.width() >= 10);
        assertTrue(detected.height() >= 10);
        assertEquals(0, detected.originX());
        assertEquals(0, detected.originY());
    }
}
