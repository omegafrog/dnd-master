package com.dndmaster.ruleknowledge.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dndmaster.ruleknowledge.application.registration.RulebookFileStorage;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookFile;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class RuleKnowledgeAssetControllerTest {
    @Test
    void rendersPdfAssetWhenLegacyRegistrationFilenameDoesNotHavePdfExtension() throws Exception {
        RulebookId id = RulebookId.generate();
        byte[] pdf = pdfWithImage();
        var registration = new StoredRulebookRegistration(id, new OwnerPlayerId(UUID.randomUUID()), "op", "hash",
                RulebookFormat.PDF, pdf.length, "stored", ProcessingStatus.INDEXED, ExtractionStatus.SUCCESS,
                "content", List.of(), null, 1, Instant.now(), Instant.now(), DocumentType.STORYBOOK, "legacy-rulebook");
        var registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(id)).thenReturn(java.util.Optional.of(registration));
        var storage = mock(RulebookFileStorage.class);
        when(storage.read(new StoredRulebookFile("stored"))).thenReturn(pdf);
        var controller = new RuleKnowledgeAssetController(registrations, storage, "token");

        ResponseEntity<byte[]> response = controller.asset(id.value(), "page 1 image 1", "token");

        assertEquals("image/png", response.getHeaders().getFirst("Content-Type"));
        assertTrue(response.getBody().length > 0);
        assertTrue(response.getBody()[0] == (byte) 0x89);
    }

    @Test
    void readsPublishedCatalogAssetWhenLocalRegistrationFileIsMissing() throws Exception {
        RulebookId id = RulebookId.generate();
        byte[] pdf = pdfWithImage();
        Path assets = Files.createTempDirectory("catalog-assets");
        Files.write(assets.resolve("published.pdf"), pdf);
        var registration = new StoredRulebookRegistration(id, new OwnerPlayerId(UUID.randomUUID()), "op", "hash",
                RulebookFormat.PDF, pdf.length, "missing", ProcessingStatus.INDEXED, ExtractionStatus.SUCCESS,
                "content", List.of(), null, 1, Instant.now(), Instant.now(), DocumentType.RULEBOOK, "published.pdf");
        var registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(id)).thenReturn(java.util.Optional.of(registration));
        var storage = mock(RulebookFileStorage.class);
        when(storage.read(new StoredRulebookFile("missing"))).thenThrow(new RuntimeException("volume unavailable"));
        var controller = new RuleKnowledgeAssetController(registrations, storage, "token", assets);

        ResponseEntity<byte[]> response = controller.asset(id.value(), "page 1 image 1", "token");

        assertEquals("image/png", response.getHeaders().getFirst("Content-Type"));
        assertTrue(response.getBody().length > 0);
    }

    @Test
    void servesAllowlistedPotentBrewMapWhenPublishedRegistrationIsMissing() throws Exception {
        Path assets = Files.createTempDirectory("catalog-assets");
        Files.write(assets.resolve("892902-A_Most_Potent_Brew.pdf"), pdfWithImage());
        var registrations = mock(RulebookRegistrationRepository.class);
        var controller = new RuleKnowledgeAssetController(registrations, mock(RulebookFileStorage.class), "token", assets);

        ResponseEntity<byte[]> response = controller.asset(UUID.randomUUID(), "page 1 image 1", "token");

        assertEquals("image/png", response.getHeaders().getFirst("Content-Type"));
        assertTrue(response.getBody().length > 0);
    }

    private static byte[] pdfWithImage() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xffff0000);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            var pdfImage = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.drawImage(pdfImage, 0, 0, 2, 2);
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
