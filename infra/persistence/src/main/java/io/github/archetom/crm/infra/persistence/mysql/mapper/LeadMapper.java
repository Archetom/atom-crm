package io.github.archetom.crm.infra.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadDistancePO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadExportRootPO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadExportValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadHierarchyStatsPO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface LeadMapper extends BaseMapper<LeadPO> {

    List<Long> selectHierarchyPath(
            @Param("tenantId") Long tenantId,
            @Param("leadId") Long leadId,
            @Param("depthLimit") Integer depthLimit);

    LeadHierarchyStatsPO selectHierarchySubtreeStats(
            @Param("tenantId") Long tenantId,
            @Param("leadId") Long leadId,
            @Param("nodeLimit") Integer nodeLimit,
            @Param("depthLimit") Integer depthLimit);

    List<Long> selectVisibleDescendantIds(
            @Param("tenantId") Long tenantId,
            @Param("leadId") Long leadId,
            @Param("actorId") Long actorId,
            @Param("dataScope") String dataScope,
            @Param("accessMode") String accessMode,
            @Param("departmentIds") List<Long> departmentIds,
            @Param("resultLimit") Integer resultLimit,
            @Param("maxDepth") Integer maxDepth,
            @Param("traversalLimit") Integer traversalLimit);

    List<Long> selectVisibleAncestorIds(
            @Param("tenantId") Long tenantId,
            @Param("leadId") Long leadId,
            @Param("actorId") Long actorId,
            @Param("dataScope") String dataScope,
            @Param("accessMode") String accessMode,
            @Param("departmentIds") List<Long> departmentIds,
            @Param("limit") Integer limit);

    List<String> selectVisibleTags(
            @Param("tenantId") Long tenantId,
            @Param("actorId") Long actorId,
            @Param("dataScope") String dataScope,
            @Param("accessMode") String accessMode,
            @Param("departmentIds") List<Long> departmentIds,
            @Param("limit") Integer limit);

    IPage<LeadDistancePO> selectNearbyPage(
            Page<LeadDistancePO> page,
            @Param("tenantId") Long tenantId,
            @Param("actorId") Long actorId,
            @Param("dataScope") String dataScope,
            @Param("accessMode") String accessMode,
            @Param("departmentIds") List<Long> departmentIds,
            @Param("longitude") BigDecimal longitude,
            @Param("latitude") BigDecimal latitude,
            @Param("minLongitude") BigDecimal minLongitude,
            @Param("maxLongitude") BigDecimal maxLongitude,
            @Param("minLatitude") BigDecimal minLatitude,
            @Param("maxLatitude") BigDecimal maxLatitude,
            @Param("longitudeUnbounded") boolean longitudeUnbounded,
            @Param("crossesAntimeridian") boolean crossesAntimeridian,
            @Param("radiusMeters") Integer radiusMeters);

    List<LeadExportRootPO> selectExportRoots(
            @Param("tenantId") Long tenantId,
            @Param("leadIds") List<Long> leadIds);

    List<LeadExportValuePO> selectExportValues(
            @Param("tenantId") Long tenantId,
            @Param("leadIds") List<Long> leadIds,
            @Param("fieldCodes") List<String> fieldCodes);
}
