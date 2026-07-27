package com.dndmaster.ruleknowledge.infrastructure.ocr;

import com.dndmaster.ruleknowledge.application.ocr.OcrFailure;
import com.dndmaster.ruleknowledge.application.ocr.OcrLine;
import com.dndmaster.ruleknowledge.application.ocr.OcrPort;
import com.dndmaster.ruleknowledge.application.ocr.OcrRequest;
import com.dndmaster.ruleknowledge.application.ocr.OcrResult;
import com.dndmaster.ruleknowledge.domain.rulebook.BoundingBox;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

public final class TesseractOcrAdapter implements OcrPort {
    private final String executable;
    private final List<String> languages;
    private final Duration timeout;

    public TesseractOcrAdapter() {
        this("tesseract", List.of("eng", "kor"), Duration.ofSeconds(20));
    }

    public TesseractOcrAdapter(String executable, List<String> languages, Duration timeout) {
        this.executable = Objects.requireNonNull(executable, "executable must not be null");
        this.languages = List.copyOf(Objects.requireNonNull(languages, "languages must not be null"));
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    }

    @Override
    public OcrResult recognize(OcrRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(request.content()));
        } catch (IOException exception) {
            return new OcrResult(List.of(), List.of("ocr image decode failed"), OcrFailure.CORRUPT);
        }
        if (image == null) {
            return new OcrResult(List.of(), List.of("ocr image decode failed"), OcrFailure.CORRUPT);
        }

        Path inputFile = null;
        try {
            inputFile = Files.createTempFile("ocr-", imageSuffix(request.mimeType()));
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", output);
                Files.write(inputFile, output.toByteArray());
            }

            Process process = new ProcessBuilder(
                    executable,
                    inputFile.toAbsolutePath().toString(),
                    "stdout",
                    "-l",
                    String.join("+", languages),
                    "--psm",
                    "6",
                    "tsv")
                    .redirectErrorStream(false)
                    .start();

            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new OcrResult(List.of(), List.of(request.sourceLabel() + " timeout"), OcrFailure.TIMEOUT);
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                if (stderr.contains("Error opening data file")
                        || stderr.contains("Failed loading language")
                        || stderr.contains("Could not initialize tesseract")) {
                    return new OcrResult(List.of(), List.of(stderr.trim()), OcrFailure.MISSING_LANGUAGE_PACK);
                }
                return new OcrResult(List.of(), List.of(trimMessage(stderr, request.sourceLabel())), OcrFailure.UNAVAILABLE);
            }
            return parseTsv(stdout, request.sourceLabel(), image.getWidth(), image.getHeight());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new OcrResult(List.of(), List.of(request.sourceLabel() + " interrupted"), OcrFailure.TIMEOUT);
        } catch (IOException exception) {
            return new OcrResult(List.of(), List.of(trimMessage(exception.getMessage(), request.sourceLabel())), OcrFailure.UNAVAILABLE);
        } finally {
            if (inputFile != null) {
                try {
                    Files.deleteIfExists(inputFile);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    private OcrResult parseTsv(String stdout, String sourceLabel, int imageWidth, int imageHeight) {
        String[] lines = stdout.split("\\R");
        if (lines.length <= 1) {
            return new OcrResult(List.of(), List.of(sourceLabel + " produced no OCR text"), OcrFailure.NONE);
        }

        Map<String, LineBuilder> builders = new LinkedHashMap<>();
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            if (line.isBlank()) {
                continue;
            }
            String[] cells = line.split("\t", 12);
            if (cells.length < 12) {
                continue;
            }
            int level = parseInt(cells[0], -1);
            if (level != 5) {
                continue;
            }
            String text = cells[11].trim();
            if (text.isBlank()) {
                continue;
            }
            int page = parseInt(cells[1], 0);
            int block = parseInt(cells[2], 0);
            int paragraph = parseInt(cells[3], 0);
            int lineNumber = parseInt(cells[4], 0);
            int left = parseInt(cells[6], 0);
            int top = parseInt(cells[7], 0);
            int width = parseInt(cells[8], 0);
            int height = parseInt(cells[9], 0);
            double confidence = parseDouble(cells[10], 0d);
            String key = page + ":" + block + ":" + paragraph + ":" + lineNumber;
            builders.computeIfAbsent(key, ignored -> new LineBuilder(lineNumber, imageWidth, imageHeight))
                    .add(text, left, top, width, height, confidence);
        }

        List<OcrLine> ocrLines = builders.values().stream()
                .filter(LineBuilder::hasText)
                .map(LineBuilder::build)
                .toList();
        List<String> warnings = ocrLines.isEmpty()
                ? List.of(sourceLabel + " produced no OCR text")
                : List.of();
        return new OcrResult(ocrLines, warnings, OcrFailure.NONE);
    }

    private static String imageSuffix(String mimeType) {
        if (mimeType == null) {
            return ".png";
        }
        return switch (mimeType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/tiff" -> ".tiff";
            case "image/bmp" -> ".bmp";
            case "image/png" -> ".png";
            default -> ".png";
        };
    }

    private static String trimMessage(String message, String sourceLabel) {
        if (message == null || message.isBlank()) {
            return sourceLabel;
        }
        return message.trim();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static final class LineBuilder {
        private final int lineNumber;
        private final int imageWidth;
        private final int imageHeight;
        private final List<String> words = new ArrayList<>();
        private int left = Integer.MAX_VALUE;
        private int top = Integer.MAX_VALUE;
        private int right = 0;
        private int bottom = 0;
        private double confidenceSum;
        private int confidenceCount;

        private LineBuilder(int lineNumber, int imageWidth, int imageHeight) {
            this.lineNumber = lineNumber;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
        }

        private void add(String text, int left, int top, int width, int height, double confidence) {
            words.add(text);
            this.left = Math.min(this.left, left);
            this.top = Math.min(this.top, top);
            this.right = Math.max(this.right, left + width);
            this.bottom = Math.max(this.bottom, top + height);
            if (confidence >= 0d) {
                confidenceSum += confidence;
                confidenceCount++;
            }
        }

        private boolean hasText() {
            return !words.isEmpty();
        }

        private OcrLine build() {
            double avgConfidence = confidenceCount == 0 ? 0d : confidenceSum / confidenceCount;
            return new OcrLine(
                    lineNumber,
                    String.join(" ", words).trim(),
                    normalizeBounds(left, top, right, bottom, imageWidth, imageHeight),
                    avgConfidence);
        }

        private static BoundingBox normalizeBounds(
                int left, int top, int right, int bottom, int imageWidth, int imageHeight) {
            double leftNorm = clamp(left, imageWidth);
            double topNorm = clamp(top, imageHeight);
            double rightNorm = Math.max(leftNorm, clamp(right, imageWidth));
            double bottomNorm = Math.max(topNorm, clamp(bottom, imageHeight));
            return new BoundingBox(leftNorm, topNorm, rightNorm, bottomNorm);
        }

        private static double clamp(int value, int size) {
            if (size <= 0) {
                return 0d;
            }
            return Math.max(0d, Math.min(1d, value / (double) size));
        }
    }
}
