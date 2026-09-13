# Project State

## Current Architecture
- **Framework & UI**: Android Native (Kotlin) with Jetpack Compose, Material Design 3, MVVM + Clean Architecture.
- **Single Source of Truth**: Android OS `NetworkStatsManager.querySummaryForDevice()`.
  - Wi-Fi: `ConnectivityManager.TYPE_WIFI`
  - Mobile Data: `ConnectivityManager.TYPE_MOBILE` (with `subscriberId = null` for all subscriptions)
  - Download = `rxBytes` (RX)
  - Upload = `txBytes` (TX)
  - Category Total = `RX + TX`
  - Overall Total = `Wi-Fi Total + Mobile Data Total`
- **Zero-Storage / Zero-Tracking Philosophy**:
  - **No local usage database**: Room / SQLite removed.
  - **No app-owned counters**: No delta aggregation, no accumulated counters.
  - **No background services / workers**: No WorkManager, no foreground or background services.
  - **No polling / continuous sampling**: User-driven queries on demand or smart refresh.
  - **Zero Network Permissions**: `android.permission.INTERNET` is NOT requested or present.
  - **Permissions**: Only `android.permission.PACKAGE_USAGE_STATS` (Usage Access) and `android.permission.ACCESS_NETWORK_STATE`.
- **Large Range Architecture**:
  - **Total Usage**: Completely unrestricted (can query 1 year or more with just 2 IPC calls: Wi-Fi + Mobile).
  - **Daily Breakdown**: Capped at 60 days (`MAX_DAILY_BREAKDOWN_DAYS = 60`) to protect the device CPU and IPC throughput. When the range exceeds 60 days, an informative banner is displayed and the overall total remains 100% accurate and independent.

## Completed
- **Core Integration**: Complete `NetworkStatsDataSourceImpl`, `NetworkStatsRepositoryImpl`, and `NetworkUsageViewModel`.
- **Date Range Engine**: Comprehensive date range system (`Today`, `Yesterday`, `Single Day`, `Multi-Day`, `Custom Range`).
  - `Today` accurately ends at the current timestamp (`nowMs`), not tomorrow midnight.
  - Correct boundary handling with local timezones (`ZoneId.systemDefault()`) and Day Light Saving (DST) transition days (23h / 25h days).
  - Safeguards against future dates and reversed date inputs.
- **Concurrency & Refresh Engine**:
  - `SmartRefreshManager` with debounce protection.
  - Coroutine cancellation handling (`CancellationException` is properly re-thrown and never swallowed).
  - `Mutex` protection preventing duplicate or overlapping IPC calls.
  - Lifecycle awareness (`ON_RESUME` refreshes only if data is stale > 60s).
- **UI & Visualization**:
  - Polished M3 design: Metric Cards for Wi-Fi and Mobile, Ratio Bar, Usage Bar Chart, Date Range Pickers.
  - Clear user guidance explaining that data is recorded by Android OS itself.
  - Developer Diagnostics Sheet (isolated; `TrafficStats` and `queryDetails` are strictly used for developer debugging and never touch user usage calculations).
- **Cleanups & Dependency Optimizations**:
  - Removed unused dependencies: Firebase BOM/AI/AppCheck, Room, Moshi, Retrofit, OkHttp, KSP.
  - Cleaned `AndroidManifest.xml` and Gradle scripts.
  - Reduced APK size from ~22.58 MB down to ~16.53 MB (~27% reduction).
  - Created clean, focused `README.md`.
- **Comprehensive Verification**: All unit, Robolectric, and Roborazzi tests pass (`BUILD SUCCESSFUL`). Full read-only audit completed.

## Known Problems
1. **Multi-SIM OEM Differences**: Passing `subscriberId = null` to `NetworkStatsManager.querySummaryForDevice` aggregates all SIM cards on standard AOSP, but certain OEM builds (e.g., custom Xiaomi/Huawei ROMs) may restrict this to the active mobile data SIM.
2. **Kernel Network Buffer Flush Delay**: The Linux kernel flushes hardware interface statistics to `NetworkStatsManager` every few minutes to conserve battery. Consequently, traffic from the last ~30 seconds may have a slight propagation delay on physical devices.
3. **Release Keystore Decoupled from Repository**: Release signing is fully hardened to require environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`, and optionally `KEY_ALIAS`). The repository contains no in-repo keystore file (`my-upload-key.jks` removed), and release signing fails fast with an actionable error if environment variables are missing. Debug builds and CI workflows require zero release secrets.

## Current Problem
- **None**. The codebase is architecturally complete, clean, stable, and verified against all functional criteria. The application is at the final milestone: **READY FOR REAL-DEVICE TESTING**.

## Files Involved
- **Data & Repository Layer**:
  - `app/src/main/java/com/example/data/source/NetworkStatsDataSource.kt` & `NetworkStatsDataSourceImpl.kt`: Direct IPC calls to `NetworkStatsManager`.
  - `app/src/main/java/com/example/domain/repository/NetworkStatsRepository.kt` & `com/example/data/repository/NetworkStatsRepositoryImpl.kt`: Usage orchestration, parallel async queries, large range handling.
  - `app/src/main/java/com/example/data/preferences/`: `AppSettingsPreferences.kt`, `DateRangePreferences.kt`, `DeveloperPreferences.kt` (UI preferences only, zero usage bytes stored).
- **Models**:
  - `app/src/main/java/com/example/model/NetworkUsage.kt`: Immutable byte holder with RX, TX, and addition operator.
  - `app/src/main/java/com/example/model/NetworkUsageSummary.kt`: Wi-Fi + Mobile + Total summary.
  - `app/src/main/java/com/example/model/DateRange.kt`: Date range boundaries, presets, validation.
  - `app/src/main/java/com/example/model/DailyNetworkUsage.kt`: Daily breakdown item model.
  - `app/src/main/java/com/example/model/ByteUnit.kt`: Binary units (1024 base: B, KB, MB, GB, TB).
- **ViewModel & State**:
  - `app/src/main/java/com/example/ui/viewmodel/NetworkUsageViewModel.kt`: Presentation state manager, refresh mutex, concurrency handling.
  - `app/src/main/java/com/example/ui/viewmodel/NetworkUsageUiState.kt`: Sealed UI states (Loading, Success, Error, PermissionRequired).
- **UI Components & Screens**:
  - `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`: Primary dashboard with metrics, chart, and range controls.
  - `app/src/main/java/com/example/ui/screens/PermissionScreen.kt`: Explains and guides user to grant Usage Access.
  - `app/src/main/java/com/example/ui/screens/SettingsScreen.kt`: Language, theme, units, and refresh settings.
  - `app/src/main/java/com/example/ui/components/MetricCard.kt`: Download, upload, total, and percentage card.
  - `app/src/main/java/com/example/ui/components/UsageBarChart.kt`: Daily usage visualization with 60-day limit notice.
  - `app/src/main/java/com/example/ui/components/DiagnosticsSheet.kt`: Raw bucket inspection for developers.
- **Utilities**:
  - `app/src/main/java/com/example/util/ByteFormatter.kt`: Consistent binary 1024 unit formatting.
  - `app/src/main/java/com/example/util/DateTimeUtils.kt`: Date formatting and local timezone resolution.
  - `app/src/main/java/com/example/util/SmartRefreshManager.kt`: Debounced refresh trigger.
  - `app/src/main/java/com/example/util/ConnectivityObserver.kt`: Connectivity status listener.
- **Configuration & Manifest**:
  - `app/src/main/AndroidManifest.xml`: Declarations for `PACKAGE_USAGE_STATS` and `ACCESS_NETWORK_STATE`.
  - `app/build.gradle.kts`: Optimized Gradle configuration.

## Root Cause
- *Historical Issues (Resolved)*:
  - **Large Range Freeze**: Previously, querying a multi-month range triggered hundreds of synchronous IPC calls for the daily breakdown. *Resolved by decoupling total calculation from daily breakdown and enforcing a 60-day cap on the breakdown.*
  - **KSP / Unused Libraries Overhead**: KSP and unused libraries (Room, Moshi, Firebase) inflated APK size and triggered build warnings. *Resolved by removing them completely.*

## Changes Already Made
- Strict enforcement of `NetworkStatsManager` as the single source of truth.
- Elimination of all app-owned usage tracking, databases, and background polling.
- Unit conversion standardized to base 1024 (`ByteUnit.kt`).
- Complete overhaul of date-range boundaries and DST compliance.
- Concurrency hardening using `Mutex` and non-cancellation swallowing.
- Full project cleanup and dependency pruning.

## Remaining Work
- **Physical Device Field Testing**:
  1. Test granting Usage Access permission on a fresh installation.
  2. Verify Wi-Fi and Mobile Data tracking independently and during switching.
  3. Validate `Today`, `Yesterday`, and custom ranges against Android OS Settings ("Data Usage").
  4. Verify behavior in Dual-SIM environments and with active VPN connections.

## Tests
- `app/src/test/java/com/example/DailyBreakdownAndTotalIndependenceTest.kt`: Verifies total calculation independence, 60-day limit, and resilience to daily breakdown failures.
- `app/src/test/java/com/example/DateRangeAndDateTimeTest.kt`: Verifies timezone conversions, DST transition days (23h / 25h), future date clamping, and reversed range normalization.
- `app/src/test/java/com/example/CancellationAndConcurrencyTest.kt`: Verifies coroutine cancellation propagation and mutex locking.
- `app/src/test/java/com/example/ExampleRobolectricTest.kt`: Robolectric CUJ verification.
- `app/src/test/java/com/example/GreetingScreenshotTest.kt`: Roborazzi UI screenshot verification.
- **Status**: All tests pass (`gradle :app:testDebugUnitTest` -> `BUILD SUCCESSFUL`).
