package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.InvoiceBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.InvoiceBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceBatchResetRequest;
import io.github.archetom.crm.api.dto.request.InvoiceBatchVersionRequest;
import io.github.archetom.crm.api.dto.request.InvoiceCreateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceIssueRequest;
import io.github.archetom.crm.api.dto.request.InvoiceOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceProfileRequest;
import io.github.archetom.crm.api.dto.request.InvoiceProfileUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceUpdateRequest;
import io.github.archetom.crm.application.assembler.InvoiceAssembler;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.ContractAccessResolver.ContractAccess;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.InvoiceAccessResolver;
import io.github.archetom.crm.application.security.InvoiceAccessResolver.InvoiceAccess;
import io.github.archetom.crm.application.service.InvoiceCommandService;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.InvoiceProfileVO;
import io.github.archetom.crm.application.vo.InvoiceVO;
import io.github.archetom.crm.domain.entity.Contract;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Invoice;
import io.github.archetom.crm.domain.entity.InvoiceProfile;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.exception.ContractNotFoundException;
import io.github.archetom.crm.domain.exception.InvoiceNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.ContractStatus;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.InvoiceAccessMode;
import io.github.archetom.crm.domain.model.InvoiceStatus;
import io.github.archetom.crm.domain.model.InvoiceVisibility;
import io.github.archetom.crm.domain.model.InvoiceTitleType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.InvoiceFieldCatalog;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.InvoiceProfileRepository;
import io.github.archetom.crm.domain.repository.InvoiceRepository;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.InvoiceId;
import io.github.archetom.crm.domain.valueobject.InvoiceProfileId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class InvoiceCommandServiceImpl implements InvoiceCommandService {
    private final InvoiceRepository invoiceRepository;
    private final InvoiceProfileRepository profileRepository;
    private final ContractRepository contractRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceAccessResolver accessResolver;
    private final ContractAccessResolver contractAccessResolver;
    private final CustomerAccessResolver customerAccessResolver;
    private final InvoiceApplicationSupport support;
    private final AttachmentParentLifecycle attachmentParentLifecycle;
    private final FileFieldBindingService fileFieldBindingService;
    private final CustomizationObjectLock objectLock;
    private final CommandServiceTemplate commandTemplate;

    /** Compatibility constructor retained for profile-only tests that do not exercise FILE fields. */
    InvoiceCommandServiceImpl(InvoiceRepository invoiceRepository, InvoiceProfileRepository profileRepository,
                              ContractRepository contractRepository, CustomerRepository customerRepository,
                              InvoiceAccessResolver accessResolver,
                              ContractAccessResolver contractAccessResolver,
                              CustomerAccessResolver customerAccessResolver,
                              InvoiceApplicationSupport support,
                              AttachmentParentLifecycle attachmentParentLifecycle,
                              CustomizationObjectLock objectLock,
                              CommandServiceTemplate commandTemplate) {
        this(invoiceRepository, profileRepository, contractRepository, customerRepository, accessResolver,
                contractAccessResolver, customerAccessResolver, support, attachmentParentLifecycle, null,
                objectLock, commandTemplate);
    }

    @Override
    public Result<InvoiceVO> create(
            AuthenticatedCaller caller, InvoiceCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.INVOICE_CREATE,
                new ServiceOperation<>() {
                    private InvoiceAccess access;
                    private ContractAccess contractAccess;
                    private InvoiceAccess assignAccess;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.INVOICES_WRITE,
                                InvoiceAccessMode.WRITE);
                        require(request, "Invoice create request");
                        contractAccess = contractAccessResolver.access(caller,
                                AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ);
                        if (request.getOwnerActorId() != null
                                && !request.getOwnerActorId().equals(caller.actorId())) {
                            assignAccess = accessResolver.access(
                                    caller, AuthorityCatalog.INVOICES_ASSIGN,
                                    InvoiceAccessMode.OWNER_SCOPE);
                        }
                    }

                    @Override
                    public InvoiceVO execute() {
                        requireSameTenant(access, contractAccess);
                        objectLock.acquire(access.tenantId(), BusinessObjectType.INVOICE);
                        Contract contract = lockContract(contractAccess,
                                contractId(request.getContractId()));
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                access.tenantId(), access.memberId());
                        requireCreateWrites(fieldAccess, request);
                        MemberId owner = request.getOwnerActorId() == null ? access.memberId()
                                : memberId(request.getOwnerActorId());
                        OrganizationMembership ownerMembership = support.requireActiveMember(
                                access.tenantId(), owner);
                        if (assignAccess != null) {
                            requireAssignableOwner(ownerMembership, assignAccess.visibility());
                        }
                        requireAvailableAmount(access, contract, null, request.getAmount());
                        Map<FieldCode, FieldDefinition> fieldsByCode = support.fieldsByCode(fields);
                        Invoice invoice = Invoice.create(access.tenantId(), owner,
                                contract.getCustomerId(), contract.getId(), applicationNumber(),
                                request.getAmount(), request.getRequestedDate(),
                                request.getInvoiceType(), titleType(request.getTitleType()),
                                request.getTitle(), request.getTaxNumber(), request.getBankName(),
                                request.getBankAccount(), request.getRegisteredAddress(),
                                request.getRegisteredPhone(), request.getContactName(),
                                request.getContactPhone(), request.getShippingAddress(),
                                request.getRemark(), support.customFields(request.getCustomFields(),
                                List.of(), true, fieldsByCode, fieldAccess));
                        invoiceRepository.save(access.tenantId(), invoice, fields);
                        fileFieldBindingService.consumeCreateDraft(access.tenantId(), access.memberId(),
                                BusinessObjectType.INVOICE, request.getFileFieldDraftId(),
                                new AttachmentTarget(AttachmentTargetType.INVOICE, invoice.getId().getValue()),
                                request.getFileFields(), fieldsByCode, fieldAccess);
                        return support.toVO(invoice, fieldAccess,
                                customerVisible(caller, access, invoice.getCustomerId()), true);
                    }
                });
    }

    @Override
    public Result<InvoiceVO> update(
            AuthenticatedCaller caller, Long invoiceIdValue, InvoiceUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.INVOICE_UPDATE,
                new ServiceOperation<>() {
                    private InvoiceAccess access;
                    private ContractAccess contractAccess;
                    private InvoiceId invoiceId;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.INVOICES_WRITE,
                                InvoiceAccessMode.WRITE);
                        require(request, "Invoice update request");
                        contractAccess = contractAccessResolver.access(caller,
                                AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ);
                        invoiceId = invoiceId(invoiceIdValue);
                    }

                    @Override
                    public InvoiceVO execute() {
                        requireSameTenant(access, contractAccess);
                        objectLock.acquire(access.tenantId(), BusinessObjectType.INVOICE);
                        Invoice observed = invoiceRepository.findVisibleById(
                                        access.tenantId(), invoiceId, access.visibility())
                                .orElseThrow(InvoiceNotFoundException::new);
                        Contract contract = lockContract(contractAccess, observed.getContractId());
                        Invoice invoice = invoiceRepository.findVisibleByIdForUpdate(
                                        access.tenantId(), invoiceId, access.visibility())
                                .orElseThrow(InvoiceNotFoundException::new);
                        if (!invoice.getContractId().sameValueAs(contract.getId())) {
                            throw support.error(ApplicationErrorCode.VERSION_CONFLICT,
                                    "Invoice relationship changed concurrently");
                        }
                        support.requireVersion(invoice, request.getVersion());
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                access.tenantId(), access.memberId());
                        requireUpdateWrites(fieldAccess);
                        BigDecimal amount = selected(request.getAmount(), invoice.getAmount());
                        requireAvailableAmount(access, contract, invoiceId, amount);
                        invoice.replaceDetails(amount,
                                selected(request.getRequestedDate(), invoice.getRequestedDate()),
                                selected(request.getInvoiceType(), invoice.getInvoiceType()),
                                request.getTitleType() == null ? invoice.getTitleType()
                                        : titleType(request.getTitleType()),
                                selected(request.getTitle(), invoice.getTitle()),
                                selected(request.getTaxNumber(), invoice.getTaxNumber()),
                                selected(request.getBankName(), invoice.getBankName()),
                                selected(request.getBankAccount(), invoice.getBankAccount()),
                                selected(request.getRegisteredAddress(), invoice.getRegisteredAddress()),
                                selected(request.getRegisteredPhone(), invoice.getRegisteredPhone()),
                                selected(request.getContactName(), invoice.getContactName()),
                                selected(request.getContactPhone(), invoice.getContactPhone()),
                                selected(request.getShippingAddress(), invoice.getShippingAddress()),
                                selected(request.getRemark(), invoice.getRemark()),
                                support.customFields(request.getCustomFields(),
                                invoice.getCustomFieldValues(), false,
                                support.fieldsByCode(fields), fieldAccess));
                        invoiceRepository.save(access.tenantId(), invoice, fields);
                        fileFieldBindingService.replaceExisting(access.tenantId(), access.memberId(),
                                BusinessObjectType.INVOICE,
                                new AttachmentTarget(AttachmentTargetType.INVOICE, invoice.getId().getValue()),
                                request.getFileFields(), support.fieldsByCode(fields), fieldAccess);
                        return support.toVO(invoice, fieldAccess,
                                customerVisible(caller, access, invoice.getCustomerId()), true);
                    }
                });
    }

    @Override
    public Result<InvoiceVO> updateOwner(
            AuthenticatedCaller caller, Long invoiceIdValue,
            InvoiceOwnerUpdateRequest request) {
        require(request, "Invoice owner request");
        return mutate(caller, invoiceIdValue, request.getVersion(),
                UseCaseOperation.INVOICE_OWNER_UPDATE, AuthorityCatalog.INVOICES_ASSIGN,
                InvoiceAccessMode.OWNER_SCOPE, (invoice, access, fields) -> {
                    support.requireWrite(fields, InvoiceFieldCatalog.OWNER_ACTOR);
                    MemberId owner = memberId(request.getOwnerActorId());
                    OrganizationMembership ownerMembership = support.requireActiveMember(
                            access.tenantId(), owner);
                    requireAssignableOwner(ownerMembership, access.visibility());
                    invoice.changeOwner(owner);
                });
    }

    @Override
    public Result<Void> updateOwners(
            AuthenticatedCaller caller, InvoiceBatchOwnerUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.INVOICE_BATCH_OWNER_UPDATE,
                new ServiceOperation<>() {
                    private InvoiceAccess access;
                    private List<InvoiceBatchVersionRequest> targets;
                    private MemberId owner;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.INVOICES_ASSIGN,
                                InvoiceAccessMode.OWNER_SCOPE);
                        require(request, "Invoice batch owner request");
                        targets = batchTargets(request.getInvoices(), "Invoice batch owner targets");
                        owner = memberId(request.getOwnerActorId());
                        OrganizationMembership ownerMembership = support.requireActiveMember(
                                access.tenantId(), owner);
                        requireAssignableOwner(ownerMembership, access.visibility());
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.INVOICE);
                        Map<FieldCode, EffectiveFieldAccess> fields = support.accesses(
                                access.tenantId(), access.memberId());
                        support.requireWrite(fields, InvoiceFieldCatalog.OWNER_ACTOR);
                        List<Invoice> invoices = batchInvoices(access, targets);
                        for (int index = 0; index < invoices.size(); index++) {
                            support.requireVersion(invoices.get(index), targets.get(index).getVersion());
                        }
                        List<FieldDefinition> definitions = support.fields(access.tenantId());
                        for (Invoice invoice : invoices) {
                            invoice.changeOwner(owner);
                            invoiceRepository.save(access.tenantId(), invoice, definitions);
                        }
                        return null;
                    }
                });
    }

    @Override
    public Result<InvoiceVO> issue(
            AuthenticatedCaller caller, Long invoiceIdValue, InvoiceIssueRequest request) {
        require(request, "Invoice issue request");
        return mutate(caller, invoiceIdValue, request.getVersion(),
                UseCaseOperation.INVOICE_ISSUE, AuthorityCatalog.INVOICES_ISSUE,
                InvoiceAccessMode.WRITE, (invoice, access, fields) -> {
                    List.of(InvoiceFieldCatalog.STATUS, InvoiceFieldCatalog.INVOICE_NUMBER,
                            InvoiceFieldCatalog.ISSUED_DATE,
                            InvoiceFieldCatalog.LOGISTICS_NUMBER)
                            .forEach(code -> support.requireWrite(fields, code));
                    invoice.issue(request.getInvoiceNumber(), request.getIssuedDate(),
                            request.getLogisticsNumber());
                });
    }

    @Override
    public Result<InvoiceVO> reset(
            AuthenticatedCaller caller, Long invoiceIdValue, Long version) {
        return mutate(caller, invoiceIdValue, version, UseCaseOperation.INVOICE_RESET,
                AuthorityCatalog.INVOICES_ISSUE, InvoiceAccessMode.WRITE,
                (invoice, access, fields) -> {
                    List.of(InvoiceFieldCatalog.STATUS, InvoiceFieldCatalog.INVOICE_NUMBER,
                            InvoiceFieldCatalog.ISSUED_DATE,
                            InvoiceFieldCatalog.LOGISTICS_NUMBER)
                            .forEach(code -> support.requireWrite(fields, code));
                    invoice.reset();
                });
    }

    @Override
    public Result<Void> resetBatch(
            AuthenticatedCaller caller, InvoiceBatchResetRequest request) {
        return commandTemplate.execute(UseCaseOperation.INVOICE_BATCH_RESET,
                new ServiceOperation<>() {
                    private InvoiceAccess access;
                    private List<InvoiceBatchVersionRequest> targets;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.INVOICES_ISSUE,
                                InvoiceAccessMode.WRITE);
                        require(request, "Invoice batch reset request");
                        targets = batchTargets(request.getInvoices(), "Invoice batch reset targets");
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.INVOICE);
                        List<Invoice> invoices = batchInvoices(access, targets);
                        for (int index = 0; index < invoices.size(); index++) {
                            Invoice invoice = invoices.get(index);
                            support.requireVersion(invoice, targets.get(index).getVersion());
                            if (invoice.getStatus() != InvoiceStatus.ISSUED) {
                                throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                        "Only issued invoices can be reset");
                            }
                        }
                        Map<FieldCode, EffectiveFieldAccess> fields = support.accesses(
                                access.tenantId(), access.memberId());
                        List.of(InvoiceFieldCatalog.STATUS, InvoiceFieldCatalog.INVOICE_NUMBER,
                                        InvoiceFieldCatalog.ISSUED_DATE,
                                        InvoiceFieldCatalog.LOGISTICS_NUMBER)
                                .forEach(code -> support.requireWrite(fields, code));
                        List<FieldDefinition> definitions = support.fields(access.tenantId());
                        for (Invoice invoice : invoices) {
                            invoice.reset();
                            invoiceRepository.save(access.tenantId(), invoice, definitions);
                        }
                        return null;
                    }
                });
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long invoiceIdValue, Long version) {
        return commandTemplate.execute(UseCaseOperation.INVOICE_DELETE,
                new ServiceOperation<>() {
                    private InvoiceAccess access;
                    private InvoiceId invoiceId;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.INVOICES_DELETE,
                                InvoiceAccessMode.OWNER_SCOPE);
                        invoiceId = invoiceId(invoiceIdValue);
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.INVOICE);
                        Invoice invoice = invoiceRepository.findVisibleByIdForUpdate(
                                        access.tenantId(), invoiceId, access.visibility())
                                .orElseThrow(InvoiceNotFoundException::new);
                        support.requireVersion(invoice, version);
                        invoice.delete();
                        attachmentParentLifecycle.withdrawAll(access.tenantId(),
                                new AttachmentTarget(AttachmentTargetType.INVOICE, invoiceId.getValue()));
                        fileFieldBindingService.withdrawAll(access.tenantId(),
                                new AttachmentTarget(AttachmentTargetType.INVOICE, invoiceId.getValue()));
                        invoiceRepository.save(access.tenantId(), invoice,
                                support.fields(access.tenantId()));
                        return null;
                    }
                });
    }

    @Override
    public Result<Void> deleteBatch(
            AuthenticatedCaller caller, InvoiceBatchDeleteRequest request) {
        return commandTemplate.execute(UseCaseOperation.INVOICE_BATCH_DELETE,
                new ServiceOperation<>() {
                    private InvoiceAccess access;
                    private List<InvoiceBatchVersionRequest> targets;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.INVOICES_DELETE,
                                InvoiceAccessMode.OWNER_SCOPE);
                        require(request, "Invoice batch delete request");
                        targets = batchTargets(request.getInvoices(), "Invoice batch delete targets");
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.INVOICE);
                        List<Invoice> invoices = batchInvoices(access, targets);
                        for (int index = 0; index < invoices.size(); index++) {
                            Invoice invoice = invoices.get(index);
                            support.requireVersion(invoice, targets.get(index).getVersion());
                            if (invoice.getStatus() != InvoiceStatus.PENDING) {
                                throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                        "Only pending invoices can be deleted");
                            }
                        }
                        List<FieldDefinition> definitions = support.fields(access.tenantId());
                        for (Invoice invoice : invoices) {
                            InvoiceId invoiceId = invoice.getId();
                            invoice.delete();
                            attachmentParentLifecycle.withdrawAll(access.tenantId(),
                                    new AttachmentTarget(AttachmentTargetType.INVOICE,
                                            invoiceId.getValue()));
                            fileFieldBindingService.withdrawAll(access.tenantId(),
                                    new AttachmentTarget(AttachmentTargetType.INVOICE,
                                            invoiceId.getValue()));
                            invoiceRepository.save(access.tenantId(), invoice, definitions);
                        }
                        return null;
                    }
                });
    }

    private List<InvoiceBatchVersionRequest> batchTargets(
            List<InvoiceBatchVersionRequest> requests, String description) {
        if (requests == null || requests.isEmpty() || requests.size() > 100) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, description + " are invalid");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (InvoiceBatchVersionRequest request : requests) {
            if (request == null || request.getInvoiceId() == null || request.getVersion() == null
                    || request.getVersion() < 0 || !ids.add(request.getInvoiceId())) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID, description + " are invalid");
            }
            invoiceId(request.getInvoiceId());
        }
        return requests.stream().sorted(Comparator.comparing(InvoiceBatchVersionRequest::getInvoiceId))
                .toList();
    }

    private List<Invoice> batchInvoices(
            InvoiceAccess access, List<InvoiceBatchVersionRequest> targets) {
        return targets.stream().map(target -> invoiceRepository.findVisibleByIdForUpdate(
                        access.tenantId(), invoiceId(target.getInvoiceId()), access.visibility())
                .orElseThrow(InvoiceNotFoundException::new)).toList();
    }

    @Override
    public Result<InvoiceProfileVO> createProfile(
            AuthenticatedCaller caller, InvoiceProfileRequest request) {
        return commandTemplate.execute(UseCaseOperation.INVOICE_PROFILE_CREATE,
                new ServiceOperation<>() {
                    private InvoiceAccess access;
                    private CustomerId customerId;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.INVOICES_WRITE,
                                InvoiceAccessMode.WRITE);
                        require(request, "Invoice profile request");
                        customerId = customerId(request.getCustomerId());
                    }

                    @Override
                    public InvoiceProfileVO execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.INVOICE);
                        requireVisibleCustomer(caller, access, customerId);
                        if (profileRepository.countActiveByCustomer(
                                access.tenantId(), customerId)
                                >= InvoiceProfile.MAX_ACTIVE_PER_CUSTOMER) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Invoice profile limit was reached for this customer");
                        }
                        InvoiceProfile profile = InvoiceProfile.create(access.tenantId(),
                                customerId, titleType(request.getTitleType()), request.getTitle(),
                                request.getTaxNumber(), request.getBankName(),
                                request.getBankAccount(), request.getRegisteredAddress(),
                                request.getRegisteredPhone(), request.getRemark());
                        profileRepository.save(access.tenantId(), profile);
                        return InvoiceAssembler.toVO(profile);
                    }
                });
    }

    @Override
    public Result<InvoiceProfileVO> updateProfile(
            AuthenticatedCaller caller, Long profileIdValue, InvoiceProfileUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.INVOICE_PROFILE_UPDATE,
                new ServiceOperation<>() {
                    private InvoiceAccess access;
                    private InvoiceProfileId profileId;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.INVOICES_WRITE,
                                InvoiceAccessMode.WRITE);
                        require(request, "Invoice profile request");
                        profileId = profileId(profileIdValue);
                    }

                    @Override
                    public InvoiceProfileVO execute() {
                        InvoiceProfile profile = profileRepository.findById(
                                        access.tenantId(), profileId)
                                .orElseThrow(() -> support.error(
                                        ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                        "Invoice profile was not found"));
                        requireProfileVersion(profile, request.getVersion());
                        if (request.getCustomerId() != null
                                && !profile.getCustomerId().sameValueAs(
                                customerId(request.getCustomerId()))) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Invoice profile customer cannot be changed");
                        }
                        requireVisibleCustomer(caller, access, profile.getCustomerId());
                        profile.update(request.getTitleType() == null ? profile.getTitleType()
                                        : titleType(request.getTitleType()),
                                selected(request.getTitle(), profile.getTitle()),
                                selected(request.getTaxNumber(), profile.getTaxNumber()),
                                selected(request.getBankName(), profile.getBankName()),
                                selected(request.getBankAccount(), profile.getBankAccount()),
                                selected(request.getRegisteredAddress(), profile.getRegisteredAddress()),
                                selected(request.getRegisteredPhone(), profile.getRegisteredPhone()),
                                selected(request.getRemark(), profile.getRemark()));
                        profileRepository.save(access.tenantId(), profile);
                        return InvoiceAssembler.toVO(profile);
                    }
                });
    }

    @Override
    public Result<Void> deleteProfile(
            AuthenticatedCaller caller, Long profileIdValue, Long version) {
        return commandTemplate.execute(UseCaseOperation.INVOICE_PROFILE_DELETE,
                new ServiceOperation<>() {
                    private InvoiceAccess access;
                    private InvoiceProfileId profileId;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.INVOICES_DELETE,
                                InvoiceAccessMode.OWNER_SCOPE);
                        profileId = profileId(profileIdValue);
                    }

                    @Override
                    public Void execute() {
                        InvoiceProfile profile = profileRepository.findById(
                                        access.tenantId(), profileId)
                                .orElseThrow(() -> support.error(
                                        ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                        "Invoice profile was not found"));
                        requireProfileVersion(profile, version);
                        requireVisibleCustomer(caller, access, profile.getCustomerId());
                        profile.delete();
                        profileRepository.save(access.tenantId(), profile);
                        return null;
                    }
                });
    }

    private Result<InvoiceVO> mutate(
            AuthenticatedCaller caller, Long invoiceIdValue, Long version,
            UseCaseOperation operation, String authority, InvoiceAccessMode mode,
            InvoiceMutation mutation) {
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            private InvoiceAccess access;
            private InvoiceId invoiceId;

            @Override
            public void validate() {
                access = accessResolver.access(caller, authority, mode);
                invoiceId = invoiceId(invoiceIdValue);
            }

            @Override
            public InvoiceVO execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.INVOICE);
                Invoice invoice = invoiceRepository.findVisibleByIdForUpdate(
                                access.tenantId(), invoiceId, access.visibility())
                        .orElseThrow(InvoiceNotFoundException::new);
                support.requireVersion(invoice, version);
                Map<FieldCode, EffectiveFieldAccess> fields = support.accesses(
                        access.tenantId(), access.memberId());
                mutation.apply(invoice, access, fields);
                invoiceRepository.save(access.tenantId(), invoice,
                        support.fields(access.tenantId()));
                return support.toVO(invoice, fields,
                        customerVisible(caller, access, invoice.getCustomerId()),
                        contractVisible(caller, access, invoice.getContractId()));
            }
        });
    }

    private Contract lockContract(ContractAccess access, ContractId contractId) {
        Contract contract = contractRepository.findVisibleByIdForUpdate(
                        access.tenantId(), contractId, access.visibility())
                .orElseThrow(ContractNotFoundException::new);
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Only active contracts can receive invoices");
        }
        return contract;
    }

    private void requireAvailableAmount(
            InvoiceAccess access, Contract contract, InvoiceId excluding,
            BigDecimal amount) {
        BigDecimal committed = invoiceRepository.sumCommittedAmount(
                access.tenantId(), contract.getId(), excluding);
        if (amount == null || committed.add(amount).compareTo(contract.getTotalAmount()) > 0) {
            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Invoice amount exceeds the remaining contract amount");
        }
    }

    private void requireVisibleCustomer(
            AuthenticatedCaller caller, InvoiceAccess invoiceAccess, CustomerId customerId) {
        var access = customerAccessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                CustomerAccessMode.READ);
        if (!access.tenantId().sameValueAs(invoiceAccess.tenantId())
                || customerRepository.findVisibleById(access.tenantId(), customerId,
                access.visibility()).isEmpty()) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                    "Invoice customer was not found");
        }
    }

    private boolean customerVisible(
            AuthenticatedCaller caller, InvoiceAccess invoiceAccess, CustomerId customerId) {
        try {
            var access = customerAccessResolver.access(caller,
                    AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
            return access.tenantId().sameValueAs(invoiceAccess.tenantId())
                    && customerRepository.findVisibleById(access.tenantId(), customerId,
                    access.visibility()).isPresent();
        } catch (NonRetryableApplicationException exception) {
            return false;
        }
    }

    private boolean contractVisible(
            AuthenticatedCaller caller, InvoiceAccess invoiceAccess, ContractId contractId) {
        try {
            var access = contractAccessResolver.access(caller,
                    AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ);
            return access.tenantId().sameValueAs(invoiceAccess.tenantId())
                    && contractRepository.findVisibleById(access.tenantId(), contractId,
                    access.visibility()).isPresent();
        } catch (NonRetryableApplicationException exception) {
            return false;
        }
    }

    private void requireAssignableOwner(
            OrganizationMembership owner, InvoiceVisibility visibility) {
        if (visibility.dataScope() == DataScope.TENANT) {
            return;
        }
        if (visibility.dataScope() == DataScope.SELF) {
            if (!owner.getMemberId().sameValueAs(visibility.callerMemberId())) {
                throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                        "Invoice owner is outside the assignment scope");
            }
            return;
        }
        if (owner.getDepartmentId() == null
                || !visibility.ownerDepartmentIds().contains(owner.getDepartmentId())) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Invoice owner is outside the assignment scope");
        }
    }

    private void requireCreateWrites(
            Map<FieldCode, EffectiveFieldAccess> accesses, InvoiceCreateRequest request) {
        List.of(InvoiceFieldCatalog.CUSTOMER, InvoiceFieldCatalog.CONTRACT,
                InvoiceFieldCatalog.AMOUNT, InvoiceFieldCatalog.INVOICE_TYPE,
                InvoiceFieldCatalog.TITLE_TYPE, InvoiceFieldCatalog.TITLE,
                InvoiceFieldCatalog.OWNER_ACTOR)
                .forEach(code -> support.requireWrite(accesses, code));
        requireOptionalWrites(accesses, request.getRequestedDate(), request.getTaxNumber(),
                request.getBankName(), request.getBankAccount(),
                request.getRegisteredAddress(), request.getRegisteredPhone(),
                request.getContactName(), request.getContactPhone(),
                request.getShippingAddress(), request.getRemark());
    }

    private void requireUpdateWrites(Map<FieldCode, EffectiveFieldAccess> accesses) {
        List.of(InvoiceFieldCatalog.AMOUNT, InvoiceFieldCatalog.REQUESTED_DATE,
                InvoiceFieldCatalog.INVOICE_TYPE, InvoiceFieldCatalog.TITLE_TYPE,
                InvoiceFieldCatalog.TITLE, InvoiceFieldCatalog.TAX_NUMBER,
                InvoiceFieldCatalog.BANK_NAME, InvoiceFieldCatalog.BANK_ACCOUNT,
                InvoiceFieldCatalog.REGISTERED_ADDRESS,
                InvoiceFieldCatalog.REGISTERED_PHONE, InvoiceFieldCatalog.CONTACT_NAME,
                InvoiceFieldCatalog.CONTACT_PHONE, InvoiceFieldCatalog.SHIPPING_ADDRESS,
                InvoiceFieldCatalog.REMARK)
                .forEach(code -> support.requireWrite(accesses, code));
    }

    private void requireOptionalWrites(
            Map<FieldCode, EffectiveFieldAccess> accesses, Object requestedDate,
            Object taxNumber, Object bankName, Object bankAccount,
            Object registeredAddress, Object registeredPhone, Object contactName,
            Object contactPhone, Object shippingAddress, Object remark) {
        Map<String, Object> values = Map.of(
                InvoiceFieldCatalog.REQUESTED_DATE, value(requestedDate),
                InvoiceFieldCatalog.TAX_NUMBER, value(taxNumber),
                InvoiceFieldCatalog.BANK_NAME, value(bankName),
                InvoiceFieldCatalog.BANK_ACCOUNT, value(bankAccount),
                InvoiceFieldCatalog.REGISTERED_ADDRESS, value(registeredAddress),
                InvoiceFieldCatalog.REGISTERED_PHONE, value(registeredPhone),
                InvoiceFieldCatalog.CONTACT_NAME, value(contactName),
                InvoiceFieldCatalog.CONTACT_PHONE, value(contactPhone),
                InvoiceFieldCatalog.SHIPPING_ADDRESS, value(shippingAddress),
                InvoiceFieldCatalog.REMARK, value(remark));
        values.forEach((code, provided) -> {
            if (provided != Missing.VALUE) {
                support.requireWrite(accesses, code);
            }
        });
    }

    private void requireSameTenant(InvoiceAccess invoice, ContractAccess contract) {
        if (!invoice.tenantId().sameValueAs(contract.tenantId())) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Invoice tenant context is inconsistent");
        }
    }

    private void requireProfileVersion(InvoiceProfile profile, Long version) {
        if (version == null || !version.equals(profile.getVersion())) {
            throw support.error(ApplicationErrorCode.VERSION_CONFLICT,
                    "Invoice profile version is stale");
        }
    }

    private InvoiceTitleType titleType(String value) {
        return support.parse(InvoiceTitleType.class, value, "Invoice title type");
    }

    private ContractId contractId(Long value) {
        try {
            return new ContractId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract ID must be positive");
        }
    }

    private InvoiceId invoiceId(Long value) {
        try {
            return new InvoiceId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Invoice ID must be positive");
        }
    }

    private InvoiceProfileId profileId(Long value) {
        try {
            return new InvoiceProfileId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Invoice profile ID must be positive");
        }
    }

    private CustomerId customerId(Long value) {
        try {
            return new CustomerId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer ID must be positive");
        }
    }

    private MemberId memberId(Long value) {
        try {
            return new MemberId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Member ID must be positive");
        }
    }

    private String applicationNumber() {
        return "INV-" + UUID.randomUUID().toString().replace("-", "");
    }

    private Object value(Object value) {
        return value == null ? Missing.VALUE : value;
    }

    private <T> T selected(T requested, T existing) {
        return requested == null ? existing : requested;
    }

    private void require(Object request, String description) {
        if (request == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " must not be empty");
        }
    }

    private interface InvoiceMutation {
        void apply(Invoice invoice, InvoiceAccess access,
                   Map<FieldCode, EffectiveFieldAccess> accesses);
    }

    private enum Missing { VALUE }
}
