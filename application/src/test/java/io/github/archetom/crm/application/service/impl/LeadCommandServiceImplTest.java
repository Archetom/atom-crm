package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadBatchTagRequest;
import io.github.archetom.crm.api.dto.request.LeadCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadCreateRequest;
import io.github.archetom.crm.api.dto.request.LeadOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadUpdateRequest;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.port.out.LeadStructureLock;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.LeadAccessResolver;
import io.github.archetom.crm.application.service.LeadConversionService;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.LeadRepository;
import io.github.archetom.crm.domain.repository.LeadStarRepository;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LeadCommandServiceImplTest {

    private final AuthenticatedCaller caller = mock(AuthenticatedCaller.class);
    private final LeadRepository leadRepository = mock(LeadRepository.class);
    private final LeadStarRepository starRepository = mock(LeadStarRepository.class);
    private final LeadAccessResolver accessResolver = mock(LeadAccessResolver.class);
    private final CustomerAccessResolver customerAccessResolver = mock(CustomerAccessResolver.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final LeadApplicationSupport support = mock(LeadApplicationSupport.class);
    private final LeadConversionService conversionService = mock(LeadConversionService.class);
    private final LeadStructureLock structureLock = mock(LeadStructureLock.class);
    private final CustomizationObjectLock objectLock = mock(CustomizationObjectLock.class);
    private final CommandServiceTemplate commandTemplate = mock(CommandServiceTemplate.class);
    private LeadCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LeadCommandServiceImpl(leadRepository, starRepository, accessResolver,
                customerAccessResolver, customerRepository, support, mock(AttachmentParentLifecycle.class), conversionService,
                structureLock, objectLock, commandTemplate);
        when(commandTemplate.execute(any(), any())).thenAnswer(invocation -> {
            ServiceOperation<?> operation = invocation.getArgument(1);
            operation.validate();
            return new Result<>();
        });
    }

    @Test
    void everyCommandRejectsBeforeRepositoryAccessWhenItsAuthorityIsMissing() {
        List<AuthorizationCase> cases = List.of(
                new AuthorizationCase(AuthorityCatalog.LEADS_WRITE, LeadAccessMode.WRITE,
                        () -> service.create(caller, new LeadCreateRequest())),
                new AuthorizationCase(AuthorityCatalog.LEADS_WRITE, LeadAccessMode.WRITE,
                        () -> service.update(caller, 1L, new LeadUpdateRequest())),
                new AuthorizationCase(AuthorityCatalog.LEADS_DELETE, LeadAccessMode.OWNER_SCOPE,
                        () -> service.delete(caller, 1L, 0L)),
                new AuthorizationCase(AuthorityCatalog.LEADS_WRITE, LeadAccessMode.WRITE,
                        () -> service.updateState(caller, 1L, new LeadStateUpdateRequest())),
                new AuthorizationCase(AuthorityCatalog.LEADS_ASSIGN, LeadAccessMode.OWNER_SCOPE,
                        () -> service.updateOwner(caller, 1L, new LeadOwnerUpdateRequest())),
                new AuthorizationCase(AuthorityCatalog.LEADS_WRITE, LeadAccessMode.WRITE,
                        () -> service.replaceTags(caller, 1L, new LeadTagsUpdateRequest())),
                new AuthorizationCase(AuthorityCatalog.LEADS_WRITE, LeadAccessMode.WRITE,
                        () -> service.changeTags(caller, new LeadBatchTagRequest())),
                new AuthorizationCase(AuthorityCatalog.LEADS_ASSIGN, LeadAccessMode.OWNER_SCOPE,
                        () -> service.replaceCollaborators(
                                caller, 1L, new LeadCollaboratorsUpdateRequest())),
                new AuthorizationCase(AuthorityCatalog.LEADS_READ, LeadAccessMode.READ,
                        () -> service.exitCollaborator(caller, 1L, 0L)),
                new AuthorizationCase(AuthorityCatalog.LEADS_WRITE, LeadAccessMode.WRITE,
                        () -> service.updateParent(caller, 1L, new LeadParentUpdateRequest())),
                new AuthorizationCase(AuthorityCatalog.LEADS_READ, LeadAccessMode.READ,
                        () -> service.setStarred(caller, 1L, new LeadStarUpdateRequest())));

        for (AuthorizationCase authorizationCase : cases) {
            NonRetryableApplicationException denied = new NonRetryableApplicationException(
                    ApplicationErrorCode.ACCESS_DENIED);
            when(accessResolver.access(caller, authorizationCase.authority(),
                    authorizationCase.mode())).thenThrow(denied);

            assertThrows(NonRetryableApplicationException.class,
                    authorizationCase.invocation()::run);
            verify(accessResolver).access(eq(caller), eq(authorizationCase.authority()),
                    eq(authorizationCase.mode()));
            org.mockito.Mockito.reset(accessResolver);
        }
        verifyNoInteractions(leadRepository, starRepository, customerRepository,
                structureLock, objectLock);
    }

    private record AuthorizationCase(
            String authority, LeadAccessMode mode, Runnable invocation) {
    }
}
