package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import com.dndmaster.adventure.application.runtime.*;
import org.junit.jupiter.api.Test;

class RuntimeTurnLockServiceTest {
    @Test void acquire_is_mutually_exclusive_and_release_allows_retry() {
        UUID session = UUID.randomUUID(); UUID turn = UUID.randomUUID();
        FakeRepository repository = new FakeRepository(session);
        RuntimeTurnLockService service = new RuntimeTurnLockService(repository);
        service.acquire(session, turn);
        assertThrows(GmTurnAlreadyInProgressException.class, () -> service.acquire(session, UUID.randomUUID()));
        service.release(session);
        service.acquire(session, UUID.randomUUID());
        assertTrue(repository.current(session).orElseThrow().turnInProgress());
    }

    private static final class FakeRepository implements GmProviderBindingRepository {
        private final UUID session; private final AtomicReference<ProviderBinding> value;
        FakeRepository(UUID session) { this.session = session; value = new AtomicReference<>(new ProviderBinding(session, new GmProviderSelection("codex-cli", "gpt-5.6-luna", "none"), 0, false)); }
        public Optional<ProviderBinding> current(UUID id) { return id.equals(session) ? Optional.of(value.get()) : Optional.empty(); }
        public void save(ProviderBinding binding) { value.set(binding); }
        public boolean compareAndSet(UUID id, long version, ProviderBinding updated) { return id.equals(session) && value.compareAndSet(value.get(), updated); }
    }
}
