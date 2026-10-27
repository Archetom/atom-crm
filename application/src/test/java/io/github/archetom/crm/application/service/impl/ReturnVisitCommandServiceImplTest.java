package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReturnVisitCreateRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitUpdateRequest;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.ReturnVisitAccessResolver;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.model.ReturnVisitAccessMode;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.ReturnVisitRepository;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReturnVisitCommandServiceImplTest {
    private final AuthenticatedCaller caller = mock(AuthenticatedCaller.class);
    private final ReturnVisitRepository visits = mock(ReturnVisitRepository.class);
    private final ReturnVisitAccessResolver accessResolver = mock(ReturnVisitAccessResolver.class);
    private final OrganizationAccessGuard accessGuard = mock(OrganizationAccessGuard.class);
    private final CustomizationObjectLock objectLock = mock(CustomizationObjectLock.class);
    private final CommandServiceTemplate commands = mock(CommandServiceTemplate.class);
    private ReturnVisitCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReturnVisitCommandServiceImpl(visits, mock(ContactRepository.class), mock(ContractRepository.class),
                mock(OrganizationMembershipRepository.class), accessResolver, accessGuard,
                mock(BusinessRecordAccessGuard.class), mock(ReturnVisitApplicationSupport.class),
                mock(ActivityAuditService.class), mock(AttachmentParentLifecycle.class), mock(FileFieldBindingService.class),
                objectLock, commands);
        when(commands.execute(any(), any())).thenAnswer(invocation -> {
            ServiceOperation<?> operation = invocation.getArgument(1);
            operation.validate();
            return new Result<>();
        });
        when(accessResolver.access(caller, AuthorityCatalog.RETURN_VISITS_WRITE, ReturnVisitAccessMode.WRITE))
                .thenReturn(mock(ReturnVisitAccessResolver.ReturnVisitAccess.class));
    }

    @Test
    void createWithFileDraftRequiresCurrentFilesWrite() {
        ReturnVisitCreateRequest request = new ReturnVisitCreateRequest().setFileFieldDraftId(1L);
        when(accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE)).thenThrow(denied());

        assertThrows(NonRetryableApplicationException.class, () -> service.create(caller, request));

        verify(accessGuard).requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
        verifyNoInteractions(visits, objectLock);
    }

    @Test
    void updateWithFileValuesRequiresCurrentFilesWrite() {
        ReturnVisitUpdateRequest request = new ReturnVisitUpdateRequest()
                .setFileFields(java.util.Map.of("cf_file", java.util.List.of()));
        when(accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE)).thenThrow(denied());

        assertThrows(NonRetryableApplicationException.class, () -> service.update(caller, 1L, request));

        verify(accessGuard).requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
        verifyNoInteractions(visits, objectLock);
    }

    @Test
    void ordinaryUpdateDoesNotRequireFilesWrite() {
        service.update(caller, 1L, new ReturnVisitUpdateRequest());

        verify(accessGuard, never()).requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
        verifyNoInteractions(visits, objectLock);
    }

    private static NonRetryableApplicationException denied() {
        return new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED);
    }
}
