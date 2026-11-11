# Android ↔ Laravel SMS Gateway API Integration Contract

**Version:** 1.1.0 (implemented MVP)
**Audience:** Kotlin/Jetpack Compose Android developer using Google AI Studio  
**Location in Laravel project:** `admin/doc/ANDROID_API_INTEGRATION.md`  
**Server:** Laravel Blade admin + JSON API; Android is a separate app/repository.

> This is the normative implemented server contract. Keep this file and `admin/doc/OPENAPI.yaml` synchronized. Server tests do not establish physical SMS delivery; see `MANUAL_TEST_RESULTS.md` for evidence and pending device tests.

## 1. Base URL and conventions
- Production example: `https://gateway.example.com/api/v1` (replace with actual HTTPS hostname).
- Request/response: `Content-Type: application/json`, UTF-8. All timestamps ISO 8601 UTC, e.g. `2026-11-11T06:30:00Z`.
- IDs: UUID strings. Telephone numbers: E.164, e.g. `+919876543210` (example only).
- Device authenticated requests: `Authorization: Bearer <device_token>`; **admin Blade session is not used by Android**.
- Errors: `{ "error": { "code": "INVALID_TRANSITION", "message": "Human-readable message", "details": {} }, "request_id": "uuid" }`.
- Network timeout guideline: connect 10s, read 20s. Retry safe GETs with exponential backoff and jitter; POST retries require stable event IDs or idempotency keys.
- Do not log bearer tokens, pairing codes, SMS bodies or full phone numbers.

## 2. Pairing flow
Admin opens Devices → Pair Device. Laravel generates a 5-minute, single-use pairing code and a QR containing only `{ "base_url": "https://gateway.example.com/api/v1", "pairing_code": "12345678" }`. Pairing is permitted only when a challenge is active and no other active device is paired. Rate-limit incorrect attempts. QR contains a sensitive temporary credential; display only to an authenticated admin.

### POST `/devices/pair` (unauthenticated, pairing-code protected)
Request:
```json
{
  "pairing_code": "12345678",
  "device_name": "Office Android 01",
  "installation_id": "d3d30a76-f58c-4e25-a44a-7f3d31c887ee",
  "app_version": "1.0.0",
  "android_version": "15"
}
```
`installation_id` is a randomly generated UUID persisted for this app installation; **not** IMEI, IMSI, or Android hardware identifier.

Success `201` (token returned **once**):
```json
{
  "device_id": "6d4ac98c-7d28-4c5f-972e-f9c5b48a2e32",
  "device_token": "opaque-high-entropy-token-returned-only-once",
  "token_type": "Bearer",
  "server_time": "2026-11-11T06:30:00Z",
  "config": { "poll_interval_seconds": 15, "heartbeat_interval_seconds": 30 }
}
```
Failure: `401 INVALID_PAIRING_CODE`, `409 DEVICE_ALREADY_PAIRED`, `410 PAIRING_EXPIRED`, `429 RATE_LIMITED`. Do not disclose whether a code exists beyond necessary errors. Token must be stored encrypted/Keystore-backed on Android; server stores only hash.

## 3. Heartbeat
### POST `/device/heartbeat` (Bearer)
Request:
```json
{
  "device_id": "6d4ac98c-7d28-4c5f-972e-f9c5b48a2e32",
  "app_version": "1.0.0",
  "android_version": "15",
  "battery_pct": 84,
  "sms_permission_granted": true,
  "gateway_active": true,
  "sim_label": "SIM 1",
  "network_type": "wifi"
}
```
Response `200`:
```json
{
  "server_time": "2026-11-11T06:30:00Z",
  "device_status": "active",
  "sending_enabled": true,
  "config": { "poll_interval_seconds": 15, "heartbeat_interval_seconds": 30 }
}
```
Server validates device ID against token. `sim_label` is a user-visible label, not a sensitive subscriber identifier. `sending_enabled=false` means do not send or claim jobs.

## 4. Poll for one SMS job
### GET `/device/jobs/next` (Bearer)
Return `200` with `job:null` if none. Claim one job **atomically** only if device active, sending enabled, permitted recipient, daily cap and pacing constraints satisfied.

No job:
```json
{ "server_time": "2026-11-11T06:30:00Z", "job": null, "retry_after_seconds": 15 }
```
With job:
```json
{
  "server_time": "2026-11-11T06:30:00Z",
  "job": {
    "id": "4ed6c81f-8c43-4c64-9862-cb1f81e25a15",
    "lease_id": "16ebc183-377e-4b7e-9d0a-1bcfc9d47a67",
    "lease_expires_at": "2026-11-11T06:31:00Z",
    "destination_e164": "+919876543210",
    "body": "Your requested appointment is confirmed.",
    "subscription_preference": "user_selected_sim",
    "created_at": "2026-11-11T06:29:00Z"
  }
}
```
The Android client must persist the job and lease locally **before** acknowledging start. `lease_id` is required on all job updates. A job not acknowledged as started can be requeued after lease expiry; a started job must **not** be automatically resent if status becomes ambiguous.

## 5. Acknowledge start (before invoking SmsManager)
### POST `/device/jobs/{job_id}/start` (Bearer)
Request:
```json
{ "lease_id": "16ebc183-377e-4b7e-9d0a-1bcfc9d47a67", "event_id": "58ceac4d-9ec4-4c0f-bcaa-9d6c1a8ad1e3", "occurred_at": "2026-11-11T06:30:05Z" }
```
Response `200`:
```json
{ "job_id": "4ed6c81f-8c43-4c64-9862-cb1f81e25a15", "status": "sending", "accepted": true }
```
The client must wait for successful `200` before calling SmsManager. A duplicate start with the same `event_id` returns `200` with the same result. If the app crashes after start, **do not automatically send again**; mark locally uncertain and reconcile with server. `409 STALE_LEASE` or `409 INVALID_TRANSITION` means do not send.

## 6. Report send/delivery events
### POST `/device/jobs/{job_id}/events` (Bearer)
Request:
```json
{
  "lease_id": "16ebc183-377e-4b7e-9d0a-1bcfc9d47a67",
  "event_id": "82c33313-8427-437d-86aa-2ebce0e320e5",
  "event_type": "sms_sent",
  "occurred_at": "2026-11-11T06:30:08Z",
  "result_code": "RESULT_OK",
  "metadata": { "parts_total": 1 }
}
```
Response `200`:
```json
{ "job_id": "4ed6c81f-8c43-4c64-9862-cb1f81e25a15", "status": "sent", "event_accepted": true }
```
Allowed `event_type` values:
- `sms_sent`: Android `sentIntent` reports successful send to radio/network (`RESULT_OK`). **Not proof of handset delivery**.
- `sms_send_failed`: Android `sentIntent` reports failure; include sanitized `result_code` and optional `failure_code` such as `NO_SERVICE`, `RADIO_OFF`, `GENERIC_FAILURE`, `PERMISSION_DENIED`.
- `sms_delivered`: Android `deliveryIntent` reports successful delivery where supported; may be absent.
- `sms_delivery_failed`: Android delivery callback indicates failure where supported; record outcome without pretending delivery was successful.

For multipart SMS, track **each part's sent callback** locally and report one aggregate `sms_sent` only when all parts succeed; report `sms_send_failed` if any part fails, with part count and sanitized failure. Delivery for multipart SMS must be treated conservatively: do not claim whole-message delivery without adequate confirmation for all parts. Use `SmsManager.divideMessage()` and `sendMultipartTextMessage()` when needed.

Duplicate `event_id` with identical payload returns same successful response; duplicate ID with different payload returns `409 EVENT_ID_CONFLICT`. Stale lease/illegal state returns `409`. Do not create a second SMS send when event upload fails: retry **only the event report** with the same `event_id`.

## 7. Reconcile local job state
### GET `/device/jobs/{job_id}` (Bearer)
Response `200`:
```json
{
  "job_id": "4ed6c81f-8c43-4c64-9862-cb1f81e25a15",
  "status": "sending",
  "lease_id": "16ebc183-377e-4b7e-9d0a-1bcfc9d47a67",
  "last_event_at": "2026-11-11T06:30:05Z"
}
```
Use after app restart/network recovery to decide whether an event needs resubmission. A `sending` state with unknown local execution **must not trigger an automatic resend**. Show 'Needs review' in the app/admin.

## 8. Unpair/revocation behavior
Admin can revoke the device. Every protected request must immediately fail with `403 DEVICE_REVOKED`. Android should stop polling/sending, clear token and sensitive local jobs after safe reconciliation, and return to pairing screen. Never attempt to bypass revocation.

## 9. Admin-only message creation API (optional MVP integration)
This endpoint is authenticated by **Laravel admin session + CSRF** for same-origin Blade calls, or a separately scoped admin API credential if implemented later. The Android bearer token **cannot** call it.

### POST `/api/v1/admin/messages` (admin auth; `Idempotency-Key: <UUID>`)
Request:
```json
{
  "destination_e164": "+919876543210",
  "body": "Your requested appointment is confirmed.",
  "consent_reference": "customer-opt-in-record-123"
}
```
Response `201`:
```json
{ "id": "4ed6c81f-8c43-4c64-9862-cb1f81e25a15", "status": "queued", "created_at": "2026-11-11T06:29:00Z" }
```
Validation: `422` for invalid E.164, missing consent basis, oversized body, or unsupported destination; `403` for disabled sending; `409` for suppressed recipient or mismatched idempotency key; `429` for quota/rate limit. Do not expose this route to the Android device.

## 10. Android implementation requirements
1. **Screens:** Pair Device (QR + manual code), Gateway Home (connected/disconnected, selected SIM, SMS permission, active toggle), Recent Jobs (local job states), Settings (server URL, notification/permissions), Troubleshooting.
2. **Permissions:** `INTERNET`, `SEND_SMS` with runtime permission request; `POST_NOTIFICATIONS` where relevant; declare only required foreground-service permissions/types for the targeted SDK. Avoid `READ_SMS`, `RECEIVE_SMS`, contacts and device identifiers.
3. **Architecture:** MVVM, Retrofit/OkHttp, Kotlin coroutines, Room for durable local job/event ledger, Android Keystore-backed secret storage, foreground service only where supported/allowed. WorkManager for recovery—not guaranteed 15-second background polling.
4. **SIM selection:** list available active subscriptions via permitted APIs; explicitly choose one; use `SmsManager.getSmsManagerForSubscriptionId()` or current supported equivalent, checking API-level behavior. If SIM missing/changed, pause and surface error.
5. **Job ledger:** locally persist job ID, lease ID, start event ID, send-invoked flag, per-part callbacks, report event IDs and upload state. Use a mutex/single worker to prevent parallel sends.
6. **Ordering:** persist job → call `/start` → persist confirmed start → invoke SmsManager exactly once → await callbacks → persist event → POST event until acknowledged. After crash in ambiguous window, do not resend automatically.
7. **Connectivity:** poll every server-advised 15 seconds while actively running; heartbeat every 30 seconds; exponential backoff with jitter on errors; respect `429 Retry-After`.
8. **Notifications:** persistent status notification if operating as a permitted foreground service; show permission errors and disconnected state clearly. Background execution may be limited by device OEM and Android version.
9. **Security:** reject HTTP in production; no hardcoded secrets; store bearer token securely; redact logs; no untrusted URL redirect with bearer token; certificate validation enabled.
10. **Manual testing:** test SMS permission denial, SIM absent, airplane mode, dual SIM, multipart Unicode, app kill/restart, device revoke, expired lease, network interruption after send, delayed/absent delivery callback, and duplicate event retries.

## 11. HTTP error codes
| HTTP | Code | Client action |
|---|---|---|
| 400 | BAD_REQUEST | Show validation issue |
| 401 | UNAUTHORIZED | Stop and re-pair if token invalid |
| 403 | DEVICE_REVOKED / SENDING_DISABLED | Stop sending; show status |
| 409 | STALE_LEASE / INVALID_TRANSITION / EVENT_ID_CONFLICT | Do not send; reconcile |
| 410 | PAIRING_EXPIRED | Ask admin for new QR |
| 422 | VALIDATION_ERROR | Fix request; do not blindly retry |
| 429 | RATE_LIMITED | Respect Retry-After |
| 500/503 | SERVER_ERROR / MAINTENANCE | Back off; do not resend SMS |

## 12. Integration acceptance tests
- Pairing returns device ID and one-time token; wrong/expired code rejected; second device rejected.
- Heartbeat updates Blade dashboard within 90 seconds.
- Admin queues one eligible message; Android polls, starts, sends once, reports sent; dashboard updates.
- Delivery status is displayed only after supported delivery callback.
- Restart after start acknowledgment does **not** cause duplicate send.
- Network outage after physical SMS send retries event upload, not SMS transmission.
- Device revocation blocks all API calls.
- Consent/suppression/caps are enforced server-side.
- Every endpoint matches `OPENAPI.yaml` and example payloads.

## 13. Known MVP limitations
No guaranteed SMS delivery, no carrier-grade throughput, no automatic retries of ambiguous sends, no iOS gateway, no public bulk campaign feature, no commercial DLT integration, no billing/multi-tenant SaaS, no reliable background polling guarantee on every Android phone, and no guarantee of Google Play approval for SMS permissions.

## 14. Version 1.1 implementation decisions (normative)

- Routes and example fields above are implemented. `POST /admin/messages` is implemented with a same-origin Laravel admin session and CSRF token, never device bearer authentication. Missing CSRF returns 419 `CSRF_MISMATCH`. Unauthorized Blade requests redirect to login; API requests use the JSON error envelope even without an Accept header.
- Pairing has a five-minute TTL, five failed attempts per challenge, and five requests/minute/IP. A new challenge invalidates the previous challenge. Re-pairing requires revocation first and produces a new random token. Reused codes return 401. Only correct expired challenges need replacement; the expiry response is intentionally coarse.
- Device routes are limited to 60 requests/minute/IP. Login is limited to five requests/minute/IP and email hash. Admin mutations are limited to 30/minute/admin. Honor Retry-After on 429, including event uploads.
- A heartbeat must be less than 90 seconds old, with `gateway_active=true`, `sms_permission_granted=true`, and a nonempty user SIM label, before polling can claim work. This is reported readiness, not proof of Android capabilities. `network_type` is optional and restricted to `wifi`, `mobile`, `none`, `other`.
- Poll always returns `retry_after_seconds`, including when a job exists. All responses are private/no-store. A poll GET mutates queue state; do not prefetch, cache, or share its result between workers.
- One installation has at most one leased or sending message at a time. Leases last 60 seconds. Only unstarted expired leases are returned to queued, with a fresh lease ID on the next claim. `attempt_count` counts leases. Revocation makes leased/sending jobs unknown; it does not requeue them.
- A started job without authoritative callback becomes unknown after 10 minutes, during polling or minute-by-minute maintenance. New callback events for unknown jobs return 409 `INVALID_TRANSITION`; reconcile manually. Exact previously accepted event retries still return the original success response. The same response replay does **not** authorize a second SmsManager call. The Android durable ledger decides whether invocation is already performed or ambiguous.
- State updates use server receipt time, never the phone clock, for deadlines and quota. `occurred_at` must be an ISO8601 UTC timestamp ending in `Z`, with optional 1–6 fractional digits. It is retained as device-provided evidence and does not decide transition ordering.
- Daily cap is 30 at most and counts starts, including failed/unknown results, per UTC day. Minimum spacing is at least 60 seconds. Both checks run again at start. Limits are installation-wide to survive re-pairing; they cannot be raised beyond MVP maxima. Carrier limits can be lower. Message creation may queue future work; quota is enforced at claim/start rather than rejecting a queue merely because today's quota is full.
- The server permits these configurable countries only: IN (+91), GB (+44), AU (+61), DE (+49), FR (+33), SG (+65). Default is IN. E.164 syntax plus the country calling-code allowlist is validated; this is not proof that a number exists or is mobile. Shared-code country disambiguation (for example +1) is deliberately unsupported.
- Suppression and country policy are checked both at claim and start. Suppressed queued messages remain visible and can be cancelled. Sending disabled blocks creation/claim/start, but reporting already-started callbacks remains allowed. No server can recall a send after start acknowledgment has reached the phone.
- Body limit is configurable from 1–1000 Unicode characters; GSM-7 extension characters count twice and Unicode estimates count UTF-16 code units. Android must still use `divideMessage()` for actual segmentation. SMS bodies and consent references are encrypted with Laravel's application key. Preserve body whitespace. Other input strings follow Laravel trimming.
- Success events (`sms_sent`, `sms_delivered`) require `result_code=RESULT_OK`. Failure events require one of: `RESULT_ERROR_GENERIC_FAILURE`, `RESULT_ERROR_RADIO_OFF`, `RESULT_ERROR_NULL_PDU`, `RESULT_ERROR_NO_SERVICE`, `RESULT_ERROR_LIMIT_EXCEEDED`, `RESULT_ERROR_FDN_CHECK_FAILURE`, `RESULT_ERROR_SHORT_CODE_NOT_ALLOWED`, `RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED`, `RESULT_ERROR_UNKNOWN`, `RESULT_CANCELED`. Map other Android callback failures to `RESULT_ERROR_UNKNOWN`.
- Optional `failure_code` is restricted to `NO_SIM`, `NO_SERVICE`, `PERMISSION_DENIED`, `RADIO_OFF`, `GENERIC_FAILURE`, `LIMIT_EXCEEDED`, `DELIVERY_FAILED`. Only metadata `parts_total` (integer 1–20) is accepted. Never send arbitrary exception text, message text, phone numbers, or subscriber identifiers in metadata.
- Preflight no-SIM/permission/radio problems must pause the client **before** start. Do not manufacture an authoritative sent callback if SmsManager throws before returning a callback; after an acknowledged start an uncertain local failure needs review. API unreachable is a connectivity state, not an SMS failure result. Timeout produces unknown, not failed.
- `sms_delivery_failed` is accepted only from sent, changes status to unknown, records failure code and preserves sent_at. Missing delivery callbacks leave messages sent indefinitely; no claim of delivery is made. Sent/delivered multipart events require conservative aggregate callbacks for every part.
- A duplicate event is compared by job, type, lease, occurred_at, result/failure code and part count. Equivalent field order is ignored. Same event ID plus different values returns 409 `EVENT_ID_CONFLICT`; cross-job reuse is also rejected. Invalid ownership/start lease returns 409 `STALE_LEASE`; reconcile GET for another device's job returns 404.
- Message creation uses an admin-scoped UUID Idempotency-Key. Replay returns 201 with the same ID and **current** status, which may no longer be queued. Changed destination/body/consent returns 409 `IDEMPOTENCY_CONFLICT`. The client must retain the same key across creation timeouts.
- Retention defaults to 30 days: terminal message bodies, destination numbers and consent references are removed by a queued daily maintenance job. Non-content idempotency tombstones and immutable events remain to block replay. Active queued/leased/sending content is retained until terminal; operators should cancel obsolete queued jobs. Audit and suppression records remain until an operator-defined legal retention process removes them.
- Other error codes include `RECIPIENT_SUPPRESSED` (409), `DEVICE_ALREADY_PAIRED` (409), `NOT_FOUND` (404), `FORBIDDEN` (403), and `HTTPS_REQUIRED` (400). Production requires HTTPS.
