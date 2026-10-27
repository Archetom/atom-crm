package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/** Per-replica, non-queueing memory admission shared by completed CRM CSV exports. */
@Component
public class CustomerCsvExportAdmission {

    private static final int MAX_ACTIVE_PER_REPLICA = 4;
    private final Set<MemberKey> activeMembers = new HashSet<>();
    private int activeExports;

    public synchronized Lease tryAcquire(TenantId tenantId, MemberId memberId) {
        if (tenantId == null || memberId == null || activeExports >= MAX_ACTIVE_PER_REPLICA) {
            return null;
        }
        MemberKey key = new MemberKey(tenantId.getValue(), memberId.getValue());
        if (!activeMembers.add(key)) {
            return null;
        }
        activeExports++;
        return new Lease(this, key);
    }

    private synchronized void release(MemberKey key) {
        if (activeMembers.remove(key)) {
            activeExports--;
        }
    }

    private record MemberKey(Long tenantId, Long memberId) {
    }

    public static final class Lease implements AutoCloseable {
        private final CustomerCsvExportAdmission owner;
        private final MemberKey key;
        private boolean released;

        private Lease(CustomerCsvExportAdmission owner, MemberKey key) {
            this.owner = owner;
            this.key = key;
        }

        @Override
        public synchronized void close() {
            if (!released) {
                released = true;
                owner.release(key);
            }
        }
    }
}
