package com.dndmaster.appall;

import com.dndmaster.aigamemaster.infrastructure.endpoint.RelayProviderConnectionAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RelayProviderConnectionAdapterWiringTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues(
                    "ai-game-master.relay.base-url=http://localhost:8080",
                    "ai-game-master.integration.internal-token=test-token",
                    "ai-game-master.relay.timeout=PT3M")
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(RelayProviderConnectionAdapter.class);

    @Test
    void adapterIsCreatedWithItsConfiguredConstructor() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RelayProviderConnectionAdapter.class);
        });
    }
}
