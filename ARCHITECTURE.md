# Relay Gateway — Architecture & Implementation Notes

## 1. System Overview & Technology Stack

Relay Gateway is an Android enterprise gateway application that bridges a Laravel SMS Gateway backend (`v1.1.0`) with native Android telephony (`SmsManager`).

- **Architecture:** Clean MVVM with Repository pattern and single-worker execution mutex.
- **Language & UI:** Kotlin Coroutines & Flow with Jetpack Compose and Material Design 3.
- **Local Persistence:** Room Database (`AppDatabase`) maintaining an immutable, durable send ledger and event upload queue.
- **Credential Storage:** Android Keystore-backed AES-256 GCM encryption.
- **Network Layer:** Retrofit + OkHttp with strict TLS enforcement, dynamic origin validation, cross-origin redirect token stripping, and zero logging of credentials, phone numbers, or SMS bodies.
- **Telephony Dispatch:** `SmsManager` binding to explicitly selected `SubscriptionInfo` (`createForSubscriptionId`).

---

## 2. Target SDK & Foreground Service Policy

- **Minimum SDK:** Android 10 (API 29)
- **Target SDK:** Android 15 (API 35) / Android 16 (API 36 ready)
- **Enterprise / Internal Distribution Rationale:**
  - Standard consumer apps on Google Play face restricted SMS policies and cannot obtain SMS foreground service exemptions.
  - For enterprise/internal distribution, consistent 15-second polling and 30-second heartbeats require an active user-started foreground service declared with `foregroundServiceType="dataSync"`.
  - Android 14+ (API 34+) requires explicit foreground service types and corresponding manifest permissions (`FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC`).
  - **Manual Mode Fallback:** To accommodate aggressive OEM battery optimizers (MIUI, EMUI, ColorOS) or devices where background services are restricted, a user-controllable "Manual Foreground Mode" allows running the loop entirely in the active UI without starting a background service. WorkManager provides opportunistic recovery rather than guaranteed 15-second polling.

---

## 3. Durable Send Ledger & State Sequencing

The durable ledger in Room (`sms_jobs`, `job_part_callbacks`, `outbound_events`) is the single source of truth. There is no atomic transaction across Room, the network, and the cellular radio; therefore, strict sequencing prevents duplicate transmissions:

```
[ Poll GET /device/jobs/next ]
              │
              ▼
[ Persist Job & Lease in Room DB (state: RECEIVED) ]  <── Before any network ack!
              │
              ▼
[ Preflight Check: SEND_SMS permission & selected SIM present ]
              │ (If invalid: Pause gateway, do NOT call /start)
              ▼
[ POST /device/jobs/{id}/start (stable event UUID) ]
              │
        ┌─────┴─────────────────────────┐
     200 OK                          409 / Error
        │                               │
        ▼                               ▼
[ Persist START_CONFIRMED &     [ Mark CANCELLED_STALE ]
  SEND_INTENT_PERSISTED in Room ] (Never call SmsManager)
        │
        ▼
[ SmsManager.divideMessage(body) ]
[ Persist Part Records (0..partsTotal-1) ]
[ Construct Explicit Package-Targeted PendingIntents ]
[ Invoke sendTextMessage / sendMultipartTextMessage ]
        │
        ▼
[ LocalState: CALLBACK_PROCESSING ]
        │
   ┌────┴──────────────────────────┐
All Parts OK                    Any Part Failed
   │                               │
   ▼                               ▼
[ Report sms_sent (RESULT_OK) ] [ Report sms_send_failed ]
                                (Include failure_code, parts_total)
                                (NEVER automatically resend!)
```

### Safety Rules:
1. **Never Resend on Crash / Ambiguity:** If the app is killed or restarts after `/start` acknowledgment or during invocation, the job is flagged `isAmbiguous = true`, `localState = NEEDS_REVIEW`. Even if replaying `/start` returns HTTP 200, the app **never invokes SmsManager again**. The operator must inspect the job and use the "Reconcile" action.
2. **Retry Uploads, Never SMS:** Network failure during `POST /events` retries only the event report with the same `event_id`. It never re-triggers SMS transmission.
3. **Multipart Aggregation:** `sms_sent` is reported only when all parts return `RESULT_OK`. If any part fails, `sms_send_failed` is reported. Delivery requires all parts to return delivery callbacks.

---

## 4. Security & Keystore Encryption

1. **Random Installation UUID:** Generated on first launch using `UUID.randomUUID()` and persisted in private app storage. Hardware identifiers (IMEI, MEID, IMSI, MAC) are never queried.
2. **Keystore AES-256 GCM:** The single-use bearer token returned by `POST /devices/pair` is immediately encrypted using an Android Keystore-backed key (`AndroidKeyStore` provider). Plaintext tokens never touch unencrypted storage or logs.
3. **HTTPS & Origin Enforcement:** Plaintext `http://` is blocked in production builds (`!BuildConfig.DEBUG`). The user is presented with the parsed origin before pairing.
4. **Cross-Origin Redirect Protection:** If a redirect points to a different host or scheme, the `Authorization` header is stripped immediately.

---

## 5. Release Signing Instructions (No Embedded Secrets)

To sign release builds for internal or enterprise deployment, pass credentials via environment variables:

```bash
# 1. Set environment variables (do NOT commit secrets to git)
export KEYSTORE_PATH="/path/to/enterprise-release-key.jks"
export STORE_PASSWORD="your-secure-store-password"
export KEY_PASSWORD="your-secure-key-password"

# 2. Build release bundle or APK
gradle :app:assembleRelease
```

The app's `build.gradle.kts` automatically reads `System.getenv("KEYSTORE_PATH")`, `System.getenv("STORE_PASSWORD")`, and `System.getenv("KEY_PASSWORD")`. If not provided, it falls back to root directory key placeholders without breaking development compilation.
