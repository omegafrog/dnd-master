package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.application.ports.MapModelPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 격자 정렬이 확정된 지도에서 수평·수직 선분 후보를 만드는 결정론적 분석기.
 *
 * <p>외부 영상 라이브러리를 필수로 두지 않고, 지도 준비 서비스가 이미 쓰는
 * {@link ImageIO}만 사용한다. 밝기 하나가 아니라 어두운 비율·양쪽 대비·선의
 * 연속성을 함께 계산하므로 장식의 짧은 선은 자동 벽으로 확정하지 않는다.</p>
 */
final class ImageBoundaryDetector {
    private static final double MIN_CANDIDATE_SCORE = .28;
    private static final double WALL_SCORE = .62;
    private static final int MIN_DARK_THRESHOLD = 35;
    private static final int MAX_DARK_THRESHOLD = 115;

    private ImageBoundaryDetector() {}

    static Detection detect(MapModelPort.MapInput input, ObjectMapper mapper, int width, int height) {
        try {
            if (input.imageDataUri().isBlank()) return Detection.empty("지도 이미지가 없습니다.");
            JsonNode geometry = mapper.readTree(input.mapData());
            if (!geometry.path("gridConfirmed").asBoolean(false)) {
                return Detection.empty("사용자가 격자를 적용한 뒤에만 이미지 선분을 분석합니다.");
            }
            double originX = geometry.path("gridOriginX").asDouble(Double.NaN);
            double originY = geometry.path("gridOriginY").asDouble(Double.NaN);
            double cellSize = geometry.path("gridCellSize").asDouble(Double.NaN);
            if (!Double.isFinite(originX) || !Double.isFinite(originY)
                    || !Double.isFinite(cellSize) || cellSize <= 0) {
                return Detection.empty("저장된 격자 좌표가 올바르지 않습니다.");
            }
            int comma = input.imageDataUri().indexOf(',');
            if (comma < 0) return Detection.empty("지도 이미지 형식을 읽지 못했습니다.");
            byte[] bytes = Base64.getDecoder().decode(input.imageDataUri().substring(comma + 1));
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) return Detection.empty("지도 이미지를 읽지 못했습니다.");

            String expectedRevision = geometry.path("imageRevision").asText("").trim();
            if (!expectedRevision.isBlank() && !expectedRevision.equals(sha256(input.imageDataUri()))) {
                return Detection.empty("저장된 이미지 버전과 현재 이미지가 달라 다시 감지해야 합니다.");
            }
            Crop crop = Crop.parse(geometry.path("crop"), source.getWidth(), source.getHeight());
            int[][] luminance = blurredLuminance(source, crop);
            int darkThreshold = adaptiveDarkThreshold(luminance, crop);
            EdgeSample[][] horizontal = new EdgeSample[height + 1][width];
            EdgeSample[][] vertical = new EdgeSample[height][width + 1];
            boolean[][] horizontalWalls = new boolean[height + 1][width];
            boolean[][] verticalWalls = new boolean[height][width + 1];
            for (int y = 0; y <= height; y++) {
                for (int x = 0; x < width; x++) {
                    horizontal[y][x] = sample(luminance, crop, originX + x * cellSize,
                            originY + y * cellSize, cellSize, true, darkThreshold);
                    horizontalWalls[y][x] = horizontal[y][x].wall();
                }
            }
            for (int y = 0; y < height; y++) {
                for (int x = 0; x <= width; x++) {
                    vertical[y][x] = sample(luminance, crop, originX + x * cellSize,
                            originY + y * cellSize, cellSize, false, darkThreshold);
                    verticalWalls[y][x] = vertical[y][x].wall();
                }
            }

            List<String> boundaries = new ArrayList<>();
            Map<String, MapModelPort.MapBoundaryCandidate> candidates = new LinkedHashMap<>();
            for (int y = 0; y <= height; y++) {
                for (int x = 0; x < width; x++) {
                    addCandidate(boundaries, candidates, x, y, "HORIZONTAL", horizontal[y][x], darkThreshold);
                }
            }
            for (int y = 0; y < height; y++) {
                for (int x = 0; x <= width; x++) {
                    addCandidate(boundaries, candidates, x, y, "VERTICAL", vertical[y][x], darkThreshold);
                }
            }
            int doorCount = 0;
            for (int y = 0; y <= height; y++) {
                for (int x = 1; x < width - 1; x++) {
                    if (horizontalWalls[y][x - 1] && horizontalWalls[y][x + 1]
                            && doorFeatures(horizontal[y][x]) >= 2) {
                        replaceDoor(boundaries, candidates, x, y, "HORIZONTAL", horizontal[y][x]);
                        doorCount++;
                    }
                }
            }
            for (int y = 1; y < height - 1; y++) {
                for (int x = 0; x <= width; x++) {
                    if (verticalWalls[y - 1][x] && verticalWalls[y + 1][x]
                            && doorFeatures(vertical[y][x]) >= 2) {
                        replaceDoor(boundaries, candidates, x, y, "VERTICAL", vertical[y][x]);
                        doorCount++;
                    }
                }
            }
            String rationale = String.format(Locale.ROOT,
                    "이미지 선분 분석: 벽 %d개, 문 후보 %d개, 확인 필요 %d개 (밝기 임계값 %d, 자르기 %s)",
                    boundaries.stream().filter(value -> value.contains(",WALL,false")).count(),
                    doorCount,
                    candidates.values().stream().filter(candidate -> candidate.confidence() < WALL_SCORE).count(),
                    darkThreshold, crop.fingerprint());
            return new Detection(List.copyOf(boundaries), List.copyOf(candidates.values()), rationale);
        } catch (RuntimeException | IOException exception) {
            return Detection.empty("지도 선분 분석을 완료하지 못했습니다. 수동 검수를 사용하세요.");
        }
    }

    private static void addCandidate(List<String> boundaries,
            Map<String, MapModelPort.MapBoundaryCandidate> candidates, int x, int y,
            String orientation, EdgeSample sample, int darkThreshold) {
        if (!sample.valid() || sample.score() < MIN_CANDIDATE_SCORE) return;
        String key = key(x, y, orientation);
        String kind = sample.wall() ? "WALL" : "WALL";
        if (sample.wall()) boundaries.add(x + "," + y + "," + orientation + ",WALL,false");
        candidates.put(key, candidate(x, y, orientation, kind, sample, darkThreshold));
    }

    private static void replaceDoor(List<String> boundaries,
            Map<String, MapModelPort.MapBoundaryCandidate> candidates, int x, int y,
            String orientation, EdgeSample sample) {
        String prefix = x + "," + y + "," + orientation + ",";
        boundaries.removeIf(value -> value.startsWith(prefix));
        boundaries.add(x + "," + y + "," + orientation + ",DOOR,false");
        candidates.put(key(x, y, orientation), new MapModelPort.MapBoundaryCandidate(
                x, y, orientation, "DOOR", clamp(sample.score()),
                evidence(sample, true), "IMAGE_RULES"));
    }

    private static MapModelPort.MapBoundaryCandidate candidate(int x, int y, String orientation,
            String kind, EdgeSample sample, int darkThreshold) {
        return new MapModelPort.MapBoundaryCandidate(x, y, orientation, kind,
                clamp(sample.score()), evidence(sample, false), "IMAGE_RULES");
    }

    private static List<String> evidence(EdgeSample sample, boolean door) {
        List<String> values = new ArrayList<>();
        if (sample.continuity() >= .70) values.add("continuous-edge");
        if (sample.coverage() >= .70) values.add("cell-spanning");
        if (sample.contrast() >= .12) values.add("room-contrast");
        if (sample.darkFraction() >= .55) values.add("dark-line");
        if (door && sample.jambSignal() >= .10) values.add("door-frame");
        return List.copyOf(values);
    }

    private static int doorFeatures(EdgeSample sample) {
        if (!sample.valid() || sample.wall()) return 0;
        int features = 0;
        // Neighbouring wall segments are checked by the caller. Require two
        // independent visual clues as well, so an ordinary gap or decoration
        // is not promoted to a door candidate.
        if (sample.darkFraction() >= .10 && sample.darkFraction() <= .82 && sample.continuity() >= .10) features++;
        if (sample.contrast() >= .10) features++;
        if (sample.jambSignal() >= .10) features++;
        return features;
    }

    private static EdgeSample sample(int[][] image, Crop crop, double x, double y,
            double cellSize, boolean horizontal, int darkThreshold) {
        int axisStart = horizontal ? (int) Math.round(x + cellSize * .10) : (int) Math.round(y + cellSize * .10);
        int axisEnd = horizontal ? (int) Math.round(x + cellSize * .90) : (int) Math.round(y + cellSize * .90);
        int limit = horizontal ? image[0].length - 1 : image.length - 1;
        axisStart = Math.max(0, Math.min(limit, axisStart));
        axisEnd = Math.max(0, Math.min(limit, axisEnd));
        if (horizontal) {
            axisStart = Math.max(axisStart, crop.x());
            axisEnd = Math.min(axisEnd, crop.right() - 1);
        } else {
            axisStart = Math.max(axisStart, crop.y());
            axisEnd = Math.min(axisEnd, crop.bottom() - 1);
        }
        if (axisEnd < axisStart) return EdgeSample.invalid();
        int band = Math.max(2, Math.min(16, (int) Math.round(cellSize * .16)));
        EdgeSample best = EdgeSample.invalid();
        for (int offset = -band; offset <= band; offset++) {
            int coordinate = (int) Math.round(horizontal ? y : x) + offset;
            if (horizontal && (coordinate < crop.y() || coordinate >= crop.bottom())) continue;
            if (!horizontal && (coordinate < crop.x() || coordinate >= crop.right())) continue;
            List<Integer> values = new ArrayList<>(axisEnd - axisStart + 1);
            List<Boolean> dark = new ArrayList<>(axisEnd - axisStart + 1);
            for (int axis = axisStart; axis <= axisEnd; axis++) {
                int value = horizontal ? image[coordinate][axis] : image[axis][coordinate];
                values.add(value);
                dark.add(value <= darkThreshold);
            }
            if (values.isEmpty()) continue;
            double mean = values.stream().mapToInt(Integer::intValue).average().orElse(255);
            double median = median(values);
            double darkFraction = dark.stream().filter(Boolean::booleanValue).count() / (double) dark.size();
            double continuity = longestRun(dark) / (double) dark.size();
            int outsideDistance = band + 2;
            double before = outsideMean(image, crop, coordinate - outsideDistance, axisStart, axisEnd, horizontal);
            double after = outsideMean(image, crop, coordinate + outsideDistance, axisStart, axisEnd, horizontal);
            double contrast = (Double.isFinite(before) && Double.isFinite(after))
                    ? Math.abs((before + after) / 2d - median) / 255d : 0d;
            double coverage = (axisEnd - axisStart + 1) / Math.max(1d, cellSize * .80);
            double jambSignal = jambSignal(dark);
            double score = .45 * darkFraction + .30 * clamp(contrast * 2.4) + .25 * continuity;
            EdgeSample current = new EdgeSample(mean, median, darkFraction, contrast, continuity,
                    clamp(coverage), jambSignal, score, true, score >= WALL_SCORE
                    && darkFraction >= .55 && (contrast >= .08 || darkFraction >= .85));
            if (!best.valid() || current.score() > best.score()) best = current;
        }
        return best;
    }

    private static double outsideMean(int[][] image, Crop crop, int coordinate,
            int axisStart, int axisEnd, boolean horizontal) {
        if (horizontal && (coordinate < crop.y() || coordinate >= crop.bottom())) return Double.NaN;
        if (!horizontal && (coordinate < crop.x() || coordinate >= crop.right())) return Double.NaN;
        int total = 0;
        long sum = 0;
        for (int axis = axisStart; axis <= axisEnd; axis++) {
            int value = horizontal ? image[coordinate][axis] : image[axis][coordinate];
            sum += value;
            total++;
        }
        return total == 0 ? Double.NaN : sum / (double) total;
    }

    private static int[][] blurredLuminance(BufferedImage image, Crop crop) {
        int[][] values = new int[image.getHeight()][image.getWidth()];
        for (int y = crop.y(); y < crop.bottom(); y++) {
            for (int x = crop.x(); x < crop.right(); x++) {
                int sum = 0;
                int count = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = Math.max(crop.y(), Math.min(crop.bottom() - 1, y + dy));
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = Math.max(crop.x(), Math.min(crop.right() - 1, x + dx));
                        sum += luminance(image.getRGB(xx, yy));
                        count++;
                    }
                }
                values[y][x] = sum / count;
            }
        }
        return values;
    }

    private static int adaptiveDarkThreshold(int[][] values, Crop crop) {
        int[] histogram = new int[256];
        int count = 0;
        for (int y = crop.y(); y < crop.bottom(); y++) {
            for (int x = crop.x(); x < crop.right(); x++) {
                histogram[Math.max(0, Math.min(255, values[y][x]))]++;
                count++;
            }
        }
        int target = Math.max(0, (int) Math.floor(count * .20));
        int seen = 0;
        int percentile = 255;
        for (int value = 0; value < histogram.length; value++) {
            seen += histogram[value];
            if (seen >= target) {
                percentile = value;
                break;
            }
        }
        return Math.max(MIN_DARK_THRESHOLD, Math.min(MAX_DARK_THRESHOLD,
                (int) Math.round(percentile * .72)));
    }

    private static double median(List<Integer> values) {
        int[] sorted = values.stream().mapToInt(Integer::intValue).sorted().toArray();
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2d : sorted[middle];
    }

    private static int longestRun(List<Boolean> values) {
        int longest = 0;
        int run = 0;
        int toleratedBright = 0;
        for (boolean value : values) {
            if (value) {
                run++;
                toleratedBright = 0;
            } else if (run > 0 && toleratedBright == 0) {
                toleratedBright = 1;
            } else {
                run = 0;
                toleratedBright = 0;
            }
            longest = Math.max(longest, run);
        }
        return longest;
    }

    private static double jambSignal(List<Boolean> values) {
        int size = values.size();
        if (size < 4) return 0;
        int quarter = Math.max(1, size / 4);
        double left = fraction(values.subList(0, quarter));
        double right = fraction(values.subList(size - quarter, size));
        double middle = fraction(values.subList(quarter, size - quarter));
        return Math.max(0, Math.min(left, right) - middle);
    }

    private static double fraction(List<Boolean> values) {
        return values.stream().filter(Boolean::booleanValue).count() / (double) Math.max(1, values.size());
    }

    private static int luminance(int rgb) {
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        return (299 * red + 587 * green + 114 * blue) / 1000;
    }

    private static String sha256(String value) {
        try {
            return HexFormatHolder.format(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String key(int x, int y, String orientation) { return orientation + ":" + x + ":" + y; }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }

    record Detection(List<String> boundaries, List<MapModelPort.MapBoundaryCandidate> candidates, String rationale) {
        static Detection empty(String rationale) { return new Detection(List.of(), List.of(), rationale); }
    }

    private record EdgeSample(double mean, double median, double darkFraction, double contrast,
                              double continuity, double coverage, double jambSignal, double score,
                              boolean valid, boolean wall) {
        static EdgeSample invalid() { return new EdgeSample(255, 255, 0, 0, 0, 0, 0, 0, false, false); }
    }

    private record Crop(int x, int y, int width, int height) {
        static Crop parse(JsonNode node, int imageWidth, int imageHeight) {
            if (node == null || node.isMissingNode() || node.isNull() || node.asText("").isBlank()) {
                return new Crop(0, 0, imageWidth, imageHeight);
            }
            String[] parts = node.asText("").trim().split(",", -1);
            if (parts.length != 4) throw new IllegalArgumentException("crop must be x,y,width,height");
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int width = Integer.parseInt(parts[2].trim());
            int height = Integer.parseInt(parts[3].trim());
            if (x < 0 || y < 0 || width < 1 || height < 1 || x + width > imageWidth || y + height > imageHeight) {
                throw new IllegalArgumentException("crop is outside image");
            }
            return new Crop(x, y, width, height);
        }
        int right() { return x + width; }
        int bottom() { return y + height; }
        String fingerprint() { return x + "," + y + "," + width + "," + height; }
    }

    private static final class HexFormatHolder {
        private HexFormatHolder() {}
        static String format(byte[] bytes) {
            StringBuilder value = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) value.append(String.format(Locale.ROOT, "%02x", item));
            return value.toString();
        }
    }
}
