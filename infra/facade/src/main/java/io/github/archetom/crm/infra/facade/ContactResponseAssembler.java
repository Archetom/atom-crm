package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.ContactAddressResponse;
import io.github.archetom.crm.api.dto.response.ContactCallerCollaboratorStateResponse;
import io.github.archetom.crm.api.dto.response.ContactCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.ContactCollaboratorResponse;
import io.github.archetom.crm.api.dto.response.ContactHierarchyResponse;
import io.github.archetom.crm.api.dto.response.ContactResponse;
import io.github.archetom.crm.api.dto.response.NearbyContactResponse;
import io.github.archetom.crm.application.vo.ContactAddressVO;
import io.github.archetom.crm.application.vo.ContactCollaboratorBaselineVO;
import io.github.archetom.crm.application.vo.ContactCollaboratorVO;
import io.github.archetom.crm.application.vo.ContactHierarchyVO;
import io.github.archetom.crm.application.vo.ContactVO;
import io.github.archetom.crm.application.vo.NearbyContactVO;

final class ContactResponseAssembler {

    private ContactResponseAssembler() {
    }

    static ContactResponse toResponse(ContactVO value) {
        if (value == null) {
            return null;
        }
        return new ContactResponse().setId(value.id())
                .setOwnerActorId(value.ownerActorId())
                .setCustomerId(value.customerId())
                .setParentContactId(value.parentContactId())
                .setName(value.name()).setPhoneNumber(value.phoneNumber())
                .setTelephone(value.telephone()).setJobTitle(value.jobTitle())
                .setEmail(value.email()).setWebsite(value.website())
                .setDescription(value.description()).setNextContactTime(value.nextContactTime())
                .setStatus(value.status()).setLocked(value.locked())
                .setDealStatus(value.dealStatus()).setDealTime(value.dealTime())
                .setTags(value.tags()).setAddresses(value.addresses().stream()
                        .map(ContactResponseAssembler::toResponse).toList())
                .setCollaborators(value.collaborators().stream()
                        .map(ContactResponseAssembler::toResponse).toList())
                .setCustomFields(value.customFields()).setFileFields(java.util.Map.of()).setStarred(value.starred())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime()).setLastActivityTime(value.lastActivityTime())
                .setLastActivityContent(value.lastActivityContent());
    }

    static ContactCollaboratorResponse toResponse(ContactCollaboratorVO value) {
        return new ContactCollaboratorResponse().setActorId(value.actorId()).setAccess(value.access());
    }

    static ContactCollaboratorBaselineResponse toResponse(
            ContactCollaboratorBaselineVO value) {
        return new ContactCollaboratorBaselineResponse().setVersion(value.version())
                .setCollaborators(value.collaborators().stream()
                        .map(ContactResponseAssembler::toResponse).toList())
                .setCallerState(ContactCallerCollaboratorStateResponse.valueOf(value.callerState()));
    }

    static ContactHierarchyResponse toResponse(ContactHierarchyVO value) {
        return new ContactHierarchyResponse().setRequestedContactId(value.requestedContactId())
                .setContacts(value.contacts().stream()
                        .map(ContactResponseAssembler::toResponse).toList());
    }

    static NearbyContactResponse toResponse(NearbyContactVO value) {
        return new NearbyContactResponse().setContact(toResponse(value.contact()))
                .setDistanceMeters(value.distanceMeters());
    }

    private static ContactAddressResponse toResponse(ContactAddressVO value) {
        return new ContactAddressResponse().setKey(value.key()).setLabel(value.label())
                .setCountry(value.country()).setRegion(value.region()).setCity(value.city())
                .setPostalCode(value.postalCode()).setAddressLine(value.addressLine())
                .setLongitude(value.longitude()).setLatitude(value.latitude())
                .setPrimary(value.primary());
    }
}
