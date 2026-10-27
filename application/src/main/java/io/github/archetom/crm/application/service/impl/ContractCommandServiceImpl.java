package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContractBatchCollaboratorAddRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchCollaboratorRemoveRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchVersionRequest;
import io.github.archetom.crm.api.dto.request.ContractCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractCreateRequest;
import io.github.archetom.crm.api.dto.request.ContractOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractProductLineRequest;
import io.github.archetom.crm.api.dto.request.ContractUpdateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.port.out.ProductCatalogLock;
import io.github.archetom.crm.application.security.ContactAccessResolver;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.ContractAccessResolver.ContractAccess;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.ContractCommandService;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ContractVO;
import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.Contract;
import io.github.archetom.crm.domain.entity.ContractCollaborator;
import io.github.archetom.crm.domain.entity.ContractProductLine;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Opportunity;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.Product;
import io.github.archetom.crm.domain.entity.ProductCategory;
import io.github.archetom.crm.domain.exception.ContractNotFoundException;
import io.github.archetom.crm.domain.exception.ProductNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.ContractVisibility;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.ProductFieldCatalog;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.InvoiceRepository;
import io.github.archetom.crm.domain.repository.ReceivablePlanRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.ProductCategoryRepository;
import io.github.archetom.crm.domain.repository.ProductRepository;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.ProductId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContractCommandServiceImpl implements ContractCommandService {
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final ReceivableRepository receivableRepository;
    private final ReceivablePlanRepository receivablePlanRepository;
    private final CustomerRepository customerRepository;
    private final ContactRepository contactRepository;
    private final OpportunityRepository opportunityRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ContractAccessResolver accessResolver;
    private final CustomerAccessResolver customerAccessResolver;
    private final ContactAccessResolver contactAccessResolver;
    private final OpportunityAccessResolver opportunityAccessResolver;
    private final OrganizationAccessGuard organizationAccessGuard;
    private final ContractApplicationSupport support;
    private final AttachmentParentLifecycle attachmentParentLifecycle;
    private final FileFieldBindingService fileFieldBindingService;
    private final ProductApplicationSupport productSupport;
    private final CustomizationObjectLock objectLock;
    private final ProductCatalogLock catalogLock;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<ContractVO> create(
            AuthenticatedCaller caller, ContractCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTRACT_CREATE,
                new ServiceOperation<>() {
                    private ContractAccess access;
                    private ContractAccess assignAccess;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTRACTS_WRITE,
                                ContractAccessMode.WRITE);
                        require(request, "Contract create request");
                        if (hasProducts(request.getProducts())) {
                            organizationAccessGuard.requireActiveMembership(
                                    caller, AuthorityCatalog.PRODUCTS_READ);
                        }
                        if ((request.getOwnerActorId() != null
                                && !request.getOwnerActorId().equals(caller.actorId()))
                                || (request.getCollaborators() != null
                                && !request.getCollaborators().isEmpty())) {
                            assignAccess = accessResolver.access(
                                    caller, AuthorityCatalog.CONTRACTS_ASSIGN,
                                    ContractAccessMode.OWNER_SCOPE);
                        }
                    }

                    @Override
                    public ContractVO execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CONTRACT);
                        if (hasProducts(request.getProducts())) {
                            catalogLock.acquire(access.tenantId());
                        }
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        Map<FieldCode, FieldDefinition> fieldsByCode = support.fieldsByCode(fields);
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                access.tenantId(), access.memberId());
                        requireCreateFields(fieldAccess, request);
                        MemberId owner = request.getOwnerActorId() == null ? access.memberId()
                                : memberId(request.getOwnerActorId());
                        OrganizationMembership ownerMembership = support.requireActiveMember(
                                access.tenantId(), owner);
                        if (assignAccess != null) {
                            requireAssignableOwner(ownerMembership, assignAccess.visibility());
                        }
                        List<ContractCollaborator> collaborators =
                                support.collaborators(request.getCollaborators());
                        support.requireActiveMembers(access.tenantId(), collaborators.stream()
                                .map(ContractCollaborator::memberId).toList());
                        Relations relations = relations(caller, access,
                                request.getCustomerId(), request.getOpportunityId(),
                                request.getContactId());
                        List<ContractProductLine> products = products(
                                access, request.getProducts());
                        Contract contract = Contract.create(access.tenantId(), owner,
                                relations.customerId(), relations.opportunityId(),
                                relations.contactId(), request.getName(), request.getNumber(),
                                request.getOrderDate(), request.getStartDate(), request.getEndDate(),
                                request.getContractType(), request.getPaymentType(), request.getRemark(),
                                request.getDiscountRate(), products, collaborators,
                                support.customFields(request.getCustomFields(), List.of(), true,
                                        fieldsByCode, fieldAccess));
                        contractRepository.save(access.tenantId(), contract, fields);
                        fileFieldBindingService.consumeCreateDraft(access.tenantId(), access.memberId(),
                                BusinessObjectType.CONTRACT, request.getFileFieldDraftId(),
                                new AttachmentTarget(AttachmentTargetType.CONTRACT, contract.getId().getValue()),
                                request.getFileFields(), fieldsByCode, fieldAccess);
                        return output(caller, access, contract, fieldAccess, true);
                    }
                });
    }

    @Override
    public Result<ContractVO> update(
            AuthenticatedCaller caller, Long contractIdValue, ContractUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTRACT_UPDATE,
                new ServiceOperation<>() {
                    private ContractAccess access;
                    private ContractId contractId;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTRACTS_WRITE,
                                ContractAccessMode.WRITE);
                        require(request, "Contract update request");
                        if (hasProducts(request.getProducts())) {
                            organizationAccessGuard.requireActiveMembership(
                                    caller, AuthorityCatalog.PRODUCTS_READ);
                        }
                        contractId = contractId(contractIdValue);
                    }

                    @Override
                    public ContractVO execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CONTRACT);
                        if (hasProducts(request.getProducts())) {
                            catalogLock.acquire(access.tenantId());
                        }
                        Contract contract = contractRepository.findVisibleByIdForUpdate(
                                        access.tenantId(), contractId, access.visibility())
                                .orElseThrow(ContractNotFoundException::new);
                        support.requireVersion(contract, request.getVersion());
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        Map<FieldCode, FieldDefinition> fieldsByCode = support.fieldsByCode(fields);
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                access.tenantId(), access.memberId());
                        requireUpdateFields(fieldAccess, request);
                        boolean relationsRequested = request.getCustomerId() != null
                                || request.getOpportunityId() != null || request.getContactId() != null;
                        Relations relations = relationsRequested ? relations(caller, access,
                                selected(request.getCustomerId(), contract.getCustomerId().getValue()),
                                selected(request.getOpportunityId(), contract.getOpportunityId() == null
                                        ? null : contract.getOpportunityId().getValue()),
                                selected(request.getContactId(), contract.getContactId() == null
                                        ? null : contract.getContactId().getValue()))
                                : new Relations(contract.getCustomerId(), contract.getOpportunityId(),
                                contract.getContactId());
                        BigDecimal committed = invoiceRepository.sumCommittedAmount(
                                access.tenantId(), contractId, null);
                        BigDecimal received = receivableRepository.sumActiveAmount(
                                access.tenantId(), contractId, null);
                        BigDecimal planned = receivablePlanRepository.sumActivePlannedAmount(
                                access.tenantId(), contractId, null);
                        if (invoiceRepository.existsByContract(access.tenantId(), contractId)
                                && !contract.getCustomerId().sameValueAs(relations.customerId())) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "A contract with invoices cannot change customer");
                        }
                        if (receivableRepository.existsByContract(access.tenantId(), contractId)
                                && !contract.getCustomerId().sameValueAs(relations.customerId())) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "A contract with receivables cannot change customer");
                        }
                        if (receivablePlanRepository.existsByContract(access.tenantId(), contractId)
                                && !contract.getCustomerId().sameValueAs(relations.customerId())) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "A contract with receivable plans cannot change customer");
                        }
                        contract.replaceDetails(relations.customerId(), relations.opportunityId(),
                                relations.contactId(), selected(request.getName(), contract.getName()),
                                selected(request.getNumber(), contract.getNumber()),
                                selected(request.getOrderDate(), contract.getOrderDate()),
                                selected(request.getStartDate(), contract.getStartDate()),
                                selected(request.getEndDate(), contract.getEndDate()),
                                selected(request.getContractType(), contract.getContractType()),
                                selected(request.getPaymentType(), contract.getPaymentType()),
                                selected(request.getRemark(), contract.getRemark()),
                                selected(request.getDiscountRate(), contract.getDiscountRate()),
                                request.getProducts() == null ? contract.getProductLines()
                                        : products(access, request.getProducts()),
                                support.customFields(request.getCustomFields(),
                                        contract.getCustomFieldValues(), false,
                                        fieldsByCode, fieldAccess));
                        if (contract.getTotalAmount().compareTo(committed) < 0) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Contract amount cannot be below committed invoice amount");
                        }
                        if (contract.getTotalAmount().compareTo(received) < 0) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Contract amount cannot be below received amount");
                        }
                        if (contract.getTotalAmount().compareTo(planned) < 0) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Contract amount cannot be below planned receivable amount");
                        }
                        contractRepository.save(access.tenantId(), contract, fields);
                        fileFieldBindingService.replaceExisting(access.tenantId(), access.memberId(),
                                BusinessObjectType.CONTRACT,
                                new AttachmentTarget(AttachmentTargetType.CONTRACT, contract.getId().getValue()),
                                request.getFileFields(), fieldsByCode, fieldAccess);
                        return output(caller, access, contract, fieldAccess, false);
                    }
                });
    }

    @Override
    public Result<ContractVO> updateOwner(
            AuthenticatedCaller caller, Long contractIdValue,
            ContractOwnerUpdateRequest request) {
        require(request, "Contract owner request");
        return mutate(caller, contractIdValue, request.getVersion(),
                UseCaseOperation.CONTRACT_OWNER_UPDATE, AuthorityCatalog.CONTRACTS_ASSIGN,
                ContractAccessMode.OWNER_SCOPE, (contract, access, fields) -> {
                    support.requireWrite(fields, ContractFieldCatalog.OWNER_ACTOR);
                    MemberId owner = memberId(request.getOwnerActorId());
                    OrganizationMembership ownerMembership = support.requireActiveMember(
                            access.tenantId(), owner);
                    requireAssignableOwner(ownerMembership, access.visibility());
                    contract.changeOwner(owner);
                }, true);
    }

    @Override
    public Result<Void> updateOwners(
            AuthenticatedCaller caller, ContractBatchOwnerUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTRACT_BATCH_OWNER_UPDATE,
                new ServiceOperation<>() {
                    private ContractAccess access;
                    private List<ContractBatchVersionRequest> targets;
                    private MemberId owner;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTRACTS_ASSIGN,
                                ContractAccessMode.OWNER_SCOPE);
                        require(request, "Contract batch owner request");
                        targets = batchTargets(request.getContracts(), "Contract batch owner targets");
                        owner = memberId(request.getOwnerActorId());
                        OrganizationMembership ownerMembership = support.requireActiveMember(
                                access.tenantId(), owner);
                        requireAssignableOwner(ownerMembership, access.visibility());
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CONTRACT);
                        Map<FieldCode, EffectiveFieldAccess> fields = support.accesses(
                                access.tenantId(), access.memberId());
                        support.requireWrite(fields, ContractFieldCatalog.OWNER_ACTOR);
                        List<Contract> contracts = batchContracts(access, targets);
                        for (int index = 0; index < contracts.size(); index++) {
                            support.requireVersion(contracts.get(index), targets.get(index).getVersion());
                        }
                        List<FieldDefinition> definitions = support.fields(access.tenantId());
                        for (Contract contract : contracts) {
                            contract.changeOwner(owner);
                            contractRepository.save(access.tenantId(), contract, definitions);
                        }
                        return null;
                    }
                });
    }

    @Override
    public Result<Void> addCollaborators(
            AuthenticatedCaller caller, ContractBatchCollaboratorAddRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTRACT_BATCH_COLLABORATOR_ADD,
                new ServiceOperation<>() {
                    private ContractAccess access;
                    private List<ContractBatchVersionRequest> targets;
                    private List<ContractCollaborator> additions;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTRACTS_ASSIGN,
                                ContractAccessMode.OWNER_SCOPE);
                        require(request, "Contract batch collaborator add request");
                        targets = batchTargets(request.getContracts(),
                                "Contract batch collaborator targets");
                        additions = support.collaborators(request.getCollaborators());
                        requireUniqueCollaborators(additions,
                                "Contract batch collaborators are invalid");
                        support.requireActiveMembers(access.tenantId(), additions.stream()
                                .map(ContractCollaborator::memberId).toList());
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CONTRACT);
                        List<Contract> contracts = batchContracts(access, targets);
                        requireBatchVersions(contracts, targets);
                        List<List<ContractCollaborator>> replacements = contracts.stream().map(contract ->
                                mergeCollaborators(contract, additions)).toList();
                        List<FieldDefinition> definitions = support.fields(access.tenantId());
                        for (int index = 0; index < contracts.size(); index++) {
                            Contract contract = contracts.get(index);
                            contract.replaceCollaborators(replacements.get(index));
                            contractRepository.save(access.tenantId(), contract, definitions);
                        }
                        return null;
                    }
                });
    }

    @Override
    public Result<Void> removeCollaborators(
            AuthenticatedCaller caller, ContractBatchCollaboratorRemoveRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTRACT_BATCH_COLLABORATOR_REMOVE,
                new ServiceOperation<>() {
                    private ContractAccess access;
                    private List<ContractBatchVersionRequest> targets;
                    private Set<MemberId> removals;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTRACTS_ASSIGN,
                                ContractAccessMode.OWNER_SCOPE);
                        require(request, "Contract batch collaborator remove request");
                        targets = batchTargets(request.getContracts(),
                                "Contract batch collaborator targets");
                        removals = batchCollaboratorMembers(request.getActorIds());
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CONTRACT);
                        List<Contract> contracts = batchContracts(access, targets);
                        requireBatchVersions(contracts, targets);
                        List<List<ContractCollaborator>> replacements = contracts.stream().map(contract ->
                                removeCollaborators(contract, removals)).toList();
                        List<FieldDefinition> definitions = support.fields(access.tenantId());
                        for (int index = 0; index < contracts.size(); index++) {
                            Contract contract = contracts.get(index);
                            List<ContractCollaborator> replacement = replacements.get(index);
                            if (!replacement.equals(contract.getCollaborators())) {
                                contract.replaceCollaborators(replacement);
                                contractRepository.save(access.tenantId(), contract, definitions);
                            }
                        }
                        return null;
                    }
                });
    }

    @Override
    public Result<ContractVO> replaceCollaborators(
            AuthenticatedCaller caller, Long contractIdValue,
            ContractCollaboratorsUpdateRequest request) {
        require(request, "Contract collaborator request");
        return mutate(caller, contractIdValue, request.getVersion(),
                UseCaseOperation.CONTRACT_COLLABORATOR_REPLACE,
                AuthorityCatalog.CONTRACTS_ASSIGN, ContractAccessMode.OWNER_SCOPE,
                (contract, access, fields) -> {
                    List<ContractCollaborator> values =
                            support.collaborators(request.getCollaborators());
                    support.requireActiveMembers(access.tenantId(),
                            values.stream().map(ContractCollaborator::memberId).toList());
                    contract.replaceCollaborators(values);
                }, true);
    }

    @Override
    public Result<ContractVO> exitCollaborator(
            AuthenticatedCaller caller, Long contractIdValue, Long version) {
        return mutate(caller, contractIdValue, version,
                UseCaseOperation.CONTRACT_COLLABORATOR_EXIT,
                AuthorityCatalog.CONTRACTS_WRITE, ContractAccessMode.WRITE,
                (contract, access, fields) -> {
                    if (!contract.exitCollaborator(access.memberId())) {
                        throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                "Caller is not a contract collaborator");
                    }
                }, false);
    }

    @Override
    public Result<ContractVO> voidContract(
            AuthenticatedCaller caller, Long contractIdValue, Long version) {
        return mutate(caller, contractIdValue, version, UseCaseOperation.CONTRACT_VOID,
                AuthorityCatalog.CONTRACTS_WRITE, ContractAccessMode.WRITE,
                (contract, access, fields) -> {
                    support.requireWrite(fields, ContractFieldCatalog.STATUS);
                    contract.voidContract();
                }, false);
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long contractIdValue, Long version) {
        return commandTemplate.execute(UseCaseOperation.CONTRACT_DELETE,
                new ServiceOperation<>() {
                    private ContractAccess access;
                    private ContractId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTRACTS_DELETE,
                                ContractAccessMode.OWNER_SCOPE);
                        id = contractId(contractIdValue);
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CONTRACT);
                        Contract contract = contractRepository.findVisibleByIdForUpdate(
                                        access.tenantId(), id, access.visibility())
                                .orElseThrow(ContractNotFoundException::new);
                        support.requireVersion(contract, version);
                        if (invoiceRepository.sumCommittedAmount(
                                access.tenantId(), id, null).signum() > 0) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "A contract with invoices cannot be deleted");
                        }
                        if (receivableRepository.existsByContract(access.tenantId(), id)
                                || receivablePlanRepository.existsByContract(access.tenantId(), id)) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "A contract with receivables cannot be deleted");
                        }
                        contract.delete();
                        attachmentParentLifecycle.withdrawAll(access.tenantId(),
                                new AttachmentTarget(AttachmentTargetType.CONTRACT, id.getValue()));
                        fileFieldBindingService.withdrawAll(access.tenantId(),
                                new AttachmentTarget(AttachmentTargetType.CONTRACT, id.getValue()));
                        contractRepository.save(access.tenantId(), contract,
                                support.fields(access.tenantId()));
                        return null;
                    }
                });
    }

    @Override
    public Result<Void> deleteBatch(
            AuthenticatedCaller caller, ContractBatchDeleteRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTRACT_BATCH_DELETE,
                new ServiceOperation<>() {
                    private ContractAccess access;
                    private List<ContractBatchVersionRequest> targets;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTRACTS_DELETE,
                                ContractAccessMode.OWNER_SCOPE);
                        require(request, "Contract batch delete request");
                        targets = batchTargets(request.getContracts(), "Contract batch delete targets");
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CONTRACT);
                        List<Contract> contracts = batchContracts(access, targets);
                        for (int index = 0; index < contracts.size(); index++) {
                            Contract contract = contracts.get(index);
                            ContractId id = contract.getId();
                            support.requireVersion(contract, targets.get(index).getVersion());
                            if (invoiceRepository.sumCommittedAmount(
                                    access.tenantId(), id, null).signum() > 0) {
                                throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                        "A contract with invoices cannot be deleted");
                            }
                            if (receivableRepository.existsByContract(access.tenantId(), id)
                                    || receivablePlanRepository.existsByContract(access.tenantId(), id)) {
                                throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                        "A contract with receivables cannot be deleted");
                            }
                        }
                        List<FieldDefinition> definitions = support.fields(access.tenantId());
                        for (Contract contract : contracts) {
                            ContractId id = contract.getId();
                            contract.delete();
                            attachmentParentLifecycle.withdrawAll(access.tenantId(),
                                    new AttachmentTarget(AttachmentTargetType.CONTRACT, id.getValue()));
                            fileFieldBindingService.withdrawAll(access.tenantId(),
                                    new AttachmentTarget(AttachmentTargetType.CONTRACT, id.getValue()));
                            contractRepository.save(access.tenantId(), contract, definitions);
                        }
                        return null;
                    }
                });
    }

    private List<ContractBatchVersionRequest> batchTargets(
            List<ContractBatchVersionRequest> requests, String description) {
        if (requests == null || requests.isEmpty() || requests.size() > 100) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " are invalid");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (ContractBatchVersionRequest request : requests) {
            if (request == null || request.getContractId() == null || request.getVersion() == null
                    || request.getVersion() < 0 || !ids.add(request.getContractId())) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        description + " are invalid");
            }
            contractId(request.getContractId());
        }
        return requests.stream().sorted(Comparator.comparing(ContractBatchVersionRequest::getContractId))
                .toList();
    }

    private List<Contract> batchContracts(
            ContractAccess access, List<ContractBatchVersionRequest> targets) {
        return targets.stream().map(target -> contractRepository.findVisibleByIdForUpdate(
                        access.tenantId(), contractId(target.getContractId()), access.visibility())
                .orElseThrow(ContractNotFoundException::new)).toList();
    }

    private void requireBatchVersions(
            List<Contract> contracts, List<ContractBatchVersionRequest> targets) {
        for (int index = 0; index < contracts.size(); index++) {
            support.requireVersion(contracts.get(index), targets.get(index).getVersion());
        }
    }

    private void requireUniqueCollaborators(
            List<ContractCollaborator> values, String description) {
        if (values.isEmpty() || new LinkedHashSet<>(values.stream()
                .map(ContractCollaborator::memberId).toList()).size() != values.size()) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, description);
        }
    }

    private Set<MemberId> batchCollaboratorMembers(List<Long> actorIds) {
        if (actorIds == null || actorIds.isEmpty() || actorIds.size() > 100) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract batch collaborator members are invalid");
        }
        Set<MemberId> members = new LinkedHashSet<>();
        for (Long actorId : actorIds) {
            if (actorId == null || actorId <= 0 || !members.add(memberId(actorId))) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contract batch collaborator members are invalid");
            }
        }
        return Set.copyOf(members);
    }

    private List<ContractCollaborator> mergeCollaborators(
            Contract contract, List<ContractCollaborator> additions) {
        LinkedHashMap<MemberId, ContractCollaborator> merged = new LinkedHashMap<>();
        for (ContractCollaborator collaborator : contract.getCollaborators()) {
            merged.put(collaborator.memberId(), collaborator);
        }
        for (ContractCollaborator addition : additions) {
            if (contract.getOwnerMemberId().sameValueAs(addition.memberId())) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contract owner cannot be a collaborator");
            }
            merged.put(addition.memberId(), addition);
        }
        if (merged.size() > 100) {
            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Contract collaborators exceed the limit");
        }
        return List.copyOf(merged.values());
    }

    private List<ContractCollaborator> removeCollaborators(
            Contract contract, Set<MemberId> removals) {
        if (removals.contains(contract.getOwnerMemberId())) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract owner cannot be removed from collaborators");
        }
        return contract.getCollaborators().stream()
                .filter(collaborator -> !removals.contains(collaborator.memberId())).toList();
    }

    private Result<ContractVO> mutate(
            AuthenticatedCaller caller, Long idValue, Long version,
            UseCaseOperation operation, String authority, ContractAccessMode mode,
            ContractMutation mutation, boolean collaboratorsVisible) {
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            private ContractAccess access;
            private ContractId id;

            @Override
            public void validate() {
                access = accessResolver.access(caller, authority, mode);
                id = contractId(idValue);
            }

            @Override
            public ContractVO execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.CONTRACT);
                Contract contract = contractRepository.findVisibleByIdForUpdate(
                                access.tenantId(), id, access.visibility())
                        .orElseThrow(ContractNotFoundException::new);
                support.requireVersion(contract, version);
                Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                        access.tenantId(), access.memberId());
                mutation.apply(contract, access, fieldAccess);
                contractRepository.save(access.tenantId(), contract,
                        support.fields(access.tenantId()));
                return output(caller, access, contract, fieldAccess, collaboratorsVisible);
            }
        });
    }

    private ContractVO output(
            AuthenticatedCaller caller, ContractAccess access, Contract contract,
            Map<FieldCode, EffectiveFieldAccess> accesses, boolean collaboratorsVisible) {
        return support.toVO(contract, accesses,
                customerVisible(caller, access, contract),
                opportunityVisible(caller, access, contract),
                contactVisible(caller, access, contract),
                productsVisible(caller, access, accesses), collaboratorsVisible);
    }

    private boolean customerVisible(
            AuthenticatedCaller caller, ContractAccess contractAccess, Contract contract) {
        try {
            var access = customerAccessResolver.access(caller,
                    AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
            return access.tenantId().sameValueAs(contractAccess.tenantId())
                    && customerRepository.findVisibleById(access.tenantId(),
                    contract.getCustomerId(), access.visibility()).isPresent();
        } catch (NonRetryableApplicationException exception) {
            return false;
        }
    }

    private boolean opportunityVisible(
            AuthenticatedCaller caller, ContractAccess contractAccess, Contract contract) {
        if (contract.getOpportunityId() == null) {
            return false;
        }
        try {
            var access = opportunityAccessResolver.access(caller,
                    AuthorityCatalog.OPPORTUNITIES_READ, OpportunityAccessMode.READ);
            return access.tenantId().sameValueAs(contractAccess.tenantId())
                    && opportunityRepository.findVisibleById(access.tenantId(),
                    contract.getOpportunityId(), access.visibility()).isPresent();
        } catch (NonRetryableApplicationException exception) {
            return false;
        }
    }

    private boolean contactVisible(
            AuthenticatedCaller caller, ContractAccess contractAccess, Contract contract) {
        if (contract.getContactId() == null) {
            return false;
        }
        try {
            var access = contactAccessResolver.access(caller,
                    AuthorityCatalog.CONTACTS_READ, ContactAccessMode.READ);
            return access.tenantId().sameValueAs(contractAccess.tenantId())
                    && contactRepository.findVisibleById(access.tenantId(),
                    contract.getContactId(), access.visibility()).isPresent();
        } catch (NonRetryableApplicationException exception) {
            return false;
        }
    }

    private boolean productsVisible(
            AuthenticatedCaller caller, ContractAccess access,
            Map<FieldCode, EffectiveFieldAccess> contractFields) {
        if (!caller.hasAuthority(AuthorityCatalog.PRODUCTS_READ)
                || !readableUnmasked(contractFields, ContractFieldCatalog.PRODUCTS)) {
            return false;
        }
        Map<FieldCode, EffectiveFieldAccess> productFields = productSupport.accesses(
                access.tenantId(), access.memberId());
        return List.of(ProductFieldCatalog.NAME, ProductFieldCatalog.CODE,
                        ProductFieldCatalog.CATEGORY, ProductFieldCatalog.UNIT,
                        ProductFieldCatalog.LIST_PRICE)
                .stream().allMatch(code -> readableUnmasked(productFields, code));
    }

    private boolean readableUnmasked(
            Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = accesses.get(new FieldCode(code));
        return access != null && access.accessLevel() != FieldAccessLevel.HIDDEN
                && access.maskType() == FieldMaskType.NONE;
    }

    private Relations relations(
            AuthenticatedCaller caller, ContractAccess contractAccess,
            Long customerValue, Long opportunityValue, Long contactValue) {
        CustomerId customerId = customerId(customerValue);
        var customerAccess = customerAccessResolver.access(caller,
                AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
        if (!customerAccess.tenantId().sameValueAs(contractAccess.tenantId())
                || customerRepository.findVisibleById(customerAccess.tenantId(), customerId,
                customerAccess.visibility()).isEmpty()) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                    "Contract customer was not found");
        }
        OpportunityId opportunityId = opportunityId(opportunityValue);
        if (opportunityId != null) {
            var opportunityAccess = opportunityAccessResolver.access(caller,
                    AuthorityCatalog.OPPORTUNITIES_READ, OpportunityAccessMode.READ);
            Opportunity opportunity = opportunityRepository.findVisibleById(
                            opportunityAccess.tenantId(), opportunityId,
                            opportunityAccess.visibility())
                    .orElseThrow(() -> support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                            "Contract opportunity was not found"));
            if (!opportunity.getCustomerId().sameValueAs(customerId)) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contract opportunity belongs to another customer");
            }
        }
        ContactId contactId = contactId(contactValue);
        if (contactId != null) {
            var contactAccess = contactAccessResolver.access(caller,
                    AuthorityCatalog.CONTACTS_READ, ContactAccessMode.READ);
            Contact contact = contactRepository.findVisibleById(contactAccess.tenantId(),
                            contactId, contactAccess.visibility())
                    .orElseThrow(() -> support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                            "Contract contact was not found"));
            if (contact.getCustomerId() == null
                    || !contact.getCustomerId().sameValueAs(customerId)) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contract contact belongs to another customer");
            }
        }
        return new Relations(customerId, opportunityId, contactId);
    }

    private List<ContractProductLine> products(
            ContractAccess access, List<ContractProductLineRequest> requests) {
        if (requests == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract products are required");
        }
        if (requests.isEmpty()) {
            return List.of();
        }
        Map<FieldCode, EffectiveFieldAccess> productFields = productSupport.accesses(
                access.tenantId(), access.memberId());
        List.of(ProductFieldCatalog.NAME, ProductFieldCatalog.CODE,
                        ProductFieldCatalog.CATEGORY, ProductFieldCatalog.UNIT,
                        ProductFieldCatalog.LIST_PRICE)
                .forEach(code -> productSupport.requireReadableUnmasked(
                        productFields, new FieldCode(code)));
        LinkedHashSet<ProductId> ids = new LinkedHashSet<>();
        for (ContractProductLineRequest request : requests) {
            if (request == null || !ids.add(productId(request.getProductId()))) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contract product lines are invalid");
            }
        }
        List<Product> products = productRepository.findAvailableByIdsForUpdate(
                access.tenantId(), ids, ids.size());
        Map<ProductId, Product> byId = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        if (byId.size() != ids.size()) {
            throw new ProductNotFoundException();
        }
        Map<ProductCategoryId, String> categories = categoryNames(access, products);
        return java.util.stream.IntStream.range(0, requests.size()).mapToObj(index -> {
            ContractProductLineRequest request = requests.get(index);
            Product product = byId.get(new ProductId(request.getProductId()));
            return ContractProductLine.snapshot(product,
                    product.getCategoryId() == null ? null : categories.get(product.getCategoryId()),
                    request.getQuantity(), request.getUnitPrice(), index);
        }).toList();
    }

    private Map<ProductCategoryId, String> categoryNames(
            ContractAccess access, List<Product> products) {
        Set<ProductCategoryId> ids = products.stream().map(Product::getCategoryId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<ProductCategory> values = categoryRepository.findAllActive(
                access.tenantId(), 5_001);
        if (values.size() > 5_000) {
            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Product category catalog is too large");
        }
        Map<ProductCategoryId, String> result = values.stream()
                .filter(value -> ids.contains(value.getId()))
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName));
        if (!result.keySet().containsAll(ids)) {
            throw new ProductNotFoundException();
        }
        return result;
    }

    private void requireCreateFields(
            Map<FieldCode, EffectiveFieldAccess> accesses, ContractCreateRequest request) {
        List.of(ContractFieldCatalog.NAME, ContractFieldCatalog.NUMBER,
                ContractFieldCatalog.CUSTOMER, ContractFieldCatalog.OWNER_ACTOR,
                ContractFieldCatalog.PRODUCTS, ContractFieldCatalog.DISCOUNT_RATE)
                .forEach(code -> support.requireWrite(accesses, code));
        optionalFields(accesses, request.getOpportunityId(), request.getContactId(),
                request.getOrderDate(), request.getStartDate(), request.getEndDate(),
                request.getContractType(), request.getPaymentType(), request.getRemark());
    }

    private void requireUpdateFields(
            Map<FieldCode, EffectiveFieldAccess> accesses, ContractUpdateRequest request) {
        List.of(ContractFieldCatalog.NAME, ContractFieldCatalog.NUMBER,
                ContractFieldCatalog.CUSTOMER, ContractFieldCatalog.OPPORTUNITY,
                ContractFieldCatalog.CONTACT, ContractFieldCatalog.ORDER_DATE,
                ContractFieldCatalog.START_DATE, ContractFieldCatalog.END_DATE,
                ContractFieldCatalog.CONTRACT_TYPE, ContractFieldCatalog.PAYMENT_TYPE,
                ContractFieldCatalog.REMARK, ContractFieldCatalog.PRODUCTS,
                ContractFieldCatalog.DISCOUNT_RATE)
                .forEach(code -> support.requireWrite(accesses, code));
    }

    private void optionalFields(
            Map<FieldCode, EffectiveFieldAccess> accesses, Object opportunity, Object contact,
            Object orderDate, Object startDate, Object endDate, Object contractType,
            Object paymentType, Object remark) {
        Map<String, Object> values = Map.of(
                ContractFieldCatalog.OPPORTUNITY, value(opportunity),
                ContractFieldCatalog.CONTACT, value(contact),
                ContractFieldCatalog.ORDER_DATE, value(orderDate),
                ContractFieldCatalog.START_DATE, value(startDate),
                ContractFieldCatalog.END_DATE, value(endDate),
                ContractFieldCatalog.CONTRACT_TYPE, value(contractType),
                ContractFieldCatalog.PAYMENT_TYPE, value(paymentType),
                ContractFieldCatalog.REMARK, value(remark));
        values.forEach((code, provided) -> {
            if (provided != Missing.VALUE) {
                support.requireWrite(accesses, code);
            }
        });
    }

    private Object value(Object value) {
        return value == null ? Missing.VALUE : value;
    }

    private boolean hasProducts(List<ContractProductLineRequest> products) {
        return products != null && !products.isEmpty();
    }

    private <T> T selected(T requested, T existing) {
        return requested == null ? existing : requested;
    }

    private void requireAssignableOwner(
            OrganizationMembership owner, ContractVisibility visibility) {
        if (visibility.dataScope() == DataScope.TENANT) {
            return;
        }
        if (visibility.dataScope() == DataScope.SELF) {
            if (!owner.getMemberId().sameValueAs(visibility.callerMemberId())) {
                throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                        "Contract owner is outside the assignment scope");
            }
            return;
        }
        if (owner.getDepartmentId() == null
                || !visibility.ownerDepartmentIds().contains(owner.getDepartmentId())) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Contract owner is outside the assignment scope");
        }
    }

    private ContractId contractId(Long value) {
        try {
            return new ContractId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract ID must be positive");
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

    private OpportunityId opportunityId(Long value) {
        if (value == null) {
            return null;
        }
        try {
            return new OpportunityId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity ID must be positive");
        }
    }

    private ContactId contactId(Long value) {
        if (value == null) {
            return null;
        }
        try {
            return new ContactId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contact ID must be positive");
        }
    }

    private ProductId productId(Long value) {
        try {
            return new ProductId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product ID must be positive");
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

    private void require(Object request, String description) {
        if (request == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " must not be empty");
        }
    }

    private interface ContractMutation {
        void apply(Contract contract, ContractAccess access,
                   Map<FieldCode, EffectiveFieldAccess> accesses);
    }

    private record Relations(
            CustomerId customerId, OpportunityId opportunityId, ContactId contactId) {
    }

    private enum Missing { VALUE }
}
