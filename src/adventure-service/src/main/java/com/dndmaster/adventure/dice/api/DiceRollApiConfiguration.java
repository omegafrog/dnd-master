package com.dndmaster.adventure.dice.api;

import com.dndmaster.adventure.dice.application.DiceRandomPort;
import com.dndmaster.adventure.dice.application.DiceRollApplicationService;
import com.dndmaster.adventure.dice.application.DiceRollRepository;
import com.dndmaster.adventure.dice.infrastructure.persistence.PostgresDiceRollRepository;
import com.dndmaster.adventure.dice.infrastructure.random.SecureDiceRandomAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    DiceRollApplicationService diceRollApplicationService(
            DiceRollRepository repository, DiceRandomPort randomPort) {
        return new DiceRollApplicationService(repository, randomPort);
    }
}
