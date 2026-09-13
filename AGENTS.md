# NetPulse Developer & Agent Instructions

This project adheres strictly to **`PROJECT_CHANGE_POLICY.md`**.

## Core Operational Directives for Any AI Agent:
1. **Never start from scratch**: NetPulse is an existing, functional Android application.
2. **Never delete existing source files or tests** without explicit user approval. Deleted files must be 0 by default.
3. **Never replace real UI or unit tests with template or dummy tests**.
4. **Never perform global cleanup** during a targeted feature fix or bug fix.
5. **Architectural Invariants**:
   - `NetworkStatsManager.querySummaryForDevice()` is the single source of truth.
   - `rxBytes` = Download, `txBytes` = Upload, `Total` = Wi-Fi + Mobile.
   - No `INTERNET` permission.
   - No app-owned usage counter, no polling, no background tracking, no Room/SQLite usage DB.
   - Large Range Total is unrestricted; Daily Breakdown is capped at 60 days.
   - DateRange, Timezone, and DST logic must be preserved.
6. **Verification on every task**:
   - Run unit and Robolectric tests (`gradle :app:testDebugUnitTest`).
   - Run screenshot tests if applicable (`gradle :app:verifyRoborazziDebug`).
   - Verify `gradle :app:assembleDebug` and `gradle :app:lintDebug`.
7. **Task reporting**:
   - Always report modified files, created files, deleted files (must be 0), tests run and their results, and build results.
