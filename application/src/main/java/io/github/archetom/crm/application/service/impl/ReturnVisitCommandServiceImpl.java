package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReturnVisitCreateRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitUpdateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.ReturnVisitAccessResolver;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.ReturnVisitCommandService;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ReturnVisitVO;
import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.Contract;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.ReturnVisit;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.ReturnVisitNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.ReturnVisitAccessMode;
import io.github.archetom.crm.domain.model.ReturnVisitForm;
import io.github.archetom.crm.domain.model.ReturnVisitSatisfaction;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ReturnVisitFieldCatalog;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.ReturnVisitRepository;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReturnVisitId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReturnVisitCommandServiceImpl implements ReturnVisitCommandService {
    private final ReturnVisitRepository repository;
    private final ContactRepository contactRepository;
    private final ContractRepository contractRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final ReturnVisitAccessResolver accessResolver;
    private final OrganizationAccessGuard accessGuard;
    private final BusinessRecordAccessGuard recordAccessGuard;
    private final ReturnVisitApplicationSupport support;
    private final ActivityAuditService auditService;
    private final AttachmentParentLifecycle attachmentParentLifecycle;
    private final FileFieldBindingService fileFieldBindingService;
    private final CustomizationObjectLock objectLock;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<ReturnVisitVO> create(AuthenticatedCaller caller, ReturnVisitCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.RETURN_VISIT_CREATE, new ServiceOperation<>() {
            private ReturnVisitAccessResolver.ReturnVisitAccess access;
            private ReturnVisitAccessResolver.ReturnVisitAccess assignAccess;

            @Override
            public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.RETURN_VISITS_WRITE,
                        ReturnVisitAccessMode.WRITE);
                require(request, "Return visit create request");
                if (changesFileFields(request.getFileFieldDraftId(), request.getFileFields())) {
                    accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
                }
                if (request.getOwnerActorId() != null
                        && !request.getOwnerActorId().equals(caller.actorId())) {
                    assignAccess = accessResolver.access(caller, AuthorityCatalog.RETURN_VISITS_ASSIGN,
                            ReturnVisitAccessMode.OWNER_SCOPE);
                }
            }

            @Override
            public ReturnVisitVO execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.RETURN_VISIT);
                List<FieldDefinition> fields = support.fields(access.tenantId());
                Map<io.github.archetom.crm.domain.valueobject.FieldCode, FieldDefinition> byCode =
                        support.fieldsByCode(fields);
                Map<io.github.archetom.crm.domain.valueobject.FieldCode, EffectiveFieldAccess> fieldAccess =
                        support.accesses(access.tenantId(), access.memberId());
                support.requireWrite(fieldAccess, ReturnVisitFieldCatalog.CUSTOMER);
                support.requireWrite(fieldAccess, ReturnVisitFieldCatalog.OWNER_ACTOR);
                support.requireWrite(fieldAccess, ReturnVisitFieldCatalog.VISIT_TIME);
                if (request.getVisitForm() != null) support.requireWrite(fieldAccess, ReturnVisitFieldCatalog.VISIT_FORM);
                if (request.getSatisfaction() != null) support.requireWrite(fieldAccess, ReturnVisitFieldCatalog.SATISFACTION);
                if (request.getContactId() != null) support.requireWrite(fieldAccess, ReturnVisitFieldCatalog.CONTACT);
                if (request.getContractId() != null) support.requireWrite(fieldAccess, ReturnVisitFieldCatalog.CONTRACT);
                if (request.getRemark() != null) support.requireWrite(fieldAccess, ReturnVisitFieldCatalog.REMARK);
                MemberId owner = request.getOwnerActorId() == null ? access.memberId()
                        : member(request.getOwnerActorId());
                OrganizationMembership ownerMembership = activeMember(access.tenantId(), owner);
                if (assignAccess != null) requireAssignable(ownerMembership, assignAccess);
                CustomerId customer = customer(caller, access.tenantId(), request.getCustomerId());
                ContactId contact = contact(caller, access.tenantId(), request.getContactId(), customer);
                ContractId contract = contract(caller, access.tenantId(), request.getContractId(), customer);
                ReturnVisit value = ReturnVisit.create(access.tenantId(), owner, customer, contact, contract,
                        number(), request.getVisitTime(), request.getRemark(),
                        ReturnVisitForm.optional(request.getVisitForm()),
                        ReturnVisitSatisfaction.optional(request.getSatisfaction()), support.customFields(
                                request.getCustomFields(), List.of(), byCode, fieldAccess));
                repository.save(access.tenantId(), value, fields);
                fileFieldBindingService.consumeCreateDraft(access.tenantId(), access.memberId(),
                        BusinessObjectType.RETURN_VISIT, request.getFileFieldDraftId(),
                        new AttachmentTarget(AttachmentTargetType.RETURN_VISIT, value.getId().getValue()),
                        request.getFileFields(), byCode, fieldAccess);
                auditService.record(caller, access.tenantId(), BusinessObjectType.CUSTOMER,
                        customer.getValue(), "Return visit created");
                return support.toVO(caller, access.tenantId(), value, fieldAccess);
            }
        });
    }

    @Override
    public Result<ReturnVisitVO> update(
            AuthenticatedCaller caller, Long returnVisitIdValue, ReturnVisitUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.RETURN_VISIT_UPDATE, new ServiceOperation<>() {
            private ReturnVisitAccessResolver.ReturnVisitAccess access;
            private ReturnVisitId id;
            @Override public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.RETURN_VISITS_WRITE,
                        ReturnVisitAccessMode.WRITE);
                require(request, "Return visit update request");
                id = id(returnVisitIdValue);
                if (changesFileFields(request.getFileFields())) {
                    accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
                }
            }
            @Override public ReturnVisitVO execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.RETURN_VISIT);
                ReturnVisit value = repository.findVisibleByIdForUpdate(access.tenantId(), id, access.visibility())
                        .orElseThrow(ReturnVisitNotFoundException::new);
                version(value, request.getVersion());
                List<FieldDefinition> fields = support.fields(access.tenantId());
                Map<io.github.archetom.crm.domain.valueobject.FieldCode, FieldDefinition> byCode =
                        support.fieldsByCode(fields);
                Map<io.github.archetom.crm.domain.valueobject.FieldCode, EffectiveFieldAccess> fieldAccess =
                        support.accesses(access.tenantId(), access.memberId());
                support.requireWrite(fieldAccess, ReturnVisitFieldCatalog.VISIT_TIME);
                if (request.visitFormSpecified()) support.requireWrite(fieldAccess, ReturnVisitFieldCatalog.VISIT_FORM);
                if (request.satisfactionSpecified()) support.requireWrite(fieldAccess, ReturnVisitFieldCatalog.SATISFACTION);
                if (request.contactIdSpecified())
                    support.requireWrite(fieldAccess, ReturnVisitFieldCatalog.CONTACT);
                if (request.contractIdSpecified())
                    support.requireWrite(fieldAccess, ReturnVisitFieldCatalog.CONTRACT);
                if (request.getRemark() != null || value.getRemark() != null)
                    support.requireWrite(fieldAccess, ReturnVisitFieldCatalog.REMARK);
                CustomerId customer = value.getCustomerId();
                ContactId contact = request.contactIdSpecified()
                        ? contact(caller, access.tenantId(), request.getContactId(), customer)
                        : value.getContactId();
                ContractId contract = request.contractIdSpecified()
                        ? contract(caller, access.tenantId(), request.getContractId(), customer)
                        : value.getContractId();
                ReturnVisitForm visitForm = request.visitFormSpecified()
                        ? ReturnVisitForm.optional(request.getVisitForm()) : value.getVisitForm();
                ReturnVisitSatisfaction satisfaction = request.satisfactionSpecified()
                        ? ReturnVisitSatisfaction.optional(request.getSatisfaction()) : value.getSatisfaction();
                value.replace(contact, contract, request.getVisitTime(), request.getRemark(), visitForm, satisfaction,
                        support.customFields(request.getCustomFields(), value.getCustomFieldValues(),
                                byCode, fieldAccess));
                repository.save(access.tenantId(), value, fields);
                fileFieldBindingService.replaceExisting(access.tenantId(), access.memberId(),
                        BusinessObjectType.RETURN_VISIT,
                        new AttachmentTarget(AttachmentTargetType.RETURN_VISIT, value.getId().getValue()),
                        request.getFileFields(), byCode, fieldAccess);
                auditService.record(caller, access.tenantId(), BusinessObjectType.CUSTOMER,
                        customer.getValue(), "Return visit updated");
                return support.toVO(caller, access.tenantId(), value, fieldAccess);
            }
        });
    }

    @Override
    public Result<ReturnVisitVO> updateOwner(
            AuthenticatedCaller caller, Long returnVisitIdValue, ReturnVisitOwnerUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.RETURN_VISIT_OWNER_UPDATE, new ServiceOperation<>() {
            private ReturnVisitAccessResolver.ReturnVisitAccess access;
            private ReturnVisitId id;
            @Override public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.RETURN_VISITS_ASSIGN,
                        ReturnVisitAccessMode.OWNER_SCOPE);
                require(request, "Return visit owner request"); id = id(returnVisitIdValue);
            }
            @Override public ReturnVisitVO execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.RETURN_VISIT);
                ReturnVisit value = repository.findVisibleByIdForUpdate(access.tenantId(), id, access.visibility())
                        .orElseThrow(ReturnVisitNotFoundException::new);
                version(value, request.getVersion());
                Map<io.github.archetom.crm.domain.valueobject.FieldCode, EffectiveFieldAccess> fieldAccess =
                        support.accesses(access.tenantId(), access.memberId());
                support.requireWrite(fieldAccess, ReturnVisitFieldCatalog.OWNER_ACTOR);
                OrganizationMembership owner = activeMember(access.tenantId(), member(request.getOwnerActorId()));
                requireAssignable(owner, access);
                value.changeOwner(owner.getMemberId());
                repository.save(access.tenantId(), value, support.fields(access.tenantId()));
                auditService.record(caller, access.tenantId(), BusinessObjectType.CUSTOMER,
                        value.getCustomerId().getValue(), "Return visit owner changed");
                return support.toVO(caller, access.tenantId(), value, fieldAccess);
            }
        });
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long returnVisitIdValue, Long version) {
        return commandTemplate.execute(UseCaseOperation.RETURN_VISIT_DELETE, new ServiceOperation<>() {
            private ReturnVisitAccessResolver.ReturnVisitAccess access; private ReturnVisitId id;
            @Override public void validate() {
                id = id(returnVisitIdValue); if (version == null || version < 0) throw support.invalid("Return visit version is invalid");
                access = accessResolver.access(caller, AuthorityCatalog.RETURN_VISITS_DELETE, ReturnVisitAccessMode.WRITE);
            }
            @Override public Void execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.RETURN_VISIT);
                ReturnVisit value = repository.findVisibleByIdForUpdate(access.tenantId(), id, access.visibility())
                        .orElseThrow(ReturnVisitNotFoundException::new);
                version(value, version); value.delete();
                AttachmentTarget target = new AttachmentTarget(AttachmentTargetType.RETURN_VISIT, id.getValue());
                attachmentParentLifecycle.withdrawAll(access.tenantId(), target);
                fileFieldBindingService.withdrawAll(access.tenantId(), target);
                repository.save(access.tenantId(), value, support.fields(access.tenantId()));
                auditService.record(caller, access.tenantId(), BusinessObjectType.CUSTOMER,
                        value.getCustomerId().getValue(), "Return visit deleted");
                return null;
            }
        });
    }

    private CustomerId customer(AuthenticatedCaller caller, TenantId tenantId, Long value) {
        TenantId actual = recordAccessGuard.requireVisible(caller, BusinessObjectType.CUSTOMER, value, false);
        sameTenant(tenantId, actual); return new CustomerId(value);
    }
    private ContactId contact(AuthenticatedCaller caller, TenantId tenantId, Long value, CustomerId customer) {
        if (value == null) return null;
        TenantId actual = recordAccessGuard.requireVisible(caller, BusinessObjectType.CONTACT, value, false);
        sameTenant(tenantId, actual);
        Contact contact = contactRepository.findById(tenantId, new ContactId(value))
                .orElseThrow(() -> support.invalid("Return visit contact was not found"));
        if (contact.getCustomerId() == null || !contact.getCustomerId().sameValueAs(customer)) {
            throw support.invalid("Return visit contact must belong to its customer");
        }
        return contact.getId();
    }
    private ContractId contract(AuthenticatedCaller caller, TenantId tenantId, Long value, CustomerId customer) {
        if (value == null) return null;
        TenantId actual = recordAccessGuard.requireVisible(caller, BusinessObjectType.CONTRACT, value, false);
        sameTenant(tenantId, actual);
        Contract contract = contractRepository.findById(tenantId, new ContractId(value))
                .orElseThrow(() -> support.invalid("Return visit contract was not found"));
        if (!contract.getCustomerId().sameValueAs(customer)) {
            throw support.invalid("Return visit contract must belong to its customer");
        }
        return contract.getId();
    }
    private OrganizationMembership activeMember(TenantId tenantId, MemberId memberId) {
        return membershipRepository.findByMember(tenantId, memberId).filter(OrganizationMembership::isActive)
                .orElseThrow(() -> support.invalid("Return visit owner is not an active member"));
    }
    private void requireAssignable(OrganizationMembership owner, ReturnVisitAccessResolver.ReturnVisitAccess access) {
        if (access.visibility().dataScope() == DataScope.TENANT) return;
        if (access.visibility().dataScope() == DataScope.SELF
                && owner.getMemberId().sameValueAs(access.memberId())) return;
        if ((access.visibility().dataScope() == DataScope.DEPARTMENT || access.visibility().dataScope() == DataScope.DEPARTMENT_SUBTREE)
                && owner.getDepartmentId() != null && access.visibility().ownerDepartmentIds().contains(owner.getDepartmentId())) return;
        throw support.invalid("Return visit owner is outside the assignment scope");
    }
    private void version(ReturnVisit value, Long version) {
        if (!value.getVersion().equals(version)) throw new AggregateVersionConflictException();
    }

    private static boolean changesFileFields(Long draftId, Map<String, List<Long>> fileFields) {
        return draftId != null || changesFileFields(fileFields);
    }

    private static boolean changesFileFields(Map<String, List<Long>> fileFields) {
        return fileFields != null && !fileFields.isEmpty();
    }
    private ReturnVisitId id(Long value) { try { return new ReturnVisitId(value); } catch (RuntimeException exception) { throw support.invalid("Return visit ID is invalid"); } }
    private MemberId member(Long value) { try { return new MemberId(value); } catch (RuntimeException exception) { throw support.invalid("Return visit owner is invalid"); } }
    private void sameTenant(TenantId expected, TenantId actual) { if (actual == null || !expected.sameValueAs(actual)) throw support.invalid("Return visit relation tenant is invalid"); }
    private void require(Object value, String name) { if (value == null) throw support.invalid(name + " is required"); }
    private String number() { return "RV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(java.util.Locale.ROOT); }
}
