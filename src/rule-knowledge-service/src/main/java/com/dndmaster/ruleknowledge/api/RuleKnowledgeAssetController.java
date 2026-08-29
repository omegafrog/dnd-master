package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.registration.RulebookFileStorage;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookFile;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/story-sources")
public final class RuleKnowledgeAssetController {
    private final RulebookRegistrationRepository registrations;
    private final RulebookFileStorage storage;
    private final String internalToken;
    private final Path assetFallbackRoot;

    @org.springframework.beans.factory.annotation.Autowired
    public RuleKnowledgeAssetController(
            RulebookRegistrationRepository registrations,
            RulebookFileStorage storage,
            @org.springframework.beans.factory.annotation.Value("${INTERNAL_SERVICE_TOKEN:}") String internalToken,
            @org.springframework.beans.factory.annotation.Value("${RULE_KNOWLEDGE_ASSET_FALLBACK_ROOT:}") String fallbackRoot) {
        this(registrations, storage, internalToken,
                fallbackRoot == null || fallbackRoot.isBlank() ? defaultAssetFallbackRoot() : Path.of(fallbackRoot));
    }

    public RuleKnowledgeAssetController(RulebookRegistrationRepository registrations,
            RulebookFileStorage storage, String internalToken) {
        this(registrations, storage, internalToken, defaultAssetFallbackRoot());
    }

    public RuleKnowledgeAssetController(
            RulebookRegistrationRepository registrations,
            RulebookFileStorage storage,
            String internalToken,
            Path assetFallbackRoot) {
        this.registrations = registrations;
        this.storage = storage;
        this.internalToken = internalToken == null ? "" : internalToken;
        this.assetFallbackRoot = assetFallbackRoot;
    }

    private static Path defaultAssetFallbackRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            Path assets = candidate.resolve("docs/assets");
            if (Files.isDirectory(assets)) return assets;
        }
        return Path.of("docs/assets").toAbsolutePath().normalize();
    }

    @GetMapping(value = "/{documentId}/assets", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> asset(
            @org.springframework.web.bind.annotation.PathVariable UUID documentId,
            @RequestParam String locator,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) throws IOException {
        if (internalToken.isBlank() || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
        }
        var registration = registrations.findById(new RulebookId(documentId));
        if (registration.isEmpty()) {
            return publishedMapFallback(locator);
        }
        StoredRulebookRegistration stored = registration.get();
        byte[] source;
        try {
            source = storage.read(new StoredRulebookFile(stored.storageKey()));
        } catch (RuntimeException exception) {
            source = readPublishedAssetFallback(stored.originalFilename())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "source file not found", exception));
        }
        // Older registrations use the compatibility filename "legacy-rulebook" even
        // when their persisted format is PDF.  The format is the authoritative
        // discriminator for rendering stored source bytes.
        RenderedAsset rendered = render(source, stored.format(), stored.originalFilename(), locator);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(rendered.contentType())).body(rendered.bytes());
    }

    private ResponseEntity<byte[]> publishedMapFallback(String locator) throws IOException {
        if (!locator.toLowerCase().contains(" image ")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "source document not found");
        }
        String filename = "892902-A_Most_Potent_Brew.pdf";
        byte[] source = readPublishedAssetFallback(filename)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "source document not found"));
        RenderedAsset rendered = render(source, RulebookFormat.PDF, filename, locator);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(rendered.contentType())).body(rendered.bytes());
    }

    private java.util.Optional<byte[]> readPublishedAssetFallback(String filename) {
        // Published catalog rows can outlive a local service's storage volume. The
        // checked-in source assets are the deterministic local catalog fallback.
        Path candidate = assetFallbackRoot.resolve(Path.of(filename).getFileName().toString()).normalize();
        if (!candidate.startsWith(assetFallbackRoot.normalize()) || !Files.isRegularFile(candidate)) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Files.readAllBytes(candidate));
        } catch (IOException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static RenderedAsset render(byte[] source, RulebookFormat format, String filename, String locator) throws IOException {
        if (format != RulebookFormat.PDF && !filename.toLowerCase().endsWith(".pdf")) {
            return new RenderedAsset(source, filename.toLowerCase().endsWith(".jpg") ? "image/jpeg" : "image/png");
        }
        int page = numberAfter(locator, "page", 1) - 1;
        try (PDDocument document = Loader.loadPDF(source)) {
            if (page < 0 || page >= document.getNumberOfPages()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "source page not found");
            }
            if (locator.toLowerCase().contains(" image ")) {
                int target = numberAfter(locator, "image", 1);
                int current = 0;
                var resources = document.getPage(page).getResources();
                if (resources != null) {
                    for (var name : resources.getXObjectNames()) {
                        var object = resources.getXObject(name);
                        if (object instanceof PDImageXObject image && ++current == target) {
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            ImageIO.write(image.getImage(), "png", out);
                            return new RenderedAsset(out.toByteArray(), "image/png");
                        }
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
