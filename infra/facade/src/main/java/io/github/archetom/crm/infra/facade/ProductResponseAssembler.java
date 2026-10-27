package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.ProductCategoryResponse;
import io.github.archetom.crm.api.dto.response.ProductEditableBaselineResponse;
import io.github.archetom.crm.api.dto.response.ProductResponse;
import io.github.archetom.crm.application.vo.ProductCategoryVO;
import io.github.archetom.crm.application.vo.ProductEditableBaselineVO;
import io.github.archetom.crm.application.vo.ProductVO;

final class ProductResponseAssembler {

    private ProductResponseAssembler() {
    }

    static ProductResponse toResponse(ProductVO value) {
        return new ProductResponse().setId(value.id())
                .setCategoryId(value.categoryId()).setCategoryName(value.categoryName())
                .setOwnerActorId(value.ownerActorId()).setName(value.name())
                .setCode(value.code()).setUnit(value.unit()).setListPrice(value.listPrice())
                .setDescription(value.description()).setAvailability(value.availability())
                .setStatus(value.status()).setCustomFields(value.customFields())
                .setFileFields(java.util.Map.of())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }

    static ProductEditableBaselineResponse toResponse(ProductEditableBaselineVO value) {
        ProductEditableBaselineResponse response = new ProductEditableBaselineResponse()
                .setId(value.id()).setVersion(value.version()).setEditable(value.editable());
        if (!value.editable()) {
            return response;
        }
        return response.setName(value.name()).setCode(value.code())
                .setCategoryId(value.categoryId()).setUnit(value.unit())
                .setListPrice(value.listPrice()).setDescription(value.description());
    }

    static ProductCategoryResponse toResponse(ProductCategoryVO value) {
        return new ProductCategoryResponse().setId(value.id())
                .setParentCategoryId(value.parentCategoryId()).setName(value.name())
                .setStatus(value.status()).setVersion(value.version())
                .setChildren(value.children().stream()
                        .map(ProductResponseAssembler::toResponse).toList());
    }
}
