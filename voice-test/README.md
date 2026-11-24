# Private OpenAI WebRTC voice test

This is an undeployed test scaffold for the owner's own microphone, separate from carrier calls. The Android app opens the HTTPS test page in the system browser. It intentionally does not acquire a new microphone permission, capture phone-call audio, change SIM settings, or enable cloud processing of calls.

The server creates a Realtime WebRTC session using OpenAI's unified interface. Audio travels directly over the negotiated WebRTC connection; the backend handles the SDP exchange only. The model is gpt-realtime-2.1-mini; the user can choose Marin or Cedar and Emirati Arabic, Modern Standard Arabic, or automatic language matching.

## Before deployment

- Use an authorized Cloudflare account and an OpenAI API project with billing enabled and an appropriate spending policy. The API is separately billed.
- Review wrangler.toml and choose a unique rate-limit namespace in your account. Cloudflare rate limiting is approximate and per location, not a global cost cap.
- Set OPENAI_API_KEY and VOICE_TEST_TOKEN as Cloudflare Worker secrets through the provider's secret-management interface. Never commit them, paste them in the Android app, or include them in the APK. The test token must be a separate randomly generated 32–128 character URL-safe secret; it is not the OpenAI key. Restrict access to the owner, and revoke the test token when finished.
- Use the project's official Wrangler CLI to deploy from voice-test/ only after configuring secrets. The server fails closed for missing credentials or a missing rate limiter. No deployment or API key creation is performed by this commit.
- Enter the deployed HTTPS origin in AI Settings, test /health, then open OpenAI Voice Test. A successful /health only proves reachability.
- End any phone call and turn off auto-reply before testing. In the browser, enter the test token, choose a voice/language, agree to cloud processing and start. Stop explicitly when done. The browser closes microphone tracks and the peer connection after 120 seconds or when the page is hidden; this is not a server-enforced maximum.
- Do not use the test for other people's calls. A production call bridge would require a supported telephony connection, local caller consent before transmitting content, proper device authentication, stronger server-enforced quotas/session cancellation, and a fresh security review.

The Worker does not log or persist transcripts, microphone content, tokens or SDP. OpenAI's applicable data controls and policies still apply. The test uses no third-party JavaScript dependencies. Unit tests mock the provider and do not spend API credits.

## Checks
Run from the repository root with Node.js 22 or later:
    node --test voice-test/test/worker.test.mjs

Documentation:
- https://developers.openai.com/api/docs/guides/voice-webrtc
- https://developers.openai.com/api/reference/resources/realtime/subresources/calls/methods/create
- https://developers.cloudflare.com/workers/configuration/secrets/
