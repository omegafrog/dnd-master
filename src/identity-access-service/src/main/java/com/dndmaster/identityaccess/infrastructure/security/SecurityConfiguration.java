package com.dndmaster.identityaccess.infrastructure.security;

import com.dndmaster.identityaccess.application.PlayerAccessApplicationService;
import com.dndmaster.identityaccess.domain.access.OwnershipAccessPolicy;
import com.dndmaster.identityaccess.infrastructure.persistence.IdentityAccessRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationManager authenticationManager(
            IdentityAccessRepository repository, PasswordEncoder passwordEncoder) {
        return new ProviderManager(new DatabaseAuthenticationProvider(repository, passwordEncoder));
    }

    @Bean
    OwnershipAccessPolicy ownershipAccessPolicy() {
        return new OwnershipAccessPolicy();
    }

    @Bean
    PlayerAccessApplicationService playerAccessApplicationService(OwnershipAccessPolicy ownershipAccessPolicy) {
        return new PlayerAccessApplicationService(ownershipAccessPolicy);
    }

    @Bean
    @Order(2)
    SecurityFilterChain identityAccessSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(request -> request.anyRequest().permitAll())
                .build();
    }
}
