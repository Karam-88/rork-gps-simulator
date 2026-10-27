# Mock GPS provider consistency — internal validation candidate

Base: `integration/gps-rating-internal-v28` at `68c7482bf852e98057be8f5bcff1040b5a3a049e`.
Candidate: `fix/mock-gps-provider-consistency`, version 1.1.27-internal-test (32).

## Investigation

The reported Google Maps jumps have not been reproduced on a connected device in
this environment. The following are verified source-level defects, not proof that
every reported jump has the same cause:

1. `MockLocationController` injected GPS, network and Android's framework fused
   provider, but never enabled Google Play Services FLP mock mode. Framework
   `LocationManager.FUSED_PROVIDER` is not the Play Services mock-mode API. Google
   documents that `setMockMode(true)` clears FLP's cache and restricts its clients
   to fixes supplied through `setMockLocation`. This missing path allows fused
   consumers to remain outside the intended mock contract.
2. Static/route loops explicitly used `Dispatchers.Default`, while stop and binder
   commands ran on main. Cancellation did not join those jobs. A tick could race
   removal/publishing, despite a comment claiming they shared a dispatcher.
3. Starting a replacement session removed and re-added each test provider. The
   removal window exposed the underlying provider. Working registrations are now
   reused, and new registrations use Android's supported replacement operation.
4. Route destination holding already existed. A zero-distance route had no loop,
   and moving routes did not checkpoint their current position. Both are corrected.

## Implementation and boundaries

- `MockLocationController.kt`: feed the same coordinates/timestamps to platform
  providers and Play Services when available; require GPS/network registration;
  report asynchronous FLP failures; acknowledge fused cleanup before recovery.
- `MockFusedPump.kt`: serialize enable, updates and disable; coalesce pending fixes
  to the newest; never queue an unbounded backlog or send a stale fix after stop.
  A failure is surfaced and ends the session rather than silently claiming success.
- `MockLocationService.kt`: all commands and ticks share main; asynchronous Google
  Tasks do not block it. Cancel ticks before cleanup, persist route checkpoints,
  keep zero-distance and completed routes alive, log lifecycle transitions in debug.
- `AppViewModel.kt`: stop completion invokes the existing recovery and rating
  qualification hooks after cleanup. Recovery eligibility/fresh-fix checks and
  blue/green notification components are unchanged.
- `MockFusedPumpTest.kt`: deterministic ordering, failures, coalescing, stop races,
  restart, repeated stop and 900 virtual heartbeat ticks.
- Build configuration: version 32, branch build trigger, JVM tests/results artifact.

No UI, animations, styles, dialogs, real-location source, manifest permissions,
or update-rate settings were changed. The existing 200–1000 ms interval is retained;
no wake lock, battery exemption, alarm, provider disabling or busy retry was added.
New `MOCK_DIAG` logs are gated by `BuildConfig.DEBUG` and contain no coordinates.

The existing location foreground-service type, permissions, ongoing notification,
`stopWithTask=false`, and sticky restart were already configured. Backgrounding
does not stop it; the user's stop-on-task-removal setting remains honored. A killed
process/OEM battery restriction can still cause a gap; sticky restart is best
effort and restores the last saved point, not full route progress. Force Stop is
not bypassed. No claim is made that ordinary apps can guarantee continuity through
arbitrary OS termination. A Google Tasks operation must settle before final cleanup
is acknowledged; no unsafe timeout declares cleanup successful while it is pending.

## Validation

Automated results and signed APK/AAB are attached to the branch's GitHub Actions
run. Unit tests use virtual ticks and do NOT establish 15-minute device endurance.
ADB was unavailable in the execution sandbox, so all device criteria below remain
pending until a tester records results. Do not merge or promote to production yet.

Install the signed AAB through Play Internal Testing for the production Maps key
and Play signing certificate. The signed APK is also provided; a key restriction
can make its embedded map unavailable even when the Play install works.

| Scenario | Procedure | Required result | Status |
|---|---|---|---|
| Static | Disable deliberate coordinate variation, mock far from real position; keep Maps foreground 15 minutes | No real-location jumps; notification stays active | Pending device |
| Route | Choose a road route longer than 15 minutes; keep Maps foreground | Continuous route following; no real jumps | Pending device |
| Background | Repeat with app switching; add a screen-off interval and record device battery mode | Active session survives ordinary backgrounding | Pending device |
| Destination | Complete a short non-loop route; wait another 15 minutes in Maps | Final coordinate continuously held until STOP | Pending device |
| Manual stop | Stop from app, repeat from notification | Blue recovery then green ONLY after fresh non-mock fix; timeout/error otherwise | Pending device |
| Races | Rapid move/pause/start/stop and immediately start again | No late fix after stop; no old cleanup disabling new session | Pending device |
| Restart | Test ordinary process recreation separately from Force Stop | Best-effort static restoration from last checkpoint; document gaps | Pending device |
| Revocation | Revoke mock-app selection during simulation | Explicit failure; no false active state or false recovery success | Pending device |

For development diagnostics, use a debug install selected as the mock app:

```sh
adb logcat -v monotonic MockLocationController:D MockLocationService:D AppViewModel:D RealLocationSource:D '*:S'
adb shell dumpsys activity services com.rork.gpssimulator
adb shell dumpsys location
```

Record device/Android/Google Play Services versions, app version, selected update
interval, battery mode, screen state, timestamps and screenshots/video from Maps.
Do not treat an app marker or a successful binder submission as proof of Maps output.

References:
- https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient#setMockMode(boolean)
- https://developer.android.com/reference/android/location/LocationManager#addTestProvider(java.lang.String,android.location.provider.ProviderProperties)
- https://developer.android.com/develop/background-work/services/fgs/service-types#location
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
