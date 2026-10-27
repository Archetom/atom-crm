package io.github.archetom.crm.infra.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactDistancePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactExportRootPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactExportValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactHierarchyStatsPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ContactMapper extends BaseMapper<ContactPO> {

    List<Long> selectHierarchyPath(
            @Param("tenantId") Long tenantId,
            @Param("contactId") Long contactId,
            @Param("depthLimit") Integer depthLimit);

    ContactHierarchyStatsPO selectHierarchySubtreeStats(
            @Param("tenantId") Long tenantId,
            @Param("contactId") Long contactId,
            @Param("nodeLimit") Integer nodeLimit,
            @Param("depthLimit") Integer depthLimit);

    List<Long> selectVisibleDescendantIds(
            @Param("tenantId") Long tenantId,
            @Param("contactId") Long contactId,
            @Param("actorId") Long actorId,
            @Param("dataScope") String dataScope,
            @Param("accessMode") String accessMode,
            @Param("departmentIds") List<Long> departmentIds,
            @Param("resultLimit") Integer resultLimit,
            @Param("maxDepth") Integer maxDepth,
            @Param("traversalLimit") Integer traversalLimit);

    List<Long> selectVisibleAncestorIds(
            @Param("tenantId") Long tenantId,
            @Param("contactId") Long contactId,
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

    IPage<ContactDistancePO> selectNearbyPage(
            Page<ContactDistancePO> page,
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

    List<ContactExportRootPO> selectExportRoots(
            @Param("tenantId") Long tenantId,
            @Param("contactIds") List<Long> contactIds);

    List<ContactExportValuePO> selectExportValues(
            @Param("tenantId") Long tenantId,
            @Param("contactIds") List<Long> contactIds,
            @Param("fieldCodes") List<String> fieldCodes);
}
