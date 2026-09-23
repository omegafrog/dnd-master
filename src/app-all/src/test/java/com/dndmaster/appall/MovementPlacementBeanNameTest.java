package com.dndmaster.appall;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.adventure.api.AdventureApiConfiguration;
import com.dndmaster.aigamemaster.api.AiGameMasterApiConfiguration;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;

class MovementPlacementBeanNameTest {
    @Test
    void movementPlacementFactoriesHaveDistinctSpringBeanNames() {
        assertThat(beanMethodNames(AdventureApiConfiguration.class))
                .contains("movementPlacementModelPort");
        assertThat(beanMethodNames(AiGameMasterApiConfiguration.class))
                .contains("aiGameMasterMovementPlacementModelPort")
                .doesNotContain("movementPlacementModelPort");
    }

    private static String[] beanMethodNames(Class<?> configurationType) {
        return Arrays.stream(configurationType.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .map(Method::getName)
                .toArray(String[]::new);
    }
}
