package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.NavigationPreferencePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NavigationPreferenceMapper extends BaseMapper<NavigationPreferencePO> {
}
