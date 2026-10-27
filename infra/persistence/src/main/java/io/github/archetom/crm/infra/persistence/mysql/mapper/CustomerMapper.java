package io.github.archetom.crm.infra.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerDistancePO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerExportRootPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerExportValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerHierarchyStatsPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface CustomerMapper extends BaseMapper<CustomerPO> {

    List<Long> selectHierarchyPath(
            @Param("tenantId") Long tenantId,
            @Param("customerId") Long customerId,
            @Param("depthLimit") Integer depthLimit);

    CustomerHierarchyStatsPO selectHierarchySubtreeStats(
            @Param("tenantId") Long tenantId,
            @Param("customerId") Long customerId,
            @Param("nodeLimit") Integer nodeLimit,
            @Param("depthLimit") Integer depthLimit);

    List<Long> selectVisibleDescendantIds(
            @Param("tenantId") Long tenantId,
            @Param("customerId") Long customerId,
            @Param("actorId") Long actorId,
            @Param("dataScope") String dataScope,
            @Param("accessMode") String accessMode,
            @Param("departmentIds") List<Long> departmentIds,
            @Param("resultLimit") Integer resultLimit,
            @Param("maxDepth") Integer maxDepth,
            @Param("traversalLimit") Integer traversalLimit);

    List<Long> selectVisibleAncestorIds(
            @Param("tenantId") Long tenantId,
            @Param("customerId") Long customerId,
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

    IPage<CustomerDistancePO> selectNearbyPage(
            Page<CustomerDistancePO> page,
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

    List<CustomerExportRootPO> selectExportRoots(
            @Param("tenantId") Long tenantId,
            @Param("customerIds") List<Long> customerIds);

    List<CustomerExportValuePO> selectExportValues(
            @Param("tenantId") Long tenantId,
            @Param("customerIds") List<Long> customerIds,
            @Param("fieldCodes") List<String> fieldCodes);
}
