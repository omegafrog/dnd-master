package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionCandidate;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageCompilationService;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ResolutionKind;
import com.dndmaster.adventure.domain.scenario.ResolutionVisibility;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ScenarioResolutionEvaluationCorpusTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void advancedResolutionCorpusRemainsStable(EvaluationCase evaluation) {
        ScenarioPackageCompilationService service = new ScenarioPackageCompilationService(new InMemoryPackageRepository());
        KnowledgeDocumentId documentId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = ScenarioSourceBundle.create(
                ScenarioBundleId.generate(),
                new OwnerPlayerId(UUID.randomUUID()),
                new ScenarioSourceBundleRevision(1, List.of(new com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection(
                        documentId, ScenarioBundleDocumentRole.MAIN_SCENARIO, KnowledgeDocumentStatus.INDEXED,
                        "scenario.txt", "STORYBOOK", 1))));

        var unit = service.compile(bundle, List.of(candidate(evaluation, documentId))).units().getFirst();

        assertEquals(evaluation.expectedStatus(), unit.status().name());
        assertEquals(evaluation.expectedCapabilities(), unit.runtimeCapabilities());
        assertEquals(evaluation.stepKinds(), unit.detail().steps().stream()
                .map(step -> step.kind().name()).toList());
        assertEquals(evaluation.outcomeLabels(), unit.detail().outcomes().stream()
                .map(ScenarioResolutionDetail.Outcome::label).toList());
        assertEquals(evaluation.randomTableRanges(), unit.detail().randomTable().stream()
                .map(ScenarioResolutionDetail.TableEntry::range).toList());
        Assertions.assertThat(unit.validationMessages()).containsExactlyElementsOf(evaluation.expectedMessages());
    }

    static List<EvaluationCase> cases() throws Exception {
        try (var input = ScenarioResolutionEvaluationCorpusTest.class.getResourceAsStream(
                        "/scenario-resolution-evaluation/advanced-resolution-cases.tsv");
                var reader = new BufferedReader(new InputStreamReader(
                        java.util.Objects.requireNonNull(input), StandardCharsets.UTF_8))) {
            return reader.lines().skip(1).filter(line -> !line.isBlank()).map(ScenarioResolutionEvaluationCorpusTest::parse).toList();
        }
    }

    private static EvaluationCase parse(String line) {
        String[] columns = line.split("\\t", -1);
        return new EvaluationCase(
                columns[0],
                ResolutionKind.valueOf(columns[1]),
                columns[2],
                blankToNull(columns[3]),
                blankToNull(columns[4]) == null ? null : Integer.valueOf(columns[4]),
                blankToNull(columns[5]),
                blankToNull(columns[6]),
                blankToNull(columns[7]),
                blankToNull(columns[8]),
                blankToNull(columns[9]),
                blankToNull(columns[10]),
                split(columns[11]),
                split(columns[12]),
                split(columns[13]),
                blankToNull(columns[14]),
                columns[15],
                split(columns[16]),
                splitMessages(columns[17]));
    }

    private static ResolutionCandidate candidate(EvaluationCase evaluation, KnowledgeDocumentId documentId) {
        List<ScenarioSourceReference> refs = List.of(new ScenarioSourceReference(documentId, 1, "page:1:span:1"));
        List<ScenarioResolutionDetail.Step> steps = new ArrayList<>();
        for (int index = 0; index < evaluation.stepKinds().size(); index++) {
            steps.add(new ScenarioResolutionDetail.Step(
                    "step-" + index,
                    ResolutionKind.valueOf(evaluation.stepKinds().get(index)),
                    index == 0 ? evaluation.abilityOrSkill() : null,
                    index == 0 ? evaluation.dc() : null,
                    index == 0 ? evaluation.diceExpression() : evaluation.diceExpression(),
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    refs));
        }
        List<ScenarioResolutionDetail.Outcome> outcomes = evaluation.outcomeLabels().stream()
                .map(label -> new ScenarioResolutionDetail.Outcome(label.toLowerCase(), label, label, refs))
                .toList();
        List<ScenarioResolutionDetail.TableEntry> randomTable = evaluation.randomTableRanges().stream()
                .map(range -> new ScenarioResolutionDetail.TableEntry(range, "range " + range, refs))
                .toList();
        return new ResolutionCandidate(
                evaluation.kind(),
                evaluation.abilityOrSkill(),
                evaluation.dc() == null ? (com.dndmaster.adventure.domain.scenario.SaveDc) null
                        : com.dndmaster.adventure.domain.scenario.SaveDc.fixed(evaluation.dc()),
                evaluation.diceExpression(),
                ResolutionVisibility.GM_REFERENCE,
                evaluation.quote(),
                refs,
                "evaluation-corpus-v1",
                new ScenarioResolutionDetail(
                        evaluation.triggerCondition(),
                        evaluation.actor(),
                        evaluation.roller(),
                        evaluation.instructionVisibility(),
                        evaluation.resultVisibility(),
                        List.of(),
                        null,
                        null,
                        steps,
                        outcomes,
                        randomTable,
                        evaluation.tableCoverage()));
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(";;"));
    }

    private static List<String> splitMessages(String value) {
        return "-".equals(value) ? List.of() : split(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record EvaluationCase(
            String id,
            ResolutionKind kind,
            String quote,
            String abilityOrSkill,
            Integer dc,
            String diceExpression,
            String triggerCondition,
            String actor,
            String roller,
            String instructionVisibility,
            String resultVisibility,
            List<String> stepKinds,
            List<String> outcomeLabels,
            List<String> randomTableRanges,
            String tableCoverage,
            String expectedStatus,
            List<String> expectedCapabilities,
            List<String> expectedMessages) {
        @Override
        public String toString() {
            return id;
        }
    }

    private static final class InMemoryPackageRepository implements ScenarioPackageRepository {
        private final Map<String, ScenarioPackage> packages = new HashMap<>();

        @Override
        public Optional<ScenarioPackage> findByInputFingerprint(String fingerprint) {
            return Optional.ofNullable(packages.get(fingerprint));
        }

        @Override
        public Optional<ScenarioPackage> findById(UUID packageId) {
            return packages.values().stream().filter(scenarioPackage -> scenarioPackage.packageId().equals(packageId)).findFirst();
        }

        @Override
        public void save(ScenarioPackage scenarioPackage) {
            packages.put(scenarioPackage.inputFingerprint(), scenarioPackage);
        }
    }
}
