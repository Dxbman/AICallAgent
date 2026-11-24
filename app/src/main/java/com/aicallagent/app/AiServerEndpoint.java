package com.aicallagent.app;

import java.net.URI;
import java.net.URISyntaxException;

/** Validate a user-entered HTTPS origin. Never accept credentials, query strings or paths. */
final class AiServerEndpoint {
    private AiServerEndpoint() { }

    static String normalizeOrigin(String input) {
        if (input == null || input.length() > 2048) throw new IllegalArgumentException("Invalid origin");
        try {
            URI uri = new URI(input.trim());
            int port = uri.getPort();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || port == 0 || port > 65535
                    || !(uri.getRawPath() == null || uri.getRawPath().isEmpty()
                    || "/".equals(uri.getRawPath()))) {
                throw new IllegalArgumentException("HTTPS origin required");
            }
            return new URI("https", null, uri.getHost(), port, null, null, null).toASCIIString();
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("Invalid HTTPS origin");
        }
    }

    static URI healthUri(String input) {
        return URI.create(normalizeOrigin(input) + "/health");
    }
}
