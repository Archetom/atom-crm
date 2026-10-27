package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.repository.ContactStarRepository;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactStarMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactStarPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ContactStarRepositoryImpl implements ContactStarRepository {

    private final ContactStarMapper mapper;

    @Override
    public void setStarred(
            TenantId tenantId, MemberId memberId, ContactId contactId, boolean starred) {
        require(tenantId, memberId, contactId);
        if (!starred) {
            mapper.delete(filter(tenantId, memberId).eq(
                    ContactStarPO::getContactId, contactId.getValue()));
            return;
        }
        ContactStarPO po = new ContactStarPO();
        po.setTenantId(tenantId.getValue());
        po.setActorId(memberId.getValue());
        po.setContactId(contactId.getValue());
        po.setCreatedTime(LocalDateTime.now());
        try {
            mapper.insert(po);
        } catch (DuplicateKeyException ignored) {
            // Idempotent personal preference update.
        }
    }

    @Override
    public boolean isStarred(TenantId tenantId, MemberId memberId, ContactId contactId) {
        require(tenantId, memberId, contactId);
        return mapper.selectCount(filter(tenantId, memberId)
                .eq(ContactStarPO::getContactId, contactId.getValue())) > 0;
    }

    @Override
    public Set<ContactId> findStarred(
            TenantId tenantId, MemberId memberId, Collection<ContactId> contactIds) {
        if (tenantId == null || memberId == null) {
            throw new IllegalArgumentException("tenant and member must not be null");
        }
        List<Long> ids = contactIds == null ? List.of() : contactIds.stream()
                .filter(java.util.Objects::nonNull).map(ContactId::getValue).distinct().toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return mapper.selectList(filter(tenantId, memberId)
                        .in(ContactStarPO::getContactId, ids))
                .stream().map(value -> new ContactId(value.getContactId()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private LambdaQueryWrapper<ContactStarPO> filter(TenantId tenantId, MemberId memberId) {
        return new LambdaQueryWrapper<ContactStarPO>()
                .eq(ContactStarPO::getTenantId, tenantId.getValue())
                .eq(ContactStarPO::getActorId, memberId.getValue());
    }

    private void require(TenantId tenantId, MemberId memberId, ContactId contactId) {
        if (tenantId == null || memberId == null || contactId == null) {
            throw new IllegalArgumentException("tenant, member, and contact must not be null");
        }
    }
}
