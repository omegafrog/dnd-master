package com.dndmaster.diceroll.api;

import com.dndmaster.diceroll.application.DiceRandomPort;
import com.dndmaster.diceroll.application.DiceRollApplicationService;
import com.dndmaster.diceroll.application.DiceRollRepository;
import com.dndmaster.diceroll.infrastructure.persistence.PostgresDiceRollRepository;
import com.dndmaster.diceroll.infrastructure.random.SecureDiceRandomAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class DiceRollApiConfiguration {

    @Bean
    DiceRollRepository diceRollRepository(DataSource dataSource) {
        return new PostgresDiceRollRepository(dataSource);
    }

    @Bean
    DiceRandomPort diceRandomPort() {
        return new SecureDiceRandomAdapter();
    }

    @Bean
    ApiRequestGuard diceApiRequestGuard(@Value("${dice.integration.internal-token:${INTERNAL_SERVICE_TOKEN:local-dev-internal-token}}") String token) { return new ApiRequestGuard(token); }

    @Bean
    DiceRollApplicationService diceRollApplicationService(
            DiceRollRepository repository, DiceRandomPort randomPort) {
        return new DiceRollApplicationService(repository, randomPort);
    }

    @Bean
    DiceRollController diceRollController(DiceRollApplicationService diceRollService, ApiRequestGuard requestGuard) {
        return new DiceRollController(diceRollService, requestGuard);
    }
}
