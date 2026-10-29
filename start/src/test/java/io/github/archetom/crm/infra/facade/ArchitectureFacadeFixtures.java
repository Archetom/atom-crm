package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.application.service.UserService;
import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.infra.persistence.mysql.po.BasePO;

/** Test-only counterexamples for the existing CRM facade boundary. */
public final class ArchitectureFacadeFixtures {
    public static class DomainDependency {
        AggregateRoot<String> aggregate;
    }

    public static class PersistenceDependency {
        BasePO row;
    }

    public static class ApplicationDependency {
        UserService service;
    }
}
