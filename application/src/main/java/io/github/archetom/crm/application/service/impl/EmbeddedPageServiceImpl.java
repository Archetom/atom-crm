package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.EmbeddedPageCreateRequest;
import io.github.archetom.crm.api.dto.request.EmbeddedPageQueryRequest;
import io.github.archetom.crm.api.dto.request.EmbeddedPageUpdateRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.EmbeddedPageService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.BusinessObjectVO;
import io.github.archetom.crm.application.vo.EmbeddedPageConfigurationOptionsVO;
import io.github.archetom.crm.application.vo.EmbeddedPageVO;
import io.github.archetom.crm.application.vo.RenderedEmbeddedPageVO;
import io.github.archetom.crm.domain.entity.EmbeddedPage;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ClientPlatform;
import io.github.archetom.crm.domain.model.EmbeddedPageStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.EmbeddedPageRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.EmbeddedPageId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmbeddedPageServiceImpl implements EmbeddedPageService {

    private final EmbeddedPageRepository pageRepository;
    private final OrganizationAccessGuard accessGuard;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<EmbeddedPageConfigurationOptionsVO> configurationOptions(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.EMBEDDED_PAGE_CONFIGURATION_OPTIONS,
                new ServiceOperation<>() {
                    @Override
                    public void validate() {
                        accessGuard.requireActiveMembership(caller, AuthorityCatalog.EMBEDDED_PAGES_READ);
                    }

                    @Override
                    public EmbeddedPageConfigurationOptionsVO execute() {
                        return new EmbeddedPageConfigurationOptionsVO(
                                Arrays.stream(BusinessObjectType.values())
                                        .map(value -> new BusinessObjectVO(value.name(), displayName(value)))
                                        .toList(),
                                Arrays.stream(ClientPlatform.values()).map(Enum::name).toList(),
                                Arrays.stream(EmbeddedPageStatus.values()).map(Enum::name).toList());
                    }
                });
    }

    @Override
    public Result<Pager<EmbeddedPageVO>> query(
            AuthenticatedCaller caller, EmbeddedPageQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.EMBEDDED_PAGE_QUERY, new ServiceOperation<>() {
            private TenantId tenantId;
            private BusinessObjectType objectType;
            private ClientPlatform platform;
            private EmbeddedPageStatus status;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.EMBEDDED_PAGES_READ);
                require(request, "Embedded page query");
                validatePage(request);
                objectType = parseOptional(BusinessObjectType.class, request.getObjectType(),
                        "Business object type");
                platform = parseOptional(ClientPlatform.class, request.getPlatform(), "Client platform");
                status = parseOptional(EmbeddedPageStatus.class, request.getStatus(), "Embedded page status");
            }

            @Override
            public Pager<EmbeddedPageVO> execute() {
                PageResult<EmbeddedPage> page = pageRepository.findPage(tenantId, objectType, platform,
                        status, request.getPage(), request.getSize());
                Pager<EmbeddedPageVO> result = new Pager<>();
                result.setPageNum(page.page());
                result.setPageSize(page.size());
                result.setTotalNum(page.total());
                result.setObjectList(page.items().stream()
                        .map(EmbeddedPageServiceImpl.this::toVO).toList());
                return result;
            }
        });
    }

    @Override
    public Result<EmbeddedPageVO> create(
            AuthenticatedCaller caller, EmbeddedPageCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.EMBEDDED_PAGE_CREATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private BusinessObjectType objectType;
            private ClientPlatform platform;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.EMBEDDED_PAGES_WRITE);
                require(request, "Embedded page create request");
                objectType = parse(BusinessObjectType.class, request.getObjectType(), "Business object type");
                platform = parse(ClientPlatform.class, request.getPlatform(), "Client platform");
            }

            @Override
            public EmbeddedPageVO execute() {
                EmbeddedPage page = EmbeddedPage.create(tenantId, new MemberId(caller.actorId()),
                        objectType, platform, request.getName(), request.getDescription(),
                        request.getUrlTemplate(), value(request.getSortOrder()));
                return toVO(pageRepository.save(tenantId, page));
            }
        });
    }

    @Override
    public Result<EmbeddedPageVO> update(
            AuthenticatedCaller caller, Long pageId, EmbeddedPageUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.EMBEDDED_PAGE_UPDATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private EmbeddedPageId id;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.EMBEDDED_PAGES_WRITE);
                id = new EmbeddedPageId(pageId);
                require(request, "Embedded page update request");
            }

            @Override
            public EmbeddedPageVO execute() {
                EmbeddedPage page = requirePage(tenantId, id);
                requireVersion(page, request.getVersion());
                page.update(request.getName(), request.getDescription(), request.getUrlTemplate(),
                        value(request.getSortOrder()), parse(EmbeddedPageStatus.class,
                                request.getStatus(), "Embedded page status"));
                return toVO(pageRepository.save(tenantId, page));
            }
        });
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long pageId, Long version) {
        return commandTemplate.execute(UseCaseOperation.EMBEDDED_PAGE_DELETE, new ServiceOperation<>() {
            private TenantId tenantId;
            private EmbeddedPageId id;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.EMBEDDED_PAGES_WRITE);
                id = new EmbeddedPageId(pageId);
            }

            @Override
            public Void execute() {
                EmbeddedPage page = requirePage(tenantId, id);
                requireVersion(page, version);
                page.assertDeletable();
                pageRepository.delete(tenantId, page);
                return null;
            }
        });
    }

    @Override
    public Result<List<RenderedEmbeddedPageVO>> listActive(
            AuthenticatedCaller caller, String objectType, Long recordId, String platform) {
        return queryTemplate.execute(UseCaseOperation.EMBEDDED_PAGE_RENDER_LIST, new ServiceOperation<>() {
            private TenantId tenantId;
            private BusinessObjectType type;
            private ClientPlatform clientPlatform;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.EMBEDDED_PAGES_READ);
                type = parse(BusinessObjectType.class, objectType, "Business object type");
                clientPlatform = parse(ClientPlatform.class, platform, "Client platform");
                if (recordId == null || recordId <= 0) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID, "Record ID must be positive");
                }
            }

            @Override
            public List<RenderedEmbeddedPageVO> execute() {
                return pageRepository.findActive(tenantId, type, clientPlatform).stream()
                        .map(page -> new RenderedEmbeddedPageVO(page.getId().getValue(), page.getName(),
                                page.getDescription(), page.render(recordId), page.getSortOrder()))
                        .toList();
            }
        });
    }

    private EmbeddedPage requirePage(TenantId tenantId, EmbeddedPageId id) {
        return pageRepository.findById(tenantId, id)
                .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Embedded page was not found"));
    }

    private EmbeddedPageVO toVO(EmbeddedPage page) {
        return new EmbeddedPageVO(page.getId().getValue(), page.getObjectType().name(),
                page.getPlatform().name(), page.getName(), page.getDescription(), page.getUrlTemplate(),
                page.getSortOrder(), page.getStatus().name(), page.getCreatedBy().getValue(),
                page.getVersion(), page.getCreatedTime(), page.getUpdatedTime());
    }

    private void validatePage(QueryRequest request) {
        if (request.getPage() == null) {
            request.setPage(QueryRequest.DEFAULT_PAGE);
        }
        if (request.getSize() == null) {
            request.setSize(QueryRequest.DEFAULT_SIZE);
        }
        if (request.getPage() < 1 || request.getPage() > QueryRequest.MAX_PAGE
                || request.getSize() < 1 || request.getSize() > QueryRequest.MAX_SIZE) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Page request is outside the supported range");
        }
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String displayName(BusinessObjectType value) {
        return Arrays.stream(value.name().toLowerCase(Locale.ROOT).split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .reduce((left, right) -> left + " " + right)
                .orElse(value.name());
    }

    private void requireVersion(EmbeddedPage page, Long version) {
        if (version == null || !version.equals(page.getVersion())) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT, "Embedded page version is stale");
        }
    }

    private void require(Object value, String description) {
        if (value == null) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " must not be empty");
        }
    }

    private <E extends Enum<E>> E parseOptional(Class<E> type, String value, String description) {
        return value == null || value.isBlank() ? null : parse(type, value, description);
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value, String description) {
        if (value == null || value.isBlank()) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " is required");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " is invalid");
        }
    }

    private NonRetryableApplicationException error(ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }
}
