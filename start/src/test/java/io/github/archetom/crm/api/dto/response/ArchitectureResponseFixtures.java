package io.github.archetom.crm.api.dto.response;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.infra.persistence.mysql.po.BasePO;

import java.util.List;

/** Generic signatures must not hide domain or persistence types from the API rule. */
public final class ArchitectureResponseFixtures {
    public record LeakingDomainResponse(List<AggregateRoot<String>> items) {
    }

    public record LeakingPersistenceResponse(List<BasePO> items) {
    }

    public record NestedDetails(String label) {
    }

    public record ScalarResponse(List<String> items) {
    }
}
