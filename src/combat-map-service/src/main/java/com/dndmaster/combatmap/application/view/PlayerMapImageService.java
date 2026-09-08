package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.GridPosition;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import javax.imageio.ImageIO;

/** 공개된 칸의 원본 픽셀만 다시 인코딩한다. */
public final class PlayerMapImageService {
    private PlayerMapImageService() {}

    public static String maskedDataUri(String sourceDataUri, double originX, double originY, double cellSize,
            Set<GridPosition> explored) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(maskedPng(sourceDataUri, originX, originY, cellSize, explored));
    }

    public static byte[] maskedPng(String sourceDataUri, double originX, double originY, double cellSize,
            Set<GridPosition> explored) {
        if (!Double.isFinite(originX) || !Double.isFinite(originY) || !Double.isFinite(cellSize) || cellSize <= 0
                || explored == null || explored.isEmpty()) {
            throw new IllegalArgumentException("public map image is unavailable");
        }
        try {
            BufferedImage source = decode(sourceDataUri);
            BufferedImage masked = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
                int gridX = (int) Math.floor((x - originX) / cellSize);
                int gridY = (int) Math.floor((y - originY) / cellSize);
                boolean visible = gridX >= 0 && gridY >= 0 && explored.contains(new GridPosition(gridX, gridY));
                masked.setRGB(x, y, visible ? source.getRGB(x, y) : 0xFF000000);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(masked, "png", bytes)) throw new IllegalArgumentException("public map image is unavailable");
            return bytes.toByteArray();
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) throw (IllegalArgumentException) exception;
            throw new IllegalArgumentException("public map image is unavailable", exception);
        }
    }

    /** 이전 공개 PNG에 새로 관찰한 칸의 원본 픽셀만 덧씌운다. */
    public static byte[] extendPng(byte[] previousPng, String sourceDataUri, double originX, double originY,
            double cellSize, Set<GridPosition> newlyCovered) {
        if (previousPng == null || newlyCovered == null || newlyCovered.isEmpty()) throw new IllegalArgumentException("public map image is unavailable");
        try {
            BufferedImage previous = ImageIO.read(new ByteArrayInputStream(previousPng));
            BufferedImage source = decode(sourceDataUri);
            if (previous == null || previous.getWidth() != source.getWidth() || previous.getHeight() != source.getHeight()) throw new IllegalArgumentException("public map image is unavailable");
            for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
                int gridX = (int) Math.floor((x - originX) / cellSize);
                int gridY = (int) Math.floor((y - originY) / cellSize);
                if (newlyCovered.contains(new GridPosition(gridX, gridY))) previous.setRGB(x, y, source.getRGB(x, y));
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(previous, "png", bytes)) throw new IllegalArgumentException("public map image is unavailable");
            return bytes.toByteArray();
        } catch (IOException exception) { throw new IllegalArgumentException("public map image is unavailable", exception); }
    }

    private static BufferedImage decode(String value) throws IOException {
        Objects.requireNonNull(value, "sourceDataUri");
        int marker = value.indexOf("base64,");
        if (!value.startsWith("data:image/") || marker < 0) throw new IllegalArgumentException("public map image is unavailable");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(value.substring(marker + "base64,".length()))));
        if (image == null) throw new IllegalArgumentException("public map image is unavailable");
        return image;
    }

    public static byte[] sourcePng(String sourceDataUri) {
        try {
            BufferedImage image = decode(sourceDataUri);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", bytes)) throw new IllegalArgumentException("map image is unavailable");
            return bytes.toByteArray();
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) throw (IllegalArgumentException) exception;
            throw new IllegalArgumentException("map image is unavailable", exception);
        }
    }

    /** 원본 이미지 안에서 잘라낼 영역인지 확인한다. 형식: x,y,width,height (픽셀). */
    public static void validateCrop(String sourceDataUri, String crop) {
        if (crop == null || crop.isBlank()) return;
        try {
            String[] values = crop.trim().split(",", -1);
            if (values.length != 4) throw new IllegalArgumentException("map crop must be x,y,width,height");
            long x = Long.parseLong(values[0].trim());
            long y = Long.parseLong(values[1].trim());
            long width = Long.parseLong(values[2].trim());
            long height = Long.parseLong(values[3].trim());
            BufferedImage image = decode(sourceDataUri);
            if (x < 0 || y < 0 || width <= 0 || height <= 0
                    || x > image.getWidth() || y > image.getHeight()
                    || width > image.getWidth() - x || height > image.getHeight() - y) {
                throw new IllegalArgumentException("map crop must fit inside source image");
            }
        } catch (IOException | NumberFormatException exception) {
            throw new IllegalArgumentException("map crop is invalid", exception);
        }
    }
}
