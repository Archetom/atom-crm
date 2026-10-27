package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.application.vo.TenantRoleVO;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorizationAssemblerTest {

    @Test
    void systemRoleResponsesExposeTheirCodeDefinedGrants() {
        TenantRole owner = TenantRole.createSystem(new TenantId(9L), SystemRoleKey.TENANT_OWNER);
        owner.onPersisted(new RoleId(3L), 0L, LocalDateTime.now(), LocalDateTime.now());

        TenantRoleVO response = AuthorizationAssembler.toVO(owner);

        assertEquals(AuthorityCatalog.tenantAssignable().size(), response.grants().size());
    }
}
