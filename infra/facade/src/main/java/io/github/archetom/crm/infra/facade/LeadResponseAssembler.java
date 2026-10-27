package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.LeadAddressResponse;
import io.github.archetom.crm.api.dto.response.LeadCallerCollaboratorStateResponse;
import io.github.archetom.crm.api.dto.response.LeadCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.LeadCollaboratorResponse;
import io.github.archetom.crm.api.dto.response.LeadHierarchyResponse;
import io.github.archetom.crm.api.dto.response.LeadResponse;
import io.github.archetom.crm.api.dto.response.NearbyLeadResponse;
import io.github.archetom.crm.application.vo.LeadAddressVO;
import io.github.archetom.crm.application.vo.LeadCollaboratorBaselineVO;
import io.github.archetom.crm.application.vo.LeadCollaboratorVO;
import io.github.archetom.crm.application.vo.LeadHierarchyVO;
import io.github.archetom.crm.application.vo.LeadVO;
import io.github.archetom.crm.application.vo.NearbyLeadVO;

final class LeadResponseAssembler {

    private LeadResponseAssembler() {
    }

    static LeadResponse toResponse(LeadVO value) {
        if (value == null) {
            return null;
        }
        return new LeadResponse().setId(value.id())
                .setOwnerActorId(value.ownerActorId())
                .setCustomerId(value.customerId())
                .setConvertedContactId(value.convertedContactId())
                .setParentLeadId(value.parentLeadId())
                .setName(value.name()).setPhoneNumber(value.phoneNumber())
                .setTelephone(value.telephone()).setJobTitle(value.jobTitle())
                .setEmail(value.email()).setWebsite(value.website())
                .setDescription(value.description()).setNextContactTime(value.nextContactTime())
                .setStatus(value.status()).setLocked(value.locked())
                .setDealStatus(value.dealStatus()).setDealTime(value.dealTime())
                .setTags(value.tags()).setAddresses(value.addresses().stream()
                        .map(LeadResponseAssembler::toResponse).toList())
                .setCollaborators(value.collaborators().stream()
                        .map(LeadResponseAssembler::toResponse).toList())
                .setCustomFields(value.customFields()).setFileFields(java.util.Map.of()).setStarred(value.starred())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime()).setLastActivityTime(value.lastActivityTime())
                .setLastActivityContent(value.lastActivityContent());
    }

    static LeadCollaboratorResponse toResponse(LeadCollaboratorVO value) {
        return new LeadCollaboratorResponse().setActorId(value.actorId()).setAccess(value.access());
    }

    static LeadCollaboratorBaselineResponse toResponse(LeadCollaboratorBaselineVO value) {
        return new LeadCollaboratorBaselineResponse().setVersion(value.version())
                .setCollaborators(value.collaborators().stream()
                        .map(LeadResponseAssembler::toResponse).toList())
                .setCallerState(LeadCallerCollaboratorStateResponse.valueOf(value.callerState()));
    }

    static LeadHierarchyResponse toResponse(LeadHierarchyVO value) {
        return new LeadHierarchyResponse().setRequestedLeadId(value.requestedLeadId())
                .setLeads(value.leads().stream()
                        .map(LeadResponseAssembler::toResponse).toList());
    }

    static NearbyLeadResponse toResponse(NearbyLeadVO value) {
        return new NearbyLeadResponse().setLead(toResponse(value.lead()))
                .setDistanceMeters(value.distanceMeters());
    }

    private static LeadAddressResponse toResponse(LeadAddressVO value) {
        return new LeadAddressResponse().setKey(value.key()).setLabel(value.label())
                .setCountry(value.country()).setRegion(value.region()).setCity(value.city())
                .setPostalCode(value.postalCode()).setAddressLine(value.addressLine())
                .setLongitude(value.longitude()).setLatitude(value.latitude())
                .setPrimary(value.primary());
    }
}
