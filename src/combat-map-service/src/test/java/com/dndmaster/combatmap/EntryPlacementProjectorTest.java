package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.combatmap.application.view.EntryPlacementProjector;
import com.dndmaster.combatmap.application.view.MapGenerationRequest;
import com.dndmaster.combatmap.application.view.MapImageEvidence;
import com.dndmaster.combatmap.domain.GridPosition;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class EntryPlacementProjectorTest {
    @Test
    void projectsNormalizedImagePointThroughConfirmedNonZeroGridOrigin() throws Exception {
        MapGenerationRequest request = new MapGenerationRequest("entry", "", 4, 3, 30, 5,
                List.of(), List.of(), null, image(200, 100), 20, 10, 20, "20,10,80,60");

        var position = new EntryPlacementProjector().geometry(request).orElseThrow().project(.35, .35);

        assertEquals(new GridPosition(2, 1), position.orElseThrow());
    }

    @Test
    void rejectsPointOutsideConfirmedGridInsteadOfClampingIt() throws Exception {
        MapGenerationRequest request = new MapGenerationRequest("entry", "", 4, 3, 30, 5,
                List.of(), List.of(), null, image(200, 100), 20, 10, 20, "");

        assertTrue(new EntryPlacementProjector().geometry(request).orElseThrow().project(.99, .99).isEmpty());
    }

    @Test
    void snapsSmallModelErrorAtGridEdgeToNearestEdgeCell() throws Exception {
        MapGenerationRequest request = new MapGenerationRequest("entry", "", 4, 3, 30, 5,
                List.of(), List.of(), null, image(200, 100), 20, 10, 20, "");

        assertEquals(new GridPosition(3, 2),
                new EntryPlacementProjector().geometry(request).orElseThrow().project(.51, .71).orElseThrow());
    }

    private static MapImageEvidence image(int width, int height) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MapImageEvidence("image/png", bytes.toByteArray());
    }
}
