package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.InvoiceUniqueFieldValuePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InvoiceUniqueFieldValueMapper extends BaseMapper<InvoiceUniqueFieldValuePO> {
}
