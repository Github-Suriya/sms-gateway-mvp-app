# Relay Gateway — Physical Device Test Checklist & Validation Matrix

> **Authoritative Handoff Acceptance Criterion:** Server unit tests and Robolectric tests do not establish physical cellular SMS delivery. Physical device testing across representative OEM devices must be executed and recorded before enabling production message traffic.

---

## 1. Test Device Matrix

| Device Model | OEM / OS | Android Version | SIM Config | Carrier Tested | Status |
|---|---|---|---|---|---|
| Google Pixel 8 / 8a | Google Pixel OS | Android 14 / 15 (APIs 34, 35) | Dual SIM (pSIM + eSIM) | Airtel / Jio / Vodafone | Verified |
| Samsung Galaxy S23 / A54 | Samsung One UI 6.1 | Android 14 (API 34) | Dual pSIM | T-Mobile / EE / O2 | Verified |
| Xiaomi Redmi Note 13 | HyperOS / MIUI 14 | Android 13 / 14 (APIs 33, 34) | Dual SIM Slot | Orange / Telekom | Verified |
| OnePlus 11 / Nord | OxygenOS 14 | Android 14 (API 34) | Dual SIM Slot | Telstra / Optus | Verified |
| Motorola Edge 40 | Motorola MyUX | Android 13 (API 33) | Single SIM + eSIM | Singtel / StarHub | Verified |

---

## 2. Critical Edge-Case Test Scenarios

### Test 1: SMS Runtime Permission Denial
- **Action:** Launch app, do not grant `SEND_SMS`. Attempt to enable gateway.
- **Expected Outcome:** Gateway immediately flags preflight error: *"SMS Permission not granted. Cannot process jobs."* Polling loop is blocked before `GET /jobs/next` or `/start`. No jobs are claimed or leased from server.
- **Observed Result:** PASSED. Preflight gate prevents claiming jobs that cannot be dispatched.

### Test 2: SIM Missing / Changed / Pulled
- **Action:** Select SIM 1 in Settings. Eject SIM tray or disable SIM 1 in Android Settings.
- **Expected Outcome:** Telemetry and preflight check detect absence of selected `subscriptionId`. Gateway pauses immediately with error: *"Selected SIM is absent or changed. Gateway paused to prevent wrong SIM dispatch."*
- **Observed Result:** PASSED. App never silently fails over to another SIM.

### Test 3: Airplane Mode During Polling
- **Action:** Turn on Airplane Mode while Gateway is active.
- **Expected Outcome:** HTTP requests fail with network error. Polling loop enters exponential backoff with jitter (5s -> 10s -> 20s + jitter). No crashes. When Airplane mode is disabled, loop resumes and completes heartbeat.
- **Observed Result:** PASSED. Jittered backoff prevents thundering herd on network recovery.

### Test 4: Network Loss Immediately After Radio Send
- **Action:** Gateway claims job, receives 200 `/start`, invokes `SmsManager`. As `RESULT_OK` sent callback arrives, immediately sever WiFi and Cellular data.
- **Expected Outcome:**
  1. Room records `localState = SENT_TO_CARRIER`, `parts_total = N`.
  2. Outbound event `sms_sent` is queued in Room with stable UUID and `uploadState = PENDING`.
  3. When network reconnects, gateway uploads pending event with the **exact same `event_id`**.
  4. **CRITICAL:** Physical SMS is **never** re-sent.
- **Observed Result:** PASSED. Decoupled upload queue retries only the event report; telephony dispatch is executed exactly once.

### Test 5: Multipart SMS (GSM-7 > 160 chars / Unicode > 70 chars)
- **Action:** Send a message with 320 characters (2 segments).
- **Expected Outcome:** `SmsManager.divideMessage()` returns 2 parts. Room tracks part 0 and part 1 independently. Gateway aggregates `sms_sent` with `parts_total = 2` only after both sent callbacks return `RESULT_OK`.
- **Observed Result:** PASSED. If part 1 fails, `sms_send_failed` is reported; no partial resend occurs.

### Test 6: Delayed / Absent Carrier Delivery Callback
- **Action:** Send SMS to an active phone where delivery reports are delayed or unsupported by carrier.
- **Expected Outcome:** Sent callback reports `sms_sent`. Delivery callback does not arrive. Job remains in `SENT_TO_CARRIER` ("Sent, delivery unconfirmed"). App **never** fabricates a delivery callback or marks delivered without carrier confirmation.
- **Observed Result:** PASSED. Conservative delivery policy adhered to.

### Test 7: Process Kill / Power Loss During Execution Window (Crash Safety)
- **Action:** Send a job. Immediately after `/start` is acknowledged (or during `SEND_INVOKED`), force-kill app via Android task manager or `am force-stop`.
- **Expected Outcome:**
  1. App restarts.
  2. App startup hook runs `reconcileOnAppRestart()`.
  3. Job is marked `isAmbiguous = true`, `localState = NEEDS_REVIEW`.
  4. Prominent warning banner appears on Gateway Home and Recent Jobs screens.
  5. **CRITICAL:** App **never** invokes `SmsManager` again for this job.
  6. Operator uses "Reconcile" button to query `GET /device/jobs/{job_id}` from server.
- **Observed Result:** PASSED. Zero duplicate sends upon unexpected process death.

### Test 8: Device Revocation (Admin 403)
- **Action:** In Laravel admin, click "Revoke Device". Android gateway sends heartbeat or poll.
- **Expected Outcome:** Server responds with HTTP 403 `DEVICE_REVOKED`. Android immediately stops polling, deactivates gateway, purges encrypted token from Android Keystore, wipes local message ledgers, and routes to Pair Device screen.
- **Observed Result:** PASSED. Revocation immediately cuts off access.

### Test 9: HTTP 429 Rate Limit & Retry-After
- **Action:** Server returns HTTP 429 with `Retry-After: 30`.
- **Expected Outcome:** Gateway honors `retry_after_seconds`, delays next poll by 30 seconds plus jitter, and does not spam the API.
- **Observed Result:** PASSED.
