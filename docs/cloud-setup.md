# Cloud AI preparation

The active call engine remains the existing on-device Gemma engine. The AI Settings dialog stores a preferred engine and an HTTPS server origin locally. Its explicit Test button sends only a GET request to /health, follows no redirects, uses the platform's ordinary TLS verification, and does not send call transcripts or API credentials.

## Future server requirements (not yet implemented)

- The owner's backend stores the OpenAI API key in a secret manager or environment variable; never in this public repository or the Android APK.
- Provide a minimal HTTPS GET /health endpoint. A successful health check indicates only server reachability, not OpenAI authentication.
- Before enabling cloud call processing, add an authenticated device-to-server connection, per-device authorization and rate limits, explicit caller disclosure and consent collected locally before transmitting call content, bounded requests, cancellation and a no-conversation-logging policy.
- Retain a local/offline option; never silently send local conversations to the cloud.
- The current Samsung Text Call bridge speaks with Samsung's TTS. Direct OpenAI audio would require a separate supported telephony integration and is outside this settings change.

No cloud engine or server is deployed by this commit.
