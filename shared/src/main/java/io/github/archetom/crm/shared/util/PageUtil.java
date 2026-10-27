package io.github.archetom.crm.shared.util;

import io.github.archetom.common.result.Pager;

/**
 * @author hanfeng
 */
public class PageUtil {
    /**
     * Copies page metadata but intentionally omits the object list. Unlike
     * {@code Pager.map(Function)}, this method does not transform or retain
     * elements — the caller fills {@code objectList} with mapped items itself.
     *
     * <p>Contract: {@code totalNum} is passed through unchanged, including the
     * {@link Pager#NO_TOTAL_NUM} sentinel — normalization is a boundary concern,
     * not a copier concern; {@code meta} is shared by reference, matching
     * {@code Pager.map} semantics.
     *
     * @param pager source pager, must not be null
     * @return a new pager carrying the source page metadata with a null object list
     */
    public static <T, S> Pager<S> copy(Pager<T> pager) {
        Pager<S> newPager = new Pager<>();

        newPager.setPageNum(pager.getPageNum());
        newPager.setPageSize(pager.getPageSize());
        newPager.setTotalNum(pager.getTotalNum());
        newPager.setMeta(pager.getMeta());

        return newPager;
    }
}
