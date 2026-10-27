package io.github.archetom.crm.infra.rest.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolves a forwarded client IP only when the direct peer is explicitly trusted. */
@Component
@Slf4j
public class LeadIntakeClientAddressResolver {

    static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final Set<String> trustedProxies;

    @Autowired
    public LeadIntakeClientAddressResolver(
            @Value("${atom.lead-intake.trusted-proxies:}") String configuredTrustedProxies,
            @Value("${atom.lead-intake.behind-proxy:false}") boolean behindProxy) {
        trustedProxies = Arrays.stream(configuredTrustedProxies.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(LeadIntakeClientAddressResolver::requiredAddress)
                .collect(Collectors.toUnmodifiableSet());
        if (behindProxy && trustedProxies.isEmpty()) {
            log.warn("Lead intake is configured behind a proxy but has no trusted proxy addresses; "
                    + "all requests will share the direct-peer source bucket");
        }
    }

    LeadIntakeClientAddressResolver(String configuredTrustedProxies) {
        this(configuredTrustedProxies, false);
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalizedAddress(request == null ? null : request.getRemoteAddr())
                .orElse("unknown");
        if (request == null || !trustedProxies.contains(remoteAddress)) {
            return remoteAddress;
        }
        List<String> hops = forwardedHops(request);
        if (hops.isEmpty()) {
            return remoteAddress;
        }
        for (int index = hops.size() - 1; index >= 0; index--) {
            Optional<String> hop = normalizedAddress(hops.get(index));
            if (hop.isEmpty()) {
                return remoteAddress;
            }
            if (!trustedProxies.contains(hop.get())) {
                return hop.get();
            }
        }
        return remoteAddress;
    }

    private List<String> forwardedHops(HttpServletRequest request) {
        Enumeration<String> headerValues = request.getHeaders(FORWARDED_FOR_HEADER);
        List<String> hops = new ArrayList<>();
        while (headerValues != null && headerValues.hasMoreElements()) {
            String headerValue = headerValues.nextElement();
            if (headerValue != null) {
                hops.addAll(Arrays.asList(headerValue.split(",", -1)));
            }
        }
        return List.copyOf(hops);
    }

    private static String requiredAddress(String value) {
        return normalizedAddress(value).orElseThrow(() -> new IllegalArgumentException(
                "lead intake trusted proxy must be an IP address"));
    }

    private static Optional<String> normalizedAddress(String value) {
        String candidate = value == null ? "" : value.trim();
        byte[] bytes = candidate.contains(":")
                ? parseIpv6(candidate) : parseIpv4(candidate);
        if (bytes == null) {
            return Optional.empty();
        }
        try {
            if (isIpv4Mapped(bytes)) {
                return Optional.of(InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16))
                        .getHostAddress());
            }
            InetAddress address = InetAddress.getByAddress(bytes);
            if (bytes.length == 4) {
                return Optional.of(address.getHostAddress());
            }
            if (address instanceof Inet6Address) {
                return Optional.of(address.getHostAddress());
            }
        } catch (UnknownHostException ignored) {
            // The parsed byte lengths are fixed; retain a fail-closed fallback for completeness.
        }
        return Optional.empty();
    }

    private static byte[] parseIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] bytes = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.length() > 3
                    || !part.chars().allMatch(valueCharacter ->
                            valueCharacter >= '0' && valueCharacter <= '9')) {
                return null;
            }
            int parsed;
            try {
                parsed = Integer.parseInt(part);
            } catch (NumberFormatException exception) {
                return null;
            }
            if (parsed > 255) {
                return null;
            }
            bytes[index] = (byte) parsed;
        }
        return bytes;
    }

    private static byte[] parseIpv6(String value) {
        if (value.isEmpty() || value.indexOf('%') >= 0
                || !value.matches("[0-9A-Fa-f:.]+")) {
            return null;
        }
        int compression = value.indexOf("::");
        if (compression != value.lastIndexOf("::")) {
            return null;
        }
        String left = compression < 0 ? value : value.substring(0, compression);
        String right = compression < 0 ? "" : value.substring(compression + 2);
        java.util.List<Integer> leftGroups = parseIpv6Groups(left, compression < 0);
        java.util.List<Integer> rightGroups = parseIpv6Groups(right, true);
        if (leftGroups == null || rightGroups == null) {
            return null;
        }
        int groupCount = leftGroups.size() + rightGroups.size();
        if (compression < 0 ? groupCount != 8 : groupCount >= 8) {
            return null;
        }
        byte[] bytes = new byte[16];
        int target = 0;
        for (int group : leftGroups) {
            bytes[target++] = (byte) (group >>> 8);
            bytes[target++] = (byte) group;
        }
        target += (8 - groupCount) * 2;
        for (int group : rightGroups) {
            bytes[target++] = (byte) (group >>> 8);
            bytes[target++] = (byte) group;
        }
        return bytes;
    }

    private static java.util.List<Integer> parseIpv6Groups(
            String value, boolean allowEmbeddedIpv4) {
        if (value.isEmpty()) {
            return java.util.List.of();
        }
        String[] parts = value.split(":", -1);
        java.util.List<Integer> groups = new java.util.ArrayList<>();
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty()) {
                return null;
            }
            if (part.indexOf('.') >= 0) {
                if (!allowEmbeddedIpv4 || index != parts.length - 1) {
                    return null;
                }
                byte[] ipv4 = parseIpv4(part);
                if (ipv4 == null) {
                    return null;
                }
                groups.add((Byte.toUnsignedInt(ipv4[0]) << 8) | Byte.toUnsignedInt(ipv4[1]));
                groups.add((Byte.toUnsignedInt(ipv4[2]) << 8) | Byte.toUnsignedInt(ipv4[3]));
            } else {
                if (part.length() > 4) {
                    return null;
                }
                try {
                    groups.add(Integer.parseInt(part, 16));
                } catch (NumberFormatException exception) {
                    return null;
                }
            }
        }
        return groups;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        if (bytes.length != 16 || bytes[10] != (byte) 0xff || bytes[11] != (byte) 0xff) {
            return false;
        }
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return true;
    }
}
