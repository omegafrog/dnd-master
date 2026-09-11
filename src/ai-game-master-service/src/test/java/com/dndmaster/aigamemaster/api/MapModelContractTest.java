package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.application.ports.MapModelPort;
import com.dndmaster.aigamemaster.application.ports.MapEntryPlacementModelPort;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapModelContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesStructuredGmPlacementAndRejectsBlockedDoor() {
        GmCompletionAdapter adapter = fixed("{\"width\":4,\"height\":3,\"boundaries\":[\"1,1,VERTICAL,WALL\",\"2,1,HORIZONTAL,DOOR\"],\"obstacles\":[],\"doors\":[],\"playerStart\":\"0,0\",\"rationale\":\"visible room boundary\",\"candidates\":[{\"x\":1,\"y\":1,\"orientation\":\"VERTICAL\",\"kind\":\"WALL\",\"confidence\":0.91,\"evidence\":[\"continuous-edge\",\"room-contrast\"],\"source\":\"IMAGE_RULES\"}]}");
        MapModelPort model = new AiGameMasterApiConfiguration().mapModelPort(adapter, mapper);

        MapModelPort.MapOutput output = model.generate(new MapModelPort.MapInput("map", "room", "grid"));

        assertEquals(4, output.width());
        assertEquals(java.util.List.of(), output.obstacles());
        assertEquals(java.util.List.of(), output.doors());
        assertEquals(java.util.List.of("1,1,VERTICAL,WALL,false", "2,1,HORIZONTAL,DOOR,false"), output.boundaries());
        assertEquals("0,0", output.playerStart());
        assertEquals(null, output.playerStartProposal());
        assertEquals(1, output.candidates().size());
        assertEquals(0.91, output.candidates().getFirst().confidence());
    }

    @Test
    void dropsLowConfidenceImageCandidatesBeforeTheyReachTheReviewScreen() {
        GmCompletionAdapter adapter = fixed("{\"width\":4,\"height\":3,\"boundaries\":[],\"obstacles\":[],\"doors\":[],\"playerStart\":\"\",\"candidates\":[{\"x\":1,\"y\":1,\"orientation\":\"VERTICAL\",\"kind\":\"WALL\",\"confidence\":0.55,\"evidence\":[\"dark-line\"],\"source\":\"IMAGE_RULES\"}]}");
        MapModelPort model = new AiGameMasterApiConfiguration().mapModelPort(adapter, mapper);

        MapModelPort.MapOutput output = model.generate(new MapModelPort.MapInput("map", "room", "grid"));

        assertEquals(java.util.List.of(), output.candidates());
    }

    @Test
    void parsesScenarioEntryStartProposalWithEvidenceAndConfidence() {
        GmCompletionAdapter adapter = fixed("{\"width\":4,\"height\":3,\"boundaries\":[],\"obstacles\":[],\"doors\":[],\"playerStart\":\"\",\"playerStartProposal\":{\"position\":\"1,2\",\"confidence\":0.88,\"evidence\":[\"story says south entrance\",\"open doorway at south edge\"],\"source\":\"SCENARIO_ENTRY\"}}");
        MapModelPort.MapOutput output = new AiGameMasterApiConfiguration().mapModelPort(adapter, mapper)
                .generate(new MapModelPort.MapInput("map", "party enters through the south entrance", "grid"));

        assertEquals("1,2", output.playerStartProposal().position());
        assertEquals(.88, output.playerStartProposal().confidence());
        assertEquals(2, output.playerStartProposal().evidence().size());
    }

    @Test
    void parsesDedicatedEntryPlacementWithTransitionAnchorAndRankedCandidate() {
        GmCompletionAdapter adapter = fixed("{\"status\":\"RESOLVED\",\"entryInterpretation\":{\"transition\":\"DESCEND_STAIRS\",\"targetScene\":\"맥주 저장고\",\"anchor\":\"STAIRS\",\"placementRelation\":\"ADJACENT_TO_ENTRY_INSIDE_DESTINATION\",\"evidence\":\"나무 계단이 삐걱거리며 지하실로 이어집니다.\"},\"candidates\":[{\"x\":4,\"y\":16,\"confidence\":0.91,\"source\":\"MAP_IMAGE\",\"anchor\":\"visible staircase\",\"reason\":\"계단 끝 바로 안쪽의 이동 가능한 칸\",\"evidence\":[\"나무 계단\",\"계단 끝의 바닥\"]}],\"reason\":\"계단 끝과 서술이 일치합니다.\"}");
        MapEntryPlacementModelPort model = new AiGameMasterApiConfiguration().mapEntryPlacementModelPort(adapter, mapper);

        MapEntryPlacementModelPort.EntryPlacementOutput output = model.propose(
                new MapEntryPlacementModelPort.EntryPlacementInput("맥주 저장고",
                        "양조장 문을 열고 지하실로 내려갑니다", "지하실 진입", "나무 계단이 삐걱거리며 지하실로 이어집니다.", "{}", ""));

        assertEquals("RESOLVED", output.status());
        assertEquals("DESCEND_STAIRS", output.interpretation().transition());
        assertEquals("STAIRS", output.interpretation().anchor());
        assertEquals(1, output.candidates().size());
        assertEquals(4, output.candidates().getFirst().x());
        assertEquals(16, output.candidates().getFirst().y());
        assertEquals("visible staircase", output.candidates().getFirst().anchor());
    }

    @Test
    void rejectsDoorThatOverlapsObstacle() {
        GmCompletionAdapter adapter = fixed("{\"width\":4,\"height\":3,\"obstacles\":[\"1,1\"],\"doors\":[\"1,1\"],\"playerStart\":\"0,0\"}");
        MapModelPort model = new AiGameMasterApiConfiguration().mapModelPort(adapter, mapper);

        assertThrows(IllegalArgumentException.class,
                () -> model.generate(new MapModelPort.MapInput("map", "room", "grid")));
    }

    @Test
    void usesAuthoredDimensionsWhenGmProviderIsUnavailable() {
        GmCompletionAdapter failing = new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser) {
                throw new IllegalStateException("provider unavailable");
            }
        };
        MapModelPort model = new AiGameMasterApiConfiguration().mapModelPort(failing, mapper);

        MapModelPort.MapOutput output = model.generate(
                new MapModelPort.MapInput("map", "room", "{\"gridWidth\":7,\"gridHeight\":6}"));

        assertEquals(7, output.width());
        assertEquals(6, output.height());
        assertEquals(java.util.List.of(), output.obstacles());
        assertEquals(java.util.List.of(), output.doors());
    }

    @Test
    void fallsBackToImageRulesWhenTheProviderReturnsMalformedJson() throws Exception {
        String image = dataUri(30, 30, graphics -> {
            graphics.setColor(java.awt.Color.BLACK);
            graphics.fillRect(10, 9, 10, 3);
        });
        MapModelPort model = new AiGameMasterApiConfiguration().mapModelPort(fixed("not json"), mapper);
        MapModelPort.MapOutput output = model.generate(new MapModelPort.MapInput(
                "map", "room", "{\"gridWidth\":3,\"gridHeight\":3,\"gridOriginX\":0,\"gridOriginY\":0,\"gridCellSize\":10,\"gridConfirmed\":true}", image));
        org.junit.jupiter.api.Assertions.assertTrue(output.boundaries().contains("1,1,HORIZONTAL,WALL,false"));
    }

    @Test
    void keepsAuthoredLinesWhenTheImageDraftIsRegenerated() throws Exception {
        MapModelPort model = new AiGameMasterApiConfiguration().mapModelPort(fixed("not json"), mapper);
        MapModelPort.MapOutput output = model.generate(new MapModelPort.MapInput("map", "room",
                "{\"gridWidth\":3,\"gridHeight\":3,\"gridConfirmed\":true,\"authoredBoundaries\":[\"0,0,VERTICAL,DOOR,false\"],\"authoredObstacles\":[\"2,2\"],\"authoredDoors\":[\"1,2\"],\"authoredPlayerStart\":\"0,1\"}"));
        assertEquals(java.util.List.of("2,2"), output.obstacles());
        assertEquals(java.util.List.of("1,2"), output.doors());
        assertEquals(java.util.List.of("0,0,VERTICAL,DOOR,false"), output.boundaries());
        assertEquals("0,1", output.playerStart());
    }

    @Test
    void createsVisualBoundaryDraftWhenProviderTimesOut() throws Exception {
        BufferedImage image = new BufferedImage(50, 40, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(java.awt.Color.BLACK);
        graphics.fillRect(15, 14, 10, 3);
        graphics.fillRect(24, 15, 3, 10);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());

        GmCompletionAdapter failing = new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser) {
                throw new IllegalStateException("provider unavailable");
            }
        };
        MapModelPort model = new AiGameMasterApiConfiguration().mapModelPort(failing, mapper);

        MapModelPort.MapOutput output = model.generate(new MapModelPort.MapInput(
                "map", "room",
                "{\"gridWidth\":4,\"gridHeight\":3,\"gridOriginX\":5,\"gridOriginY\":5,\"gridCellSize\":10,\"gridConfirmed\":true}",
                dataUri));

        assertEquals(4, output.width());
        assertEquals(3, output.height());
        org.junit.jupiter.api.Assertions.assertTrue(output.boundaries().contains("1,1,HORIZONTAL,WALL,false"));
        org.junit.jupiter.api.Assertions.assertTrue(output.boundaries().contains("2,1,VERTICAL,WALL,false"));
        org.junit.jupiter.api.Assertions.assertTrue(output.candidates().stream().anyMatch(candidate ->
                candidate.x() == 1 && candidate.y() == 1
                        && candidate.confidence() > 0.8
                        && candidate.evidence().contains("continuous-edge")));
    }

    @Test
    void doesNotAnalyseAnUnconfirmedGridOrAStaleImageRevision() throws Exception {
        String image = dataUri(30, 30, graphics -> {
            graphics.setColor(java.awt.Color.BLACK);
            graphics.fillRect(10, 9, 10, 3);
        });
        MapModelPort model = new AiGameMasterApiConfiguration().mapModelPort(unavailableProvider(), mapper);

        MapModelPort.MapOutput unconfirmed = model.generate(new MapModelPort.MapInput(
                "map", "room", "{\"gridWidth\":3,\"gridHeight\":3,\"gridOriginX\":0,\"gridOriginY\":0,\"gridCellSize\":10,\"gridConfirmed\":false}", image));
        assertEquals(java.util.List.of(), unconfirmed.boundaries());

        MapModelPort.MapOutput stale = model.generate(new MapModelPort.MapInput(
                "map", "room", "{\"gridWidth\":3,\"gridHeight\":3,\"gridOriginX\":0,\"gridOriginY\":0,\"gridCellSize\":10,\"gridConfirmed\":true,\"imageRevision\":\"stale\"}", image));
        assertEquals(java.util.List.of(), stale.boundaries());
    }

    @Test
    void keepsAPlainLightDecorationOutOfWallCandidatesAndDoesNotCallAWhiteGapADoor() throws Exception {
        String image = dataUri(40, 30, graphics -> {
            graphics.setColor(new java.awt.Color(120, 120, 120));
            graphics.fillRect(10, 9, 10, 2);
            graphics.setColor(java.awt.Color.BLACK);
            graphics.fillRect(0, 14, 10, 3);
            graphics.fillRect(20, 14, 10, 3);
        });
        MapModelPort model = new AiGameMasterApiConfiguration().mapModelPort(unavailableProvider(), mapper);
        MapModelPort.MapOutput output = model.generate(new MapModelPort.MapInput(
                "map", "room", "{\"gridWidth\":4,\"gridHeight\":3,\"gridOriginX\":0,\"gridOriginY\":0,\"gridCellSize\":10,\"gridConfirmed\":true}", image));

        org.junit.jupiter.api.Assertions.assertTrue(output.boundaries().stream().noneMatch(value -> value.startsWith("1,1,HORIZONTAL,WALL")));
        org.junit.jupiter.api.Assertions.assertTrue(output.boundaries().stream().noneMatch(value -> value.startsWith("1,1,HORIZONTAL,DOOR")));
    }

    @Test
    void doesNotTurnAContinuousDarkFloorIntoAnAutomaticWall() throws Exception {
        String image = dataUri(40, 30, graphics -> {
            graphics.setColor(new java.awt.Color(18, 18, 18));
            graphics.fillRect(0, 0, 40, 30);
        });
        MapModelPort model = new AiGameMasterApiConfiguration().mapModelPort(unavailableProvider(), mapper);

        MapModelPort.MapOutput output = model.generate(new MapModelPort.MapInput(
                "map", "room", "{\"gridWidth\":4,\"gridHeight\":3,\"gridOriginX\":0,\"gridOriginY\":0,\"gridCellSize\":10,\"gridConfirmed\":true}", image));

        assertEquals(java.util.List.of(), output.boundaries());
    }

    @Test
    void doesNotTreatTheImageCanvasBorderAsWalls() throws Exception {
        String image = dataUri(30, 30, graphics -> {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, 30, 30);
            graphics.setColor(java.awt.Color.BLACK);
            graphics.fillRect(0, 0, 30, 3);
            graphics.fillRect(0, 0, 3, 30);
            graphics.fillRect(0, 27, 30, 3);
            graphics.fillRect(27, 0, 3, 30);
        });
        MapModelPort model = new AiGameMasterApiConfiguration().mapModelPort(unavailableProvider(), mapper);

        MapModelPort.MapOutput output = model.generate(new MapModelPort.MapInput(
                "map", "room", "{\"gridWidth\":3,\"gridHeight\":3,\"gridOriginX\":0,\"gridOriginY\":0,\"gridCellSize\":10,\"gridConfirmed\":true}", image));

        assertEquals(java.util.List.of(), output.boundaries());
    }

    private static GmCompletionAdapter unavailableProvider() {
        return new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser) {
                throw new IllegalStateException("provider unavailable");
            }
        };
    }

    private static String dataUri(int width, int height, java.util.function.Consumer<java.awt.Graphics2D> draw) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        draw.accept(graphics);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static GmCompletionAdapter fixed(String response) {
        return new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser) {
                return parser.parse(response);
            }
        };
    }
}
