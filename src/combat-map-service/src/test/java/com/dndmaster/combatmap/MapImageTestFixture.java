package com.dndmaster.combatmap;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

final class MapImageTestFixture {
    private MapImageTestFixture() {}

    static String dataUri(int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
