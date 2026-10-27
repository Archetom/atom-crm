package io.github.archetom.crm.infra.persistence.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Conservative coordinate bounds used to reduce exact great-circle calculations. */
public record GeoBoundingBox(
        BigDecimal minLatitude,
        BigDecimal maxLatitude,
        BigDecimal minLongitude,
        BigDecimal maxLongitude,
        boolean longitudeUnbounded,
        boolean crossesAntimeridian) {

    private static final double EARTH_RADIUS_METERS = 6_371_000d;
    private static final int COORDINATE_SCALE = 7;

    public static GeoBoundingBox around(
            BigDecimal longitude, BigDecimal latitude, int radiusMeters) {
        if (longitude == null || latitude == null || radiusMeters < 1) {
            throw new IllegalArgumentException("coordinate bounds require a center and radius");
        }
        double centerLongitude = longitude.doubleValue();
        double centerLatitude = latitude.doubleValue();
        double angularDistance = radiusMeters / EARTH_RADIUS_METERS;
        double latitudeDelta = Math.toDegrees(angularDistance);
        double rawMinLatitude = Math.max(-90d, centerLatitude - latitudeDelta);
        double rawMaxLatitude = Math.min(90d, centerLatitude + latitudeDelta);

        boolean longitudeUnbounded = rawMinLatitude <= -90d || rawMaxLatitude >= 90d;
        double longitudeDelta = longitudeUnbounded ? 180d
                : Math.toDegrees(Math.asin(Math.min(1d,
                        Math.sin(angularDistance)
                                / Math.abs(Math.cos(Math.toRadians(centerLatitude))))));
        longitudeUnbounded = longitudeUnbounded || longitudeDelta >= 180d;

        double rawMinLongitude = longitudeUnbounded ? -180d
                : centerLongitude - longitudeDelta;
        double rawMaxLongitude = longitudeUnbounded ? 180d
                : centerLongitude + longitudeDelta;
        boolean crossesAntimeridian = !longitudeUnbounded
                && (rawMinLongitude < -180d || rawMaxLongitude > 180d);
        if (rawMinLongitude < -180d) {
            rawMinLongitude += 360d;
        }
        if (rawMaxLongitude > 180d) {
            rawMaxLongitude -= 360d;
        }

        return new GeoBoundingBox(
                lower(rawMinLatitude), upper(rawMaxLatitude),
                lower(rawMinLongitude), upper(rawMaxLongitude),
                longitudeUnbounded, crossesAntimeridian);
    }

    private static BigDecimal lower(double value) {
        return BigDecimal.valueOf(value).setScale(COORDINATE_SCALE, RoundingMode.FLOOR);
    }

    private static BigDecimal upper(double value) {
        return BigDecimal.valueOf(value).setScale(COORDINATE_SCALE, RoundingMode.CEILING);
    }
}
