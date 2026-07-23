package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.application.registration.RulebookContentExtractor;
import com.dndmaster.ruleknowledge.application.registration.RulebookFileStorage;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationApplicationService;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookFile;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionFailure;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class RulebookExtractionTest {
    private static final OwnerPlayerId OWNER =
            new OwnerPlayerId(UUID.fromString("36c6b6fd-2f36-4b79-9a91-614f9e35bd91"));

    @ParameterizedTest
    @EnumSource(RulebookFormat.class)
    void extractsEverySupportedFormatThroughPorts(RulebookFormat format) throws Exception {
        var storage = new FakeFileStorage();
        var service = new RulebookRegistrationApplicationService(storage, new FakeExtractor());

        var registration = service.uploadRulebook(OWNER, format, fixture("valid-rulebook.txt"));
        service.extractContent(registration);

        assertEquals(ProcessingStatus.EXTRACTED, registration.rulebook().processingStatus());
        assertEquals("Core movement rules.", registration.rulebook().extractionResult().orElseThrow().content().orElseThrow());
        assertTrue(registration.rulebook().isEligibleForSplitting());
        assertEquals(format, registration.rulebook().format());
    }

    @ParameterizedTest
    @MethodSource("rejectedFiles")
    void rejectsEncryptedCorruptAndUnprocessableFiles(
            String fixture, RulebookFormat format, ExtractionFailure expectedFailure) throws Exception {
        var service = new RulebookRegistrationApplicationService(new FakeFileStorage(), new FakeExtractor());

        var registration = service.uploadRulebook(OWNER, format, fixture(fixture));
        service.extractContent(registration);

        assertEquals(ProcessingStatus.REJECTED, registration.rulebook().processingStatus());
        assertEquals(expectedFailure, registration.rulebook().extractionResult().orElseThrow().failure().orElseThrow());
        assertFalse(registration.rulebook().isEligibleForSplitting());
        assertThrows(IllegalStateException.class, () -> service.extractContent(registration));
    }

    @Test
    void partialExtractionExposesMissingLocationsAndRequiresConfirmation() throws Exception {
        var service = new RulebookRegistrationApplicationService(new FakeFileStorage(), new FakeExtractor());
        var registration = service.uploadRulebook(OWNER, RulebookFormat.TXT, fixture("partial-rulebook.txt"));

        service.extractContent(registration);

        var extraction = registration.rulebook().extractionResult().orElseThrow();
        assertEquals(List.of("chapter 2", "appendix A"), extraction.missingLocations());
        assertFalse(extraction.confirmedByPlayer());
        assertEquals(ProcessingStatus.PARTIAL_AWAITING_CONFIRMATION, registration.rulebook().processingStatus());
        assertFalse(registration.rulebook().isEligibleForSplitting());

        service.confirmPartialExtraction(registration);

        assertTrue(registration.rulebook().extractionResult().orElseThrow().confirmedByPlayer());
        assertEquals(ProcessingStatus.PARTIAL_CONFIRMED, registration.rulebook().processingStatus());
        assertTrue(registration.rulebook().isEligibleForSplitting());
        assertThrows(IllegalStateException.class, () -> service.confirmPartialExtraction(registration));
    }

    @Test
    void sameOperationKeyAndSameBytesReuseRegistrationRegardlessOfFormat() throws Exception {
        var service = new RulebookRegistrationApplicationService(new FakeFileStorage(), new FakeExtractor());
        byte[] content = fixture("valid-rulebook.txt");

        var first = service.uploadRulebook("op-1", OWNER, RulebookFormat.PDF, content);
        var duplicate = service.uploadRulebook("op-1", OWNER, RulebookFormat.TXT, content);

        assertEquals(first.rulebook().id(), duplicate.rulebook().id());
        assertEquals(first.storedFile().key(), duplicate.storedFile().key());
    }

    private static Stream<Arguments> rejectedFiles() {
        return Stream.of(
                Arguments.of("encrypted.pdf", RulebookFormat.PDF, ExtractionFailure.ENCRYPTED),
                Arguments.of("corrupt.docx", RulebookFormat.DOCX, ExtractionFailure.CORRUPT),
                Arguments.of("unprocessable.txt", RulebookFormat.TXT, ExtractionFailure.UNPROCESSABLE));
    }

    private static byte[] fixture(String name) throws IOException {
        try (var input = RulebookExtractionTest.class.getResourceAsStream("/fixtures/files/" + name)) {
            if (input == null) {
                throw new IllegalArgumentException("missing fixture " + name);
            }
            return input.readAllBytes();
        }
    }

    private static final class FakeFileStorage implements RulebookFileStorage {
        private final Map<String, byte[]> files = new HashMap<>();

        @Override
        public StoredRulebookFile store(RulebookId rulebookId, byte[] content) {
            String key = rulebookId.value().toString();
            files.put(key, Arrays.copyOf(content, content.length));
            return new StoredRulebookFile(key);
        }

        @Override
        public byte[] read(StoredRulebookFile storedFile) {
            byte[] content = files.get(storedFile.key());
            return Arrays.copyOf(content, content.length);
        }
    }

    private static final class FakeExtractor implements RulebookContentExtractor {
        @Override
        public ExtractionResult extract(RulebookFormat format, byte[] content) {
            String marker = new String(content, StandardCharsets.UTF_8).trim();
            if (marker.startsWith("VALID:")) {
                return ExtractionResult.success(marker.substring("VALID:".length()).trim());
            }
            if (marker.startsWith("PARTIAL:")) {
                return ExtractionResult.partial(
                        marker.substring("PARTIAL:".length()).trim(), List.of("chapter 2", "appendix A"));
            }
            return ExtractionResult.failed(ExtractionFailure.valueOf(marker));
        }
    }
}
