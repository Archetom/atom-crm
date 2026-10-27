package io.github.archetom.crm.application.vo;

import java.util.List;

public record CrmImportTemplateVO(String objectType, List<String> columns) { }
