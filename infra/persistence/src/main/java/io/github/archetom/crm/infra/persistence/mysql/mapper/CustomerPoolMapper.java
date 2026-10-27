package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolCatalogPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolCustomerCountPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CustomerPoolMapper extends BaseMapper<CustomerPoolPO> {

    int ensureAutomationCursor(@Param("tenantId") Long tenantId);

    Long selectAutomationCursorForUpdate(@Param("tenantId") Long tenantId);

    int updateAutomationCursor(
            @Param("tenantId") Long tenantId,
            @Param("lastPoolId") Long lastPoolId);

    Long selectNonDeletedCountForUpdate(@Param("tenantId") Long tenantId);

    List<CustomerPoolCatalogPO> selectCatalogEntries(
            @Param("tenantId") Long tenantId,
            @Param("memberId") Long memberId,
            @Param("departmentId") Long departmentId,
            @Param("tenantWide") boolean tenantWide,
            @Param("limit") int limit);

    boolean existsActiveAutomaticRelease(@Param("tenantId") Long tenantId);

    List<CustomerPoolCustomerCountPO> selectCustomerCounts(
            @Param("tenantId") Long tenantId,
            @Param("poolIds") List<Long> poolIds);

    int transferMemberships(@Param("tenantId") Long tenantId,
                            @Param("sourcePoolId") Long sourcePoolId,
                            @Param("targetPoolId") Long targetPoolId);

    List<Long> selectAutomationCandidateIds(
            @Param("tenantId") Long tenantId,
            @Param("poolId") Long poolId,
            @Param("ruleType") String ruleType,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") Integer limit);
}
