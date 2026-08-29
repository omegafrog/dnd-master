package com.dndmaster.adventure;

import com.dndmaster.adventure.application.runtime.RuntimeTurnFailurePersistence;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeTurnFailurePersistenceTest {
    @Test
    void failure_persistence_uses_an_independent_transaction() throws Exception {
        Method persist = RuntimeTurnFailurePersistence.class.getMethod("persist",
                com.dndmaster.adventure.application.runtime.RuntimeTurn.class);
        assertEquals(Propagation.REQUIRES_NEW, persist.getAnnotation(Transactional.class).propagation());
    }
}
