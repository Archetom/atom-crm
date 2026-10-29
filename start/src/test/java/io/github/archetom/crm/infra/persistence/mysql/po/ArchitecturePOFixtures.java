package io.github.archetom.crm.infra.persistence.mysql.po;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;

import java.util.List;

public final class ArchitecturePOFixtures {
    public static class DetachedPO {
    }

    public static class LeakingPO extends BasePO {
        List<AggregateRoot<String>> aggregates;
    }

    public static class ScalarPO extends BasePO {
        String label;
    }
}
