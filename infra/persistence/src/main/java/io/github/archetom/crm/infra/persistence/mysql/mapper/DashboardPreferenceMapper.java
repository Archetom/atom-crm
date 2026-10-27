package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.DashboardPreferencePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DashboardPreferenceMapper extends BaseMapper<DashboardPreferencePO> {
}
