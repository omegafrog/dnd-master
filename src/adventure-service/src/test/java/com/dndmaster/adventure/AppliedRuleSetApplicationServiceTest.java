package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.ruleset.AppliedRuleSetApplicationService;
import com.dndmaster.adventure.application.ruleset.AppliedRuleSetRepository;
import com.dndmaster.adventure.application.ruleset.CreateAppliedRuleSetCommand;
import com.dndmaster.adventure.application.ruleset.RulebookOwnershipDeniedException;
import com.dndmaster.adventure.application.ruleset.RulebookOwnershipHttpPort;
import com.dndmaster.adventure.domain.ruleset.AdventureId;
import com.dndmaster.adventure.domain.ruleset.AppliedRuleSet;
import com.dndmaster.adventure.domain.ruleset.DndEdition;
import com.dndmaster.adventure.domain.ruleset.OwnerPlayerId;
import com.dndmaster.adventure.domain.ruleset.RuleApplicationDeniedException;
import com.dndmaster.adventure.domain.ruleset.RuleApplicationRequest;
import com.dndmaster.adventure.domain.ruleset.RuleSetId;
import com.dndmaster.adventure.domain.ruleset.RulebookId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppliedRuleSetApplicationServiceTest {
    private static final DndEdition EDITION = new DndEdition("D&D 5E 2024");

    @Test
    void savesExactlyOneEditionAndAtLeastOneOwnedRulebook() {
        OwnerPlayerId owner = owner();
        RulebookId rulebook = rulebook();
        InMemoryRuleSetRepository repository = new InMemoryRuleSetRepository();
        AppliedRuleSetApplicationService service = service(repository, new OwnershipMock(owner, Set.of(rulebook)));

        AppliedRuleSet saved = service.saveRuleSet(command(owner, List.of(rulebook)));

        assertEquals(EDITION, saved.edition());
        assertEquals(1, saved.selectedRulebooks().values().size());
        assertEquals(saved, repository.findById(saved.id()).orElseThrow());
    }

    @Test
    void rejectsEmptyRulebookSelection() {
        OwnerPlayerId owner = owner();
        AppliedRuleSetApplicationService service =
                service(new InMemoryRuleSetRepository(), new OwnershipMock(owner, Set.of()));

        assertThrows(IllegalArgumentException.class, () -> service.saveRuleSet(command(owner, List.of())));
    }

    @Test
    void ownershipHttpCheckRejectsAnotherPlayersRulebook() {
        OwnerPlayerId owner = owner();
        RulebookId foreignRulebook = rulebook();
        OwnershipMock ownership = new OwnershipMock(owner(), Set.of(foreignRulebook));
        AppliedRuleSetApplicationService service = service(new InMemoryRuleSetRepository(), ownership);

        assertThrows(
                RulebookOwnershipDeniedException.class,
                () -> service.saveRuleSet(command(owner, List.of(foreignRulebook))));
        assertEquals(1, ownership.calls);
    }

    @Test
    void allowsTheSameRuleSetToBeAppliedToMultipleAdventures() {
        OwnerPlayerId owner = owner();
        RulebookId rulebook = rulebook();
        InMemoryRuleSetRepository repository = new InMemoryRuleSetRepository();
        AppliedRuleSetApplicationService service = service(repository, new OwnershipMock(owner, Set.of(rulebook)));
        RuleSetId sharedRuleSetId = RuleSetId.generate();

        AppliedRuleSet first = service.saveRuleSet(
                sharedRuleSetId, command(owner, List.of(rulebook)));
        AppliedRuleSet second = service.saveRuleSet(
                sharedRuleSetId, command(owner, List.of(rulebook)));

        assertEquals(first.id(), second.id());
        assertEquals(first, second);
    }

    @Test
    void rejectsRuleApplicationOutsideSelectedRulebookOrEdition() {
        OwnerPlayerId owner = owner();
        RulebookId selected = rulebook();
        InMemoryRuleSetRepository repository = new InMemoryRuleSetRepository();
        AppliedRuleSetApplicationService service = service(repository, new OwnershipMock(owner, Set.of(selected)));
        AppliedRuleSet saved = service.saveRuleSet(command(owner, List.of(selected)));

        assertThrows(
                RuleApplicationDeniedException.class,
                () -> service.useRuleSet(
                        saved.id(), owner, new RuleApplicationRequest(EDITION, rulebook())));
        assertThrows(
                RuleApplicationDeniedException.class,
                () -> service.useRuleSet(
                        saved.id(), owner, new RuleApplicationRequest(new DndEdition("D&D 3.5E"), selected)));
    }

    private static AppliedRuleSetApplicationService service(
            AppliedRuleSetRepository repository, RulebookOwnershipHttpPort ownership) {
        return new AppliedRuleSetApplicationService(repository, ownership);
    }

    private static CreateAppliedRuleSetCommand command(OwnerPlayerId owner, List<RulebookId> rulebooks) {
        return new CreateAppliedRuleSetCommand(
                new AdventureId(UUID.randomUUID()), owner, EDITION, rulebooks);
    }

    private static OwnerPlayerId owner() {
        return new OwnerPlayerId(UUID.randomUUID());
    }

    private static RulebookId rulebook() {
        return new RulebookId(UUID.randomUUID());
    }

    private static final class OwnershipMock implements RulebookOwnershipHttpPort {
        private final OwnerPlayerId owner;
        private final Set<RulebookId> ownedRulebooks;
        private int calls;

        private OwnershipMock(OwnerPlayerId owner, Set<RulebookId> ownedRulebooks) {
            this.owner = owner;
            this.ownedRulebooks = Set.copyOf(ownedRulebooks);
        }

        @Override
        public boolean isOwnedBy(RulebookId rulebookId, OwnerPlayerId ownerPlayerId) {
            calls++;
            return owner.equals(ownerPlayerId) && ownedRulebooks.contains(rulebookId);
        }
    }

    private static final class InMemoryRuleSetRepository implements AppliedRuleSetRepository {
        private final Map<RuleSetId, AppliedRuleSet> values = new HashMap<>();

        @Override
        public Optional<AppliedRuleSet> findById(RuleSetId ruleSetId) {
            return Optional.ofNullable(values.get(ruleSetId));
        }

        @Override
        public void save(AppliedRuleSet ruleSet) {
            values.put(ruleSet.id(), ruleSet);
        }
    }
}
