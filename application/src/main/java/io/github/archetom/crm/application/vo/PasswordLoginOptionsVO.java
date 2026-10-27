package io.github.archetom.crm.application.vo;

import java.util.List;

/** Short-lived login challenge and the verified account's tenant choices. */
public record PasswordLoginOptionsVO(String loginChallenge, List<PasswordLoginTenantVO> tenants) {
    @Override
    public String toString() {
        return "PasswordLoginOptionsVO[REDACTED]";
    }
}
