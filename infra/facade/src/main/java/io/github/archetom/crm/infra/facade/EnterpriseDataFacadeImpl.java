package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.EnterpriseIdentifierLookupRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseNameSearchRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseDataAdoptionPreviewRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseDataAdoptRequest;
import io.github.archetom.crm.api.dto.response.EnterpriseDataAdoptionPreviewResponse;
import io.github.archetom.crm.api.dto.response.EnterpriseDataAdoptionResponse;
import io.github.archetom.crm.api.dto.response.EnterpriseDataCapabilityResponse;
import io.github.archetom.crm.api.dto.response.EnterpriseDataObservationResponse;
import io.github.archetom.crm.api.facade.EnterpriseDataFacade;
import io.github.archetom.crm.application.service.EnterpriseDataQueryService;
import io.github.archetom.crm.application.service.EnterpriseDataAdoptionService;
import io.github.archetom.crm.application.vo.EnterpriseDataAdoptionPreviewVO;
import io.github.archetom.crm.application.vo.EnterpriseDataAdoptionVO;
import io.github.archetom.crm.application.vo.EnterpriseDataCapabilityVO;
import io.github.archetom.crm.application.vo.EnterpriseDataObservationVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Maps the application-owned enterprise-data projection to public responses. */
@Service
@RequiredArgsConstructor
public class EnterpriseDataFacadeImpl implements EnterpriseDataFacade {

    private final EnterpriseDataQueryService service;
    private final EnterpriseDataAdoptionService adoptionService;

    @Override
    public Result<EnterpriseDataCapabilityResponse> capabilities(AuthenticatedCaller caller) {
        return ResultUtil.map(service.capabilities(caller), this::capability);
    }

    @Override
    public Result<List<EnterpriseDataObservationResponse>> search(
            AuthenticatedCaller caller, EnterpriseNameSearchRequest request) {
        return ResultUtil.map(service.search(caller, request), values ->
                values.stream().map(this::observation).toList());
    }

    @Override
    public Result<EnterpriseDataObservationResponse> lookup(
            AuthenticatedCaller caller, EnterpriseIdentifierLookupRequest request) {
        return ResultUtil.map(service.lookup(caller, request), this::observation);
    }

    @Override
    public Result<EnterpriseDataAdoptionPreviewResponse> previewAdoption(
            AuthenticatedCaller caller, EnterpriseDataAdoptionPreviewRequest request) {
        return ResultUtil.map(adoptionService.preview(caller, request), this::preview);
    }

    @Override
    public Result<EnterpriseDataAdoptionResponse> adopt(
            AuthenticatedCaller caller, EnterpriseDataAdoptRequest request) {
        return ResultUtil.map(adoptionService.adopt(caller, request), this::adoption);
    }

    private EnterpriseDataCapabilityResponse capability(EnterpriseDataCapabilityVO source) {
        return new EnterpriseDataCapabilityResponse()
                .setReady(source.ready())
                .setQueryModes(source.queryModes())
                .setLegalJurisdictions(source.legalJurisdictions())
                .setIdentifierTypes(source.identifierTypes().stream().map(type ->
                        new EnterpriseDataCapabilityResponse.IdentifierTypeResponse()
                                .setIdentityScope(type.identityScope())
                                .setRegistryScheme(type.registryScheme())).toList())
                .setMaxCandidates(source.maxCandidates())
                .setUsageMode(source.usageMode());
    }

    private EnterpriseDataObservationResponse observation(EnterpriseDataObservationVO source) {
        return new EnterpriseDataObservationResponse()
                .setPrimaryIdentifier(identifier(source.primaryIdentifier()))
                .setIdentifiers(source.identifiers().stream().map(this::identifier).toList())
                .setLegalJurisdiction(source.legalJurisdiction())
                .setRegisteredName(source.registeredName())
                .setEntityStatus(source.entityStatus())
                .setFormationDateKind(source.formationDateKind())
                .setFormationDate(source.formationDate())
                .setRegisteredAddress(address(source.registeredAddress()))
                .setSourceKind(source.sourceKind())
                .setSourceDeclaredPartial(source.sourceDeclaredPartial())
                .setAttribution(attribution(source.attribution()))
                .setObservedAt(source.observedAt())
                .setSourceUpdatedAt(source.sourceUpdatedAt())
                .setSourceRevision(source.sourceRevision())
                .setExpiresAt(source.expiresAt())
                .setUsageMode(source.usageMode());
    }

    private EnterpriseDataAdoptionPreviewResponse preview(EnterpriseDataAdoptionPreviewVO source) {
        return new EnterpriseDataAdoptionPreviewResponse()
                .setObservation(observation(source.observation()))
                .setPreviewReference(source.previewReference())
                .setDiffs(source.diffs().stream().map(value ->
                        new EnterpriseDataAdoptionPreviewResponse.DiffResponse()
                                .setFact(value.fact()).setTargetLocator(value.targetLocator())
                                .setChanged(value.changed())).toList());
    }

    private EnterpriseDataAdoptionResponse adoption(EnterpriseDataAdoptionVO source) {
        return new EnterpriseDataAdoptionResponse().setCustomerId(source.customerId())
                .setCustomerVersion(source.customerVersion()).setAttribution(
                        attribution(source.attribution()));
    }

    private EnterpriseDataObservationResponse.IdentifierResponse identifier(
            EnterpriseDataObservationVO.IdentifierVO source) {
        return new EnterpriseDataObservationResponse.IdentifierResponse()
                .setIdentityScope(source.identityScope())
                .setRegistryScheme(source.registryScheme())
                .setCanonicalValue(source.canonicalValue())
                .setIdentifierStanding(source.identifierStanding());
    }

    private EnterpriseDataObservationResponse.AddressResponse address(
            EnterpriseDataObservationVO.AddressVO source) {
        return source == null ? null : new EnterpriseDataObservationResponse.AddressResponse()
                .setCountryCode(source.countryCode())
                .setRegion(source.region())
                .setLocality(source.locality())
                .setAddressLine(source.addressLine())
                .setPostalCode(source.postalCode());
    }

    private EnterpriseDataObservationResponse.AttributionResponse attribution(
            EnterpriseDataObservationVO.AttributionVO source) {
        return new EnterpriseDataObservationResponse.AttributionResponse()
                .setContext(source.context())
                .setSourceName(source.sourceName())
                .setInformationUrl(source.informationUrl())
                .setDisclaimer(source.disclaimer());
    }
}
