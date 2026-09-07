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
        var detected = new MapGridDetector().detect(image).orElseThrow();
        assertEquals(20, detected.cellSize());
        assertEquals(20, detected.originX());
        assertEquals(20, detected.originY());
        assertEquals(9, detected.width());
        assertEquals(7, detected.height());
    }

    @Test
    void doesNotClaimPrintedGridWhenThereAreNoDarkGridPeaks() {
        BufferedImage image = new BufferedImage(3180, 2262, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();

        assertTrue(new MapGridDetector().detect(image).isEmpty());
    }

    @Test
    void preservesLargePrintedGridInsteadOfForcingTwentyByTwenty() {
        BufferedImage image = new BufferedImage(410, 410, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 410, 410);
        graphics.setColor(Color.BLACK);
        for (int p = 5; p <= 405; p += 10) { graphics.drawLine(p, 5, p, 405); graphics.drawLine(5, p, 405, p); }
        graphics.dispose();

        var detected = new MapGridDetector().detect(image).orElseThrow();
        assertEquals(40, detected.width());
        assertEquals(40, detected.height());
        assertEquals(10, detected.cellSize());
    }

    @Test
    void rejectsWallsThatOnlyFormTwoLongLines() {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 200, 200);
        graphics.setColor(Color.BLACK); graphics.drawLine(20, 20, 180, 20); graphics.drawLine(20, 180, 180, 180);
        graphics.dispose();
        assertTrue(new MapGridDetector().detect(image).isEmpty());
    }
}
