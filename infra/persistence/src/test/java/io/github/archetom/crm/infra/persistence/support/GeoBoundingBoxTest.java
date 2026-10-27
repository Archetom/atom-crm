package io.github.archetom.crm.infra.persistence.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoBoundingBoxTest {

    @Test
    void regularBoundsContainCenterWithoutCrossingAntimeridian() {
        GeoBoundingBox bounds = GeoBoundingBox.around(
                new BigDecimal("121.4737"), new BigDecimal("31.2304"), 1_000);

        assertTrue(bounds.minLatitude().compareTo(new BigDecimal("31.2304")) < 0);
        assertTrue(bounds.maxLatitude().compareTo(new BigDecimal("31.2304")) > 0);
        assertTrue(bounds.minLongitude().compareTo(new BigDecimal("121.4737")) < 0);
        assertTrue(bounds.maxLongitude().compareTo(new BigDecimal("121.4737")) > 0);
        assertEquals(0.0089932d,
                bounds.maxLatitude().subtract(new BigDecimal("31.2304")).doubleValue(), 0.000001d);
        assertEquals(0.010520d,
                bounds.maxLongitude().subtract(new BigDecimal("121.4737")).doubleValue(), 0.00001d);
        assertFalse(bounds.longitudeUnbounded());
        assertFalse(bounds.crossesAntimeridian());
    }

    @Test
    void datelineBoundsUseAWrappedLongitudePredicate() {
        GeoBoundingBox bounds = GeoBoundingBox.around(
                new BigDecimal("179.999"), BigDecimal.ZERO, 10_000);

        assertTrue(bounds.crossesAntimeridian());
        assertFalse(bounds.longitudeUnbounded());
        assertEquals(0.089932d,
                bounds.maxLatitude().doubleValue(), 0.000001d);
        assertEquals(179.909068d, bounds.minLongitude().doubleValue(), 0.000001d);
        assertEquals(-179.911068d, bounds.maxLongitude().doubleValue(), 0.000001d);
    }

    @Test
    void polarBoundsDoNotApplyLongitudeRestriction() {
        GeoBoundingBox bounds = GeoBoundingBox.around(
                BigDecimal.ZERO, new BigDecimal("89.999"), 10_000);

        assertTrue(bounds.longitudeUnbounded());
        assertFalse(bounds.crossesAntimeridian());
    }
}
