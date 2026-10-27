package io.github.archetom.crm.infra.rest.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeadIntakeClientAddressResolverTest {

    @Test
    void ignoresForwardedHeadersFromAnUntrustedPeer() {
        LeadIntakeClientAddressResolver resolver = new LeadIntakeClientAddressResolver("");
        MockHttpServletRequest request = request("192.0.2.10", "198.51.100.8");

        assertEquals("192.0.2.10", resolver.resolve(request));
    }

    @Test
    void resolvesTheRightmostUntrustedForwardedAddressThroughTrustedProxies() {
        LeadIntakeClientAddressResolver resolver = new LeadIntakeClientAddressResolver(
                "192.0.2.10, 192.0.2.11");
        MockHttpServletRequest request = request("192.0.2.10",
                "203.0.113.3, 198.51.100.8, 192.0.2.11");

        assertEquals("198.51.100.8", resolver.resolve(request));
    }

    @Test
    void fallsBackToTheTrustedPeerAtTheFirstMalformedForwardedHop() {
        LeadIntakeClientAddressResolver resolver = new LeadIntakeClientAddressResolver(
                "192.0.2.10, 192.0.2.11");
        MockHttpServletRequest request = request("192.0.2.10",
                "203.0.113.9, 198.51.100.8:443, 192.0.2.11");

        assertEquals("192.0.2.10", resolver.resolve(request));
    }

    @Test
    void combinesAllForwardedHeaderLinesInWireOrder() {
        LeadIntakeClientAddressResolver resolver = new LeadIntakeClientAddressResolver(
                "192.0.2.10, 192.0.2.11");
        MockHttpServletRequest request = request("192.0.2.10", "203.0.113.66");
        request.addHeader(LeadIntakeClientAddressResolver.FORWARDED_FOR_HEADER,
                "198.51.100.8, 192.0.2.11");

        assertEquals("198.51.100.8", resolver.resolve(request));
    }

    @Test
    void rejectsMalformedIpv4WithoutTreatingItAsAHostName() {
        LeadIntakeClientAddressResolver resolver = new LeadIntakeClientAddressResolver("");

        assertEquals("unknown", resolver.resolve(request("1.2.3.4.5", null)));
        assertEquals("unknown", resolver.resolve(request("999.2.3.4", null)));
    }

    @Test
    void normalizesBothIPv4MappedIpv6LiteralFormsToTheirIpv4Address() {
        LeadIntakeClientAddressResolver resolver = new LeadIntakeClientAddressResolver("");

        assertEquals("192.0.2.1", resolver.resolve(request("::ffff:192.0.2.1", null)));
        assertEquals("192.0.2.1", resolver.resolve(request("::ffff:c000:201", null)));
    }

    private MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) {
            request.addHeader(LeadIntakeClientAddressResolver.FORWARDED_FOR_HEADER, forwardedFor);
        }
        return request;
    }
}
