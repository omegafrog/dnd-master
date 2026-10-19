package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.scenario.MapSafetyStatus;
import com.dndmaster.adventure.domain.scenario.MapSourceReference;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PreparationMapImageSourceTest {
    @Test
    void preparationImageUsesMapAssetLocatorAndKeepsSourceDocument() {
        UUID documentId = UUID.randomUUID();
        MapDefinition map = new MapDefinition(UUID.randomUUID(), "map.pdf", "page 1 image 1",
                new MapDefinition.MapGrid(0, 0, 1, 0, "5ft"), List.of(), List.of(), List.of(),
                new MapSourceReference(new KnowledgeDocumentId(documentId), 1,
                        "page=1:chunk=source-evidence", "1"), .9, MapSafetyStatus.SAFE);

        AdventureController.MapImageSource imageSource = AdventureController.sourceForPreparationImage(map);

        assertEquals(documentId, imageSource.documentId());
        assertEquals("page 1 image 1", imageSource.locator());
    }
}
