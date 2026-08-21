package com.dndmaster.appall;

import com.dndmaster.character.api.ApiContractExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ApiContractExceptionHandlerBeanNameTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ApiContractExceptionHandler.class,
                    com.dndmaster.combatmap.api.ApiContractExceptionHandler.class
            );

    @Test
    void adviceBeansHaveDistinctNamesWhenAppAllLoadsBothModules() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("characterApiContractExceptionHandler");
            assertThat(context).hasBean("combatMapApiContractExceptionHandler");
        });
    }
}
