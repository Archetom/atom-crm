package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.CustomerAddressResponse;
import io.github.archetom.crm.api.dto.response.CustomerCallerCollaboratorStateResponse;
import io.github.archetom.crm.api.dto.response.CustomerCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.CustomerCollaboratorResponse;
import io.github.archetom.crm.api.dto.response.CustomerHierarchyResponse;
import io.github.archetom.crm.api.dto.response.CustomerResponse;
import io.github.archetom.crm.api.dto.response.NearbyCustomerResponse;
import io.github.archetom.crm.application.vo.CustomerAddressVO;
import io.github.archetom.crm.application.vo.CustomerCollaboratorBaselineVO;
import io.github.archetom.crm.application.vo.CustomerCollaboratorVO;
import io.github.archetom.crm.application.vo.CustomerHierarchyVO;
import io.github.archetom.crm.application.vo.CustomerVO;
import io.github.archetom.crm.application.vo.NearbyCustomerVO;

final class CustomerResponseAssembler {

    private CustomerResponseAssembler() {
    }

    static CustomerResponse toResponse(CustomerVO value) {
        if (value == null) {
            return null;
        }
        return new CustomerResponse().setId(value.id())
                .setOwnerActorId(value.ownerActorId())
                .setParentCustomerId(value.parentCustomerId())
                .setName(value.name()).setPhoneNumber(value.phoneNumber())
                .setTelephone(value.telephone()).setEmail(value.email())
                .setWebsite(value.website()).setDescription(value.description())
                .setStatus(value.status()).setLocked(value.locked())
                .setDealStatus(value.dealStatus()).setDealTime(value.dealTime())
                .setTags(value.tags()).setAddresses(value.addresses().stream()
                        .map(CustomerResponseAssembler::toResponse).toList())
                .setCollaborators(value.collaborators().stream()
                        .map(CustomerResponseAssembler::toResponse).toList())
                .setCustomFields(value.customFields()).setFileFields(java.util.Map.of()).setStarred(value.starred())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime()).setLastActivityTime(value.lastActivityTime())
                .setLastActivityContent(value.lastActivityContent());
    }

    static CustomerCollaboratorResponse toResponse(CustomerCollaboratorVO value) {
        return new CustomerCollaboratorResponse().setActorId(value.actorId()).setAccess(value.access());
    }

    static CustomerCollaboratorBaselineResponse toResponse(CustomerCollaboratorBaselineVO value) {
        return new CustomerCollaboratorBaselineResponse().setVersion(value.version())
                .setCollaborators(value.collaborators().stream()
                        .map(CustomerResponseAssembler::toResponse).toList())
                .setCallerState(CustomerCallerCollaboratorStateResponse.valueOf(value.callerState()));
    }

    static CustomerHierarchyResponse toResponse(CustomerHierarchyVO value) {
        return new CustomerHierarchyResponse().setRequestedCustomerId(value.requestedCustomerId())
                .setCustomers(value.customers().stream()
                        .map(CustomerResponseAssembler::toResponse).toList());
    }

    static NearbyCustomerResponse toResponse(NearbyCustomerVO value) {
        return new NearbyCustomerResponse().setCustomer(toResponse(value.customer()))
                .setDistanceMeters(value.distanceMeters());
    }

    private static CustomerAddressResponse toResponse(CustomerAddressVO value) {
        return new CustomerAddressResponse().setKey(value.key()).setLabel(value.label())
                .setCountry(value.country()).setRegion(value.region()).setCity(value.city())
                .setPostalCode(value.postalCode()).setAddressLine(value.addressLine())
                .setLongitude(value.longitude()).setLatitude(value.latitude())
                .setPrimary(value.primary());
    }
}
