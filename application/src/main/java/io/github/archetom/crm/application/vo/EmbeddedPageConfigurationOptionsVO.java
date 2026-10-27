package io.github.archetom.crm.application.vo;

import java.util.List;

/** Server-defined embedded-page options available to the current tenant member. */
public record EmbeddedPageConfigurationOptionsVO(
        List<BusinessObjectVO> objectTypes,
        List<String> platforms,
        List<String> statuses) {
}
