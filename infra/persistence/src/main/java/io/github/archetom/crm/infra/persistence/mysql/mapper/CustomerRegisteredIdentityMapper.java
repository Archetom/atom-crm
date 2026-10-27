package io.github.archetom.crm.infra.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerRegisteredIdentityPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CustomerRegisteredIdentityMapper extends BaseMapper<CustomerRegisteredIdentityPO> {
    @Select("""
            SELECT * FROM crm_customer_registered_identity
            WHERE tenant_id = #{tenantId}
              AND identity_scope_code = #{identityScopeCode}
              AND registry_scheme_code = #{registrySchemeCode}
              AND canonical_value = #{canonicalValue}
              AND released_time IS NULL
            FOR UPDATE
            """)
    CustomerRegisteredIdentityPO selectCurrentForUpdate(
            @Param("tenantId") long tenantId,
            @Param("identityScopeCode") String identityScopeCode,
            @Param("registrySchemeCode") String registrySchemeCode,
            @Param("canonicalValue") String canonicalValue);

    @Update("""
            UPDATE crm_customer_registered_identity
            SET released_time = UTC_TIMESTAMP(6)
            WHERE tenant_id = #{tenantId} AND customer_id = #{customerId}
              AND released_time IS NULL
            """)
    int releaseCurrentForCustomer(@Param("tenantId") long tenantId,
                                  @Param("customerId") long customerId);
}
