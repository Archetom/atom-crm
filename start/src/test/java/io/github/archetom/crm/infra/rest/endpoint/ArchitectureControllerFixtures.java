package io.github.archetom.crm.infra.rest.endpoint;

import io.github.archetom.crm.infra.persistence.mysql.po.BasePO;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RestController;

/** The controller rule must also apply outside the conventional controller package. */
public final class ArchitectureControllerFixtures {
    @RestController
    @Profile("architecture-negative-fixtures")
    public static class LeakingController {
        public BasePO read() {
            return null;
        }
    }
}
