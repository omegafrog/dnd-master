package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.registration.RulebookFileStorage;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookFile;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/story-sources")
public final class RuleKnowledgeAssetController {
    private final RulebookRegistrationRepository registrations;
    private final RulebookFileStorage storage;
    private final String internalToken;

    public RuleKnowledgeAssetController(
            RulebookRegistrationRepository registrations,
            RulebookFileStorage storage,
            @org.springframework.beans.factory.annotation.Value("${INTERNAL_SERVICE_TOKEN:}") String internalToken) {
        this.registrations = registrations;
        this.storage = storage;
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    @GetMapping(value = "/{documentId}/assets", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> asset(
            @org.springframework.web.bind.annotation.PathVariable UUID documentId,
            @RequestParam String locator,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) throws IOException {
        if (internalToken.isBlank() || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
        }
        StoredRulebookRegistration registration = registrations.findById(new RulebookId(documentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "source document not found"));
        byte[] source = storage.read(new StoredRulebookFile(registration.storageKey()));
        RenderedAsset rendered = render(source, registration.originalFilename(), locator);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(rendered.contentType())).body(rendered.bytes());
    }

    private static RenderedAsset render(byte[] source, String filename, String locator) throws IOException {
        if (!filename.toLowerCase().endsWith(".pdf")) {
            return new RenderedAsset(source, filename.toLowerCase().endsWith(".jpg") ? "image/jpeg" : "image/png");
        }
        int page = numberAfter(locator, "page", 1) - 1;
        try (PDDocument document = Loader.loadPDF(source)) {
            if (locator.toLowerCase().contains(" image ")) {
                int target = numberAfter(locator, "image", 1);
                int current = 0;
                for (var name : document.getPage(page).getResources().getXObjectNames()) {
                    var object = document.getPage(page).getResources().getXObject(name);
                    if (object instanceof PDImageXObject image && ++current == target) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        ImageIO.write(image.getImage(), "png", out);
                        return new RenderedAsset(out.toByteArray(), "image/png");
                    }
                }
            }
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(page, 144);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return new RenderedAsset(out.toByteArray(), "image/png");
        }
    }

    private static int numberAfter(String value, String marker, int fallback) {
        String[] parts = value.toLowerCase().split(" ");
        for (int i = 0; i + 1 < parts.length; i++) if (parts[i].equals(marker)) try { return Integer.parseInt(parts[i + 1]); } catch (NumberFormatException ignored) { }
        return fallback;
    }

    private record RenderedAsset(byte[] bytes, String contentType) { }
}
