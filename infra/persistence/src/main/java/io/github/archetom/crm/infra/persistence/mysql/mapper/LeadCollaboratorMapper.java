package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.LeadCollaboratorPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeadCollaboratorMapper extends BaseMapper<LeadCollaboratorPO> {
}
