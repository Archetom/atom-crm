package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.CustomizationDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ClientPlatform;
import io.github.archetom.crm.domain.model.EmbeddedPageStatus;
import io.github.archetom.crm.domain.valueobject.EmbeddedPageId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.net.URI;
import java.time.LocalDateTime;

/** Tenant-managed client link template. The server validates and renders it but never fetches it. */
@Getter
public class EmbeddedPage extends AggregateRoot<EmbeddedPageId> {

    private static final String RECORD_TOKEN = "{recordId}";

    private EmbeddedPageId id;
    private TenantId tenantId;
    private MemberId createdBy;
    private BusinessObjectType objectType;
    private ClientPlatform platform;
    private String name;
    private String description;
    private String urlTemplate;
    private int sortOrder;
    private EmbeddedPageStatus status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private EmbeddedPage() {
    }

    public record Snapshot(EmbeddedPageId id, TenantId tenantId, MemberId createdBy,
                           BusinessObjectType objectType, ClientPlatform platform,
                           String name, String description, String urlTemplate,
                           int sortOrder, EmbeddedPageStatus status, Long version,
                           LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static EmbeddedPage create(TenantId tenantId, MemberId createdBy,
                                      BusinessObjectType objectType, ClientPlatform platform,
                                      String name, String description, String urlTemplate,
                                      int sortOrder) {
        EmbeddedPage page = new EmbeddedPage();
        page.tenantId = requireTenant(tenantId);
        page.createdBy = requireMember(createdBy);
        page.objectType = requireObjectType(objectType);
        page.platform = requirePlatform(platform);
        page.status = EmbeddedPageStatus.INACTIVE;
        page.apply(name, description, urlTemplate, sortOrder, EmbeddedPageStatus.INACTIVE);
        page.createdTime = page.updatedTime;
        return page;
    }

    public static EmbeddedPage reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new CustomizationDomainException("embedded page snapshot must not be empty");
        }
        EmbeddedPage page = new EmbeddedPage();
        page.id = snapshot.id();
        page.tenantId = requireTenant(snapshot.tenantId());
        page.createdBy = requireMember(snapshot.createdBy());
        page.objectType = requireObjectType(snapshot.objectType());
        page.platform = requirePlatform(snapshot.platform());
        page.name = requireName(snapshot.name());
        page.description = normalizeDescription(snapshot.description());
        page.urlTemplate = requireUrlTemplate(snapshot.urlTemplate());
        page.sortOrder = requireOrder(snapshot.sortOrder());
        page.status = snapshot.status() == null ? EmbeddedPageStatus.INACTIVE : snapshot.status();
        page.restoreVersion(snapshot.version());
        page.createdTime = snapshot.createdTime();
        page.updatedTime = snapshot.updatedTime();
        return page;
    }

    public void update(String name, String description, String urlTemplate,
                       int sortOrder, EmbeddedPageStatus status) {
        apply(name, description, urlTemplate, sortOrder, status);
    }

    public String render(long recordId) {
        if (recordId <= 0) {
            throw new CustomizationDomainException("record ID must be positive");
        }
        return urlTemplate.replace(RECORD_TOKEN, Long.toString(recordId));
    }

    public boolean isActive() {
        return status == EmbeddedPageStatus.ACTIVE;
    }

    public void assertDeletable() {
        if (isActive()) {
            throw new CustomizationDomainException("active embedded pages must be deactivated before deletion");
        }
    }

    public void onPersisted(EmbeddedPageId id, Long version,
                            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new CustomizationDomainException("persisted embedded page ID is invalid");
        }
        this.id = id;
        restoreVersion(version);
        if (createdTime != null) {
            this.createdTime = createdTime;
        }
        if (updatedTime != null) {
            this.updatedTime = updatedTime;
        }
    }

    private void apply(String name, String description, String urlTemplate,
                       int sortOrder, EmbeddedPageStatus status) {
        if (status == null) {
            throw new CustomizationDomainException("embedded page status must not be empty");
        }

        String nextName = requireName(name);
        String nextDescription = normalizeDescription(description);
        String nextUrlTemplate = requireUrlTemplate(urlTemplate);
        int nextSortOrder = requireOrder(sortOrder);

        this.name = nextName;
        this.description = nextDescription;
        this.urlTemplate = nextUrlTemplate;
        this.sortOrder = nextSortOrder;
        this.status = status;
        updatedTime = LocalDateTime.now();
    }

    private static String requireUrlTemplate(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 1000) {
            throw new CustomizationDomainException("embedded page URL template is invalid");
        }
        String normalized = value.trim();
        String withoutToken = normalized.replace(RECORD_TOKEN, "1");
        if (withoutToken.contains("{") || withoutToken.contains("}")) {
            throw new CustomizationDomainException("embedded page URL contains an unknown placeholder");
        }
        try {
            URI uri = URI.create(withoutToken);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new CustomizationDomainException("embedded page URL must be HTTPS without user info");
            }
        } catch (IllegalArgumentException exception) {
            throw new CustomizationDomainException("embedded page URL template is invalid");
        }
        return normalized;
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new CustomizationDomainException("embedded page name is invalid");
        }
        return value.trim();
    }

    private static String normalizeDescription(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 500) {
            throw new CustomizationDomainException("embedded page description is too long");
        }
        return normalized;
    }

    private static int requireOrder(int value) {
        if (value < 0 || value > 10000) {
            throw new CustomizationDomainException("embedded page order is invalid");
        }
        return value;
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new CustomizationDomainException("embedded page tenant must not be empty");
        }
        return value;
    }

    private static MemberId requireMember(MemberId value) {
        if (value == null) {
            throw new CustomizationDomainException("embedded page creator must not be empty");
        }
        return value;
    }

    private static BusinessObjectType requireObjectType(BusinessObjectType value) {
        if (value == null) {
            throw new CustomizationDomainException("embedded page object type must not be empty");
        }
        return value;
    }

    private static ClientPlatform requirePlatform(ClientPlatform value) {
        if (value == null) {
            throw new CustomizationDomainException("embedded page platform must not be empty");
        }
        return value;
    }
}
