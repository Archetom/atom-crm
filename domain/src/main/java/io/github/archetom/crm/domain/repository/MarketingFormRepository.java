package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.MarketingForm;
import io.github.archetom.crm.domain.model.MarketingFormStatus;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Optional;

public interface MarketingFormRepository {

    MarketingForm save(TenantId tenantId, MarketingForm form);

    Optional<MarketingForm> findById(TenantId tenantId, MarketingFormId formId);

    /** Lock one form root for a state-changing operation in the current transaction. */
    Optional<MarketingForm> findByIdForUpdate(TenantId tenantId, MarketingFormId formId);

    Optional<MarketingForm> findActiveByPublicKey(TenantId tenantId, String publicKey);

    /** Locks the exact public form regardless of lifecycle so a draft can still be withdrawn. */
    Optional<MarketingForm> findByPublicKeyForUpdate(TenantId tenantId, String publicKey);

    /** Lock an active public form until its submission transaction has finished. */
    Optional<MarketingForm> findActiveByPublicKeyForUpdate(
            TenantId tenantId, String publicKey);

    PageResult<MarketingForm> findPage(
            TenantId tenantId, MarketingFormStatus status, int page, int size);

    boolean existsActiveReferencingField(TenantId tenantId, FieldCode fieldCode);
}
