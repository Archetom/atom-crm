package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalManagerPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApprovalManagerMapper extends BaseMapper<ApprovalManagerPO> {
}
