package io.github.archetom.crm.shared.util;

import io.github.archetom.common.result.Pager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PageUtilTest {

    @Test
    void copiesMetadataWhileLeavingDataForTheMapper() {
        Pager<String> source = new Pager<>();
        source.setPageNum(3);
        source.setPageSize(25);
        source.setTotalNum(37L);
        source.setObjectList(List.of("source item"));
        source.setMeta(Map.of("source", "query"));

        Pager<Long> copied = PageUtil.copy(source);

        assertEquals(3, copied.getPageNum());
        assertEquals(25, copied.getPageSize());
        assertEquals(37L, copied.getTotalNum());
        assertSame(source.getMeta(), copied.getMeta());
        assertNull(copied.getObjectList());
    }

    @Test
    void preservesTheUnknownTotalSentinel() {
        Pager<String> source = new Pager<>();
        source.setPageNum(6);
        source.setPageSize(40);
        source.setTotalNum(Pager.NO_TOTAL_NUM);

        Pager<Long> copied = PageUtil.copy(source);

        assertEquals(6, copied.getPageNum());
        assertEquals(40, copied.getPageSize());
        assertEquals(Pager.NO_TOTAL_NUM, copied.getTotalNum());
    }
}
