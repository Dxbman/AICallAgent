package com.aicallagent.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AiServerEndpointTest {
    @Test public void normalizesHttpsOrigins() {
        assertEquals("https://example.com", AiServerEndpoint.normalizeOrigin(" https://example.com/ "));
        assertEquals("https://example.com:8443/health", AiServerEndpoint.healthUri("https://example.com:8443").toString());
    }

    @Test public void rejectsUnsafeOrAmbiguousUrls() {
        for (String url : new String[]{"", "http://example.com", "https://user:pass@example.com",
                "https://example.com/api", "https://example.com?token=x", "https://example.com/#secret",
                "file:///tmp/file", "https://example.com:0", "https://example.com:99999", "https://",
                "https://example.com/%2e%2e"}) {
            try {
                AiServerEndpoint.normalizeOrigin(url);
                fail("Accepted invalid origin: " + url);
            } catch (IllegalArgumentException expected) { }
        }
    }
}
