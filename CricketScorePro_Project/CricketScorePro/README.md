# CricketScore Pro

Offline-first native Android cricket scoring foundation.

## Included
- Kotlin + Jetpack Compose + Material 3
- Room local database
- No login and no internet required for scoring
- Player and team creation
- Match creation
- T10/T20/custom-over style configuration
- Tennis/tape-ball/hard-ball text configuration
- Persistent delivery-by-delivery scoring
- 0–6 runs
- Wide, no-ball, bye and leg-bye
- Wickets and wicket type
- Legal-ball counting
- Overs, score, wickets and run rate
- Undo last delivery
- Ball-by-ball history
- Match history

## Open and build
1. Install Android Studio (latest stable).
2. Open this `CricketScorePro` folder.
3. Let Android Studio download the Gradle/Android dependencies.
4. Use JDK 17.
5. Run the `app` configuration on an Android device/emulator.
6. For a debug APK use Android Studio's Build > Build APK(s).

The debug APK is normally produced under:
`app/build/outputs/apk/debug/app-debug.apk`

## Important scope note
This package is a clean, compile-oriented V1 foundation rather than a claim that every advanced requirement in the very large specification is already implemented. The database and scoring core are intentionally structured so the remaining modules (full innings/playing-XI workflow, complete career statistics, rankings, partnerships, tournaments, analytics, PDF/CSV, backup/restore, super-over/DLS and advanced scorecards) can be added without replacing the foundation.

## Scoring note
For production release, add automated unit tests for every scoring rule before relying on the app for official matches. The current quick-score screen intentionally keeps player selection simple so the foundation remains easy to build and extend.
