package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.application.port.out.call.CallFactInboxStore;
import io.github.archetom.crm.domain.valueobject.ConnectorId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CallFactInboxMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CallFactInboxPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** MySQL unique key is the final delivery deduplication arbiter across adapter replicas. */
@Repository
@RequiredArgsConstructor
public class CallFactInboxRepositoryImpl implements CallFactInboxStore {
    private final CallFactInboxMapper mapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome accept(TenantId tenantId, ConnectorId connectorId,
                                    byte[] deliveryRef, byte[] factDigest, Instant now) {
        if (tenantId == null || connectorId == null || deliveryRef == null || deliveryRef.length != 32
                || factDigest == null || factDigest.length != 32 || now == null) {
            throw new IllegalArgumentException("call fact inbox input is invalid");
        }
        CallFactInboxPO value = new CallFactInboxPO();
        value.setTenantId(tenantId.getValue()); value.setConnectorId(connectorId.getValue());
        value.setDeliveryRef(deliveryRef.clone()); value.setFactDigest(factDigest.clone());
        value.setOutcome("APPLIED"); value.setCreatedTime(time(now)); value.setSettledTime(time(now));
        try {
            if (mapper.insert(value) != 1) throw new IllegalStateException("call fact inbox insert failed");
            return Outcome.APPLIED;
        } catch (DuplicateKeyException exception) {
            CallFactInboxPO existing = mapper.selectOne(new LambdaQueryWrapper<CallFactInboxPO>()
                    .eq(CallFactInboxPO::getTenantId, tenantId.getValue())
                    .eq(CallFactInboxPO::getConnectorId, connectorId.getValue())
                    .eq(CallFactInboxPO::getDeliveryRef, deliveryRef));
            if (existing == null) throw exception;
            return MessageDigest.isEqual(existing.getFactDigest(), factDigest) ? Outcome.DUPLICATE : Outcome.CONFLICT;
        }
    }

    private static LocalDateTime time(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
}
