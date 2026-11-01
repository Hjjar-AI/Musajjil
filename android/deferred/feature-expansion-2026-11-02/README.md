# Feature expansion — stopped at the user's request

**Resumed on 2026-11-02 at the user's subsequent request.** The saved source has
been copied back into the active app, while retaining the bitmap icon. This
folder is now a historical checkpoint. The user explicitly requested no build
or APK installation during the resumed implementation; use the main Android
README for current status. The statements below describe the stopped checkpoint.

Stopped on 2026-11-02. The user asked to skip these features for now and work on
the app icon. This folder preserves the unfinished work; it is outside the
Android application's source sets and is **not compiled or shipped**.

## Working app

The active application has been restored to its working database-v1 foundation.
The following icon update uses app version 0.1.1 / version code 2, with database
version 1. The draft version-2 database was never installed on the phone.

The previously agreed scope remains in [the Android plan](../../../docs/ANDROID_PLAN.md).
Backup/restore, service/contracts, runtime custom fields/groups/bulk changes,
additional leave types, hourly corrections and a readable history are still
pending in the installed application.

## Preserved draft

`source/` holds complete copies of 19 edited/new files, with paths relative to
`android/`. `tracked-changes.patch` records edits to the eight tracked files;
new files are retained separately in `source/`. `base-commit.txt` identifies the
baseline. Do not apply the patch blindly over later changes, including icon and
version updates.

Written but **not compiled or tested** before stopping:

- A proposed Room 1 → 2 migration, new entities, stable department/group/field
  identifiers, default field definitions, and preservation of existing records.
- Repository methods for custom fields, classifications, multiple tags, bulk
  employee changes, leave types, service periods/profiles, archive/return dates,
  hourly corrections and reversal events.
- Calendar service calculations: inclusive merged periods, manual deductions,
  stored target anchor/duration, month-end handling, and completion estimates.
- Hour correction planner using saved thresholds/source limits. It preserves
  earlier conversions where possible, reverses/replaces affected groups, then
  permits current-year pending conversion under the current threshold.
- Versioned ZIP/JSON backup codec with checksums and a proposed restore validator.
- SAF folder storage, read-back verification, manual/pre-restore copies,
  WorkManager weekly scheduling/catch-up, and retention of eight verified
  automatic copies. Draft restore creates a safety copy before replacement.
- Shared selection controls and Arabic resource strings for the proposed UI.

Dependency resolution succeeded for the draft. No feature build, migration test,
backup round-trip test, unit test, or phone validation was performed for it.

## Remaining before resuming

1. Review the draft models and correction examples, including multiple
   thresholds, moved/deleted source entries, past-year limits, partial groups,
   and repeated corrections. Add independent expected-result tests.
2. Compile the draft and verify the migration against the exported v1 schema
   and a copy of real v1 data. Verify fresh-install defaults too.
3. Build and connect settings/forms for backups and restore review, service and
   contracts, custom field definitions/typed values, group membership, bulk
   selection/preview, leave types, corrections/deletions, and readable history.
4. Connect the ViewModel, application lifecycle and worker scheduling. These
   integrations are incomplete in the saved draft.
5. Test snapshot round trips, invalid references and conversion consumption,
   corrupted/oversized/unsupported archives, revoked folder access, interrupted
   writes, pruning, and restore rollback. Test recovery of the safety copy.
6. Confirm archive closes service at the entered final date, and return starts
   an explicitly dated period. Verify contract expiry only warns.
7. Add app/device integration tests using isolated databases. Do not replace the
   phone's live records during automated tests.

The feature work remains deferred until the user asks to resume it.
