package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.CrmImportDraftPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CrmImportDraftMapper extends BaseMapper<CrmImportDraftPO> { }
