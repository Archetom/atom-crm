package io.github.archetom.crm.application.config;

import io.github.archetom.crm.domain.factory.UserFactory;
import io.github.archetom.crm.domain.policy.PasswordPolicy;
import io.github.archetom.crm.domain.repository.UserRepository;
import io.github.archetom.crm.domain.service.PasswordHasher;
import io.github.archetom.crm.domain.service.UserDomainService;
import io.github.archetom.crm.domain.service.impl.UserDomainServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the framework-neutral domain model into the application runtime.
 */
@Configuration(proxyBeanMethods = false)
public class DomainConfiguration {

    @Bean
    PasswordPolicy passwordPolicy() {
        return new PasswordPolicy();
    }

    @Bean
    UserDomainService userDomainService(UserRepository repository, PasswordHasher passwordHasher) {
        return new UserDomainServiceImpl(repository, passwordHasher);
    }

    @Bean
    UserFactory userFactory(UserDomainService domainService,
                            PasswordPolicy passwordPolicy) {
        return new UserFactory(domainService, passwordPolicy);
    }

    /** One injectable UTC clock for request-scoped calendar calculations. */
    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }
}
