package com.dndmaster.appall;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.adventure.api.AdventureApiConfiguration;
import com.dndmaster.character.api.CharacterManagementApiConfiguration;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;

class PlayerSessionLookupBeanNameTest {
    @Test
    void sessionLookupFactoriesHaveDistinctBoundedContextNames() {
        assertThat(beanMethodNames(AdventureApiConfiguration.class))
                .contains("adventurePlayerSessionLookupPort")
                .doesNotContain("playerSessionLookupPort");
        assertThat(beanMethodNames(CharacterManagementApiConfiguration.class))
                .contains("characterPlayerSessionLookupPort")
                .doesNotContain("playerSessionLookupPort");
    }

    private static String[] beanMethodNames(Class<?> configurationType) {
        return Arrays.stream(configurationType.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .map(Method::getName)
                .toArray(String[]::new);
    }
}
