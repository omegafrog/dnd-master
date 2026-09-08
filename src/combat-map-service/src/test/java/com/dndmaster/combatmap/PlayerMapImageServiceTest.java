package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.combatmap.application.view.PlayerMapImageService;
import com.dndmaster.combatmap.domain.GridPosition;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PlayerMapImageServiceTest {
    @Test
    void reencodesOnlyExploredCellsAndMasksUnexploredHoles() throws Exception {
        BufferedImage source = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
        fill(source, 0, 0, 2, 2, Color.RED.getRGB());
        fill(source, 2, 0, 2, 2, Color.BLUE.getRGB());

        String visible = PlayerMapImageService.maskedDataUri(dataUri(source), 0, 0, 2,
                Set.of(new GridPosition(0, 0)));
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(visible.substring(visible.indexOf(",") + 1))));

        assertEquals(Color.RED.getRGB(), result.getRGB(0, 0));
        assertEquals(Color.BLACK.getRGB(), result.getRGB(3, 1));
        assertFalse(visible.contains(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3})));
    }

    @Test
    void rejectsAnImageWhenNoExploredCellsAreAvailable() {
        assertThrows(IllegalArgumentException.class, () -> PlayerMapImageService.maskedDataUri(
                dataUri(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)), 0, 0, 2, Set.of()));
    }

    private static void fill(BufferedImage image, int x, int y, int width, int height, int color) {
        for (int row = y; row < y + height; row++) for (int column = x; column < x + width; column++) image.setRGB(column, row, color);
    }

    private static String dataUri(BufferedImage image) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception exception) { throw new AssertionError(exception); }
    }
}
