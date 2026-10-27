package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.InvoiceProfilePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InvoiceProfileMapper extends BaseMapper<InvoiceProfilePO> {
}
