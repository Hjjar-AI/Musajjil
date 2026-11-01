# مُسجِّل — Android

Native Kotlin / Jetpack Compose application with an Arabic RTL interface and a
local Room database. This directory is independent of the desktop application.

## Current increment

- Five tabs: home summary, employees, leave/hours, monthly calendar, and settings.
- Add/edit employees, search by name/employee number/national ID/department,
  filter by department, archive and restore. External identifiers remain text.
- Record/edit administrative leave with suggested inclusive days, editable
  confirmed amounts, cross-year allocation, department snapshots, and overlap
  warnings that permit saving.
- Record whole hours. Convert eligible current-year hours automatically at a
  configurable threshold (initially eight), preserving completed conversions.
  Days converted from hours remain separate from administrative leave.
- Configure excluded weekdays and manually dated holidays. Saved leave amounts
  and calculation snapshots do not change when settings change.
- Light/dark appearance follows the phone; calendar weeks start on Sunday.
  Dates support typing (including Arabic digits) and the native date picker.
- Persistent, transactional writes with local correction/conversion audit data.
  The app starts empty; sample employees exist only in Compose previews.

This is a local UI foundation, not the entire planned product. Accounts and
department authorization, Wi-Fi/hotspot hosting, configurable leave types,
runtime custom-field editing, tags/bulk operations, service/contract tracking,
hourly corrections, audit-history screens, Excel/CSV, PDF/Word, employee cards,
and backup/restore are pending. Holidays can currently be added; editing/removing
them is pending. There is no yearly leave allowance or remaining-balance logic.

The initial repository observes the full local register. Paging and selected-year
database queries should replace full-history observations before verifying the
30-year data-volume target. No network permission or server is included yet.
There is currently no app-level login or database encryption. This build is for
development evaluation; backup/restore must land before operational data use.

## Build

Open this directory as a project in Android Studio, or use:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Requirements: JDK 17 or newer, Android SDK platform 35, and network access for
initial Gradle/Maven dependency downloads. Set `ANDROID_HOME` or create an ignored
`local.properties` with `sdk.dir=/your/Android/Sdk`. Runtime needs Android 8.0
(API 26) or newer; this is a provisional minimum until the actual phones are
known. All versions are pinned in `gradle/libs.versions.toml`; the wrapper uses
Gradle 8.13 and checks its distribution checksum.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Debug signing is
for testing. A stable private release signing key is still needed for production
APK updates. Unit tests cover date validation, exclusions, annual allocations,
hour remainders, threshold changes and year separation. Device verification is
separate from these JVM tests and the build/lint checks.

## Customization map

| Change | Location |
| --- | --- |
| Light/dark colors | `app/src/main/java/com/musajjil/app/ui/theme/Color.kt` |
| Font sizes and line heights | `ui/theme/Type.kt` |
| Shared spacing and maximum content width | `ui/theme/Dimensions.kt` |
| Shapes, RTL and theme composition | `ui/theme/Theme.kt` |
| Arabic labels | `app/src/main/res/values/strings.xml` |
| Cards, actions, empty states, summaries | `ui/components/Surfaces.kt` |
| Text, date and dropdown controls | `ui/components/Fields.kt` |
| Sheet scrolling, keyboard insets, save action | `ui/components/FormSheet.kt` |
| Compact employee and leave rows | `ui/components/RecordRows.kt` |
| Default employee field order/labels/input types | `ui/forms/EmployeeForm.kt` → `EmployeeFormSchema` |
| Tabs and screen/form wiring | `ui/RegisterApp.kt` |
| Screen composition | `ui/screens/` |

All abbreviated `ui/` paths are under
`app/src/main/java/com/musajjil/app/`. Screens receive data and callbacks; they
do not open databases or execute SQL. Components receive controlled values and
callbacks, so their presentation can change without changing storage rules.
`ComponentPreviews.kt` provides a light/dark catalog in Android Studio.

The employee schema currently configures the built-in fields. Adding entirely
new stored fields requires the future custom-field model and a Room migration;
adding a label to the schema alone does not create a database column.

## Boundaries

```text
MainActivity → theme → RegisterApp (navigation)
                        ├─ screens (data + callbacks)
                        ├─ forms → shared components
                        └─ RegisterViewModel
                             └─ RegisterRepository (transactions + audit)
                                  ├─ domain (date / leave / conversion rules)
                                  └─ Room (entities / DAO / schema)
```

Room schema snapshots live in `app/schemas/` and should be committed when they
change. Implement explicit migrations before incrementing the database version;
there is no destructive migration fallback. Cloud/system backup is disabled
until an intentional backup/restore design is implemented.

The full target behavior remains in [the Android plan](../docs/ANDROID_PLAN.md)
and [screen flow](../docs/ANDROID_SCREEN_FLOW.md). Those documents include work
beyond this increment.
