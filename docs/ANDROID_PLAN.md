# مُسجِّل — Android product and implementation plan

Planning baseline: 2026-11-01.

This is a self-contained Android specification based on the user's decisions.
It describes the planned product. A native Android foundation now exists in
[`android/`](../android/README.md); that README distinguishes implemented features
from pending work. The project has no dependency on another application, its source code,
database, or migration process. Routine Excel/CSV exchange remains in scope.

Screen design: [Android screen flow](ANDROID_SCREEN_FLOW.md).

## Product scope

- Name: **مُسجِّل**. Arabic interface with right-to-left navigation and mobile layouts.
- Appearance follows the phone's light/dark setting. Calendar weeks start on
  Sunday. Date fields support both direct typing and a calendar picker.
- Native Android application, distributed as a directly installed signed APK.
- All included functionality works without internet access. Package runtime
  assets, fonts, and report resources locally.
- Store the main database on the phone. Optional hosting supports 3–5 users
  through browsers or installed Android clients over Wi-Fi or the phone's hotspot.
- Users: owner/administrator, HR staff, and managers.
- Simple manager-access default: managers see employees currently in their
  assigned departments and those employees' records. Historical reports retain
  the department saved on each record, without a separate historical-access system.
- Contract expiry warns only; it does not automatically stop service or archive
  an employee. Stay signed in until explicit logout, subject to revoked access.
- Expected scale: approximately **120 employees and 30 years of history**.
- Open on a summary home screen with quick actions. Make recording leave the
  primary action for users with editing access. Use compact employee rows.
- Employees, configurable fields, departments, classifications, multiple tags/groups,
  archive/restore, and bulk employee metadata changes.
- Whole-day leave, whole-hour entries, automatic hourly conversion, service
  periods and targets, manual service deductions, and contract-end tracking.
- Monthly and department/team calendars, with form-based editing only.
- Routine Excel/CSV import/export, selectable PDF/Word reports, plain printable
  employee ID cards, sharing/printing, and weekly/manual backup and restore.
- Full PC software is a future project. Browser access during phone hosting is
  included in the Android release.

## Data volume and historical access

- Design and verify for approximately 120 employees with 30 years of employee,
  leave, hourly, conversion, service, and correction history. This is a sizing
  target, not a hard employee limit or automatic history-deletion policy.
- Keep all recorded years accessible through employee/year/date-range filters.
  Archiving employees does not remove their earlier records from historical reports.
- Save the chosen department on each dated record for historical reporting.
  A later employee department change does not relabel those saved records.
- Load the selected month/year and bounded pages of history, rather than loading
  every employee's entire 30-year history whenever a screen opens. Index the
  employee/date/year lookups used by lists, calendars, and reporting.
- Weekly/manual backups and restore include the full retained history. Backup
  copy retention is separate from employee-record retention.
- Year-end expiry applies only to pending hours' eligibility for conversion;
  it never deletes prior hours, conversion events, or historical totals.
- Actual record count depends on entry frequency, which has not been specified.
  Use representative long-history fixtures and measure on target Android devices;
  do not infer an exact storage size or performance result from headcount alone.

## Architecture and ownership

Proposed implementation: Kotlin, Jetpack Compose, and Room over SQLite.
Use one application module initially, with clear packages for data, domain,
UI, hosting, file exchange, and reports. Use versioned upgrades for the Android
app's own database.

Both local screens and the embedded host API use the same business rules and
database. Bundle the Arabic browser interface in the APK. Enforce permissions
on the host, with transactions, record revisions, and repeat-request protection.

Proposed ownership model:

- One phone owns the shared workspace and remains fully usable without a network.
- Guests connect to that phone; shared writes succeed only when it commits them.
- Disconnected guests pause shared operations. Independent guest editing and
  synchronization are outside the initial scope.
- Internet access is unnecessary, but guests need a local connection to the host.
- Changing the owner phone uses this Android app's own verified backup/restore.
- Hosting is explicitly started/stopped, with a visible address and status.
  Validate permissions, screen locking, background execution, battery behavior,
  hotspot routing, and address changes on actual Android devices. Continuous
  background availability must be established by testing.

Proposed permissions:

| Role | Permissions |
| --- | --- |
| Administrator | All records, accounts, field definitions, rules, import, backup/restore, hosting |
| HR staff | Employee/leave maintenance, approved bulk changes, reports and sharing |
| Manager | View employees currently in assigned departments and their records/reports; read-only by default |

Revoke affected sessions when access changes. Account details and permissions
must work locally without a cloud account.

Use one current-department check consistently in employee access, search,
calendars, summaries, reports, file/card output, and the host API. When an
employee changes department, manager access follows the employee's current
department. The old department's manager does not retain special access to
earlier records; a manager authorized for the employee can view their history.
Historical department labels do not create additional access rights. This is
the simplifying default adopted after the user rejected complex transfer rules.

Persist Android sign-in across app closes/restarts until logout. A lost host
connection pauses operations rather than logging the guest out. The host must
validate current account/department permissions on reconnection. Persistent
sign-in does not override account revocation or role/department changes.

## Employee records and custom fields

- Starting fields: name, employee number, national ID, department, job title,
  phone, and employment start date. They remain configurable; required flags
  are chosen through field settings, not imposed by this initial field list.
- Use text for names, employee/national identifiers, job titles, and phone values;
  use the department reference/choice and a date for employment start. Display
  name, employee number, and department in compact rows; open details for the
  complete configured form.
- Give each employee a stable internal ID. Names, employee numbers, and national
  IDs can be duplicated: show warnings, allow saving, and never merge automatically.
- Preserve identifier text and leading zeros. Blank identifiers are not matches.
- Support departments, classifications, and several tags/groups per employee.
  Use stable references so renaming a group does not detach its members.
- Changing department updates the employee's current department, with ordinary
  change history. No assignment timeline or transfer effective-date workflow is
  required. Save the chosen department with dated records; default it to the
  current department and allow an authorized user to choose another department
  when entering a past record. No automatic historical inference is required.
- Custom fields support text, number, date, and dropdown types, ordering, choices,
  and required/optional flags. Validate consistently across local, guest, bulk,
  and spreadsheet workflows.
- Store field definitions and values separately, keyed by stable field IDs.
  Changing a field's type or removing it must not silently erase saved information.
- Bulk employee changes apply to explicitly selected records, with a preview,
  shared validation, and per-employee history. Supported actions include
  department/classification changes and adding/removing tags.
- Archive departed employees with full leave/service history and an option to restore.
- Retained default: archiving an active employee asks for the final working date
  and closes the ongoing service period. Restore retains closed periods; returning
  to work starts a new period with an explicit start date.

## Day-based leave and holidays

- The initial day-based leave type is **إدارية**. Other day-based types can be
  added or edited later in settings; do not prepopulate unselected examples.
  The separate hourly category remains available.
- Record leave and totals taken only. No yearly allowance, remaining leave balance,
  or annual-entitlement processing.
- Suggest the number of days automatically, then let the user adjust it before saving.
- New entries use whole days only. Shorter absences use whole-hour entries.
- Save the suggested and confirmed amounts separately. Reports use the confirmed
  amount and saved reporting-year allocations, without recalculating history.
- Each leave type has explicit excluded weekdays and an option to exclude public
  holidays. Configure actual exclusions during setup. Do not infer rules from names.
- Enter holidays manually as a name and actual date. No automatic annual recurrence
  or formula engine. Count an excluded date once even if several exclusions overlap.
- Changed holiday/settings rules affect future suggestions, not saved confirmed amounts.
- Refresh the suggestion if dates/type change, preserving a manual adjustment for review.
- Split leave spanning two years between those years. When the day total was adjusted,
  ask the user to confirm whole-day allocations whose sum equals the saved total.
- Warn about overlapping leave and possible duplicates, while allowing authorized
  users to proceed. Prevent accidental retry duplicates separately.
- Correct existing leave individually; no bulk leave correction interface.
- Correction/deletion reasons are optional. Always retain who changed what,
  when, and the original/revised values.

## Hourly entries and conversion

- One hourly category with no subtypes, and one counter per employee per calendar year.
- Record employee, date, positive whole-hour duration, and optional notes.
  No minutes, fractional hours, or half-day input.
- Multiple hourly entries on the same date are allowed, with duplicate warnings.
- Initial conversion threshold: **8 hours = 1 full day**, configurable.
- Convert complete threshold-sized groups and retain the remainder:
  10 hours at the 8-hour threshold produces 1 converted day and 2 pending hours.
  Another 6 hours in the same year produces another day.
- Converted days remain a separate hourly equivalent total. They do not create or
  increase administrative, unpaid, or other day-based leave.
- Display original hours, converted day equivalents, and pending hours clearly.
  Do not count the same source hours and their converted days twice.
- Convert automatically after saving hourly entries and after threshold changes,
  using eligible current-year pending hours.
- Completed conversions retain their original threshold and result.
  Pending hours use the current threshold, including hours entered before it changed.
- Save each conversion's rule, source entries, consumed hours, result, and timestamp.
  Consume hours and save conversion events atomically and idempotently.
- Pending hours never contribute to a later year. A new year starts at zero pending
  hours; previous records, conversions, and unconverted historical hours remain visible.
  Enforce this by the recorded date/year without needing a midnight background task.
- Retained correction default: source-hour edits/deletions adjust related conversions
  through traceable correction/reversal events using the original rules. A settings
  change alone never rewrites completed conversions. Corrections must not revive
  previous-year pending hours into the current year's pool.
- Work through correction examples involving multiple thresholds before implementation.

## Service and contracts

- Ongoing service grows each calendar day, including weekends and holidays, until its end.
  Do not count future dates as already completed service.
- Calculate current totals when the reporting date changes without daily manual entry.
- Retained default: overlapping periods produce a warning and each date counts once.
  Gaps between periods do not accrue service. Preserve the period records.
- **Effective service = counted service days minus the manual deduction.**
  Apply the deduction once. Leave records and hourly conversions never change it automatically.
- Retain manual deduction history and the original/revised values.
- Include a service target, remaining duration where applicable, contract-end date,
  and in-app summaries of approaching contract ends.
- Calendar-based years/months are a proposed target-duration default because the user
  was unsure of the convention. Store the entered duration, anchor date, rule, and
  derived target days explicitly. Proposed anchor: earliest service start.
- Proposed month-end rule: use the last valid day of the destination month.
  Define inclusive service dates and anniversary boundaries in worked examples.
- Changing a target's convention/anchor requires explicit review and preserves history.
- Completion dates are estimates, separate from an entered contract-end date.
  Do not project automatic target completion for an employee with no active period.
- Reaching a contract-end date produces a warning only. Service continues under
  the active-period rules until an end date is explicitly recorded. Renewal or
  departure is an explicit reviewed action; expiry never archives automatically.

## Calendars

- Include monthly and department/team leave views.
- Display weeks starting on Sunday. This is a presentation choice, independent
  of each leave type's excluded weekdays and manually entered holiday dates.
- Views, reports, and record details use the same saved data and amounts.
- Select a record to open its ordinary edit form.
- No dragging or resizing leave dates, and no bulk corrections to existing leave.
- Form changes use the same suggestion, confirmation, year allocation, and history rules.

## Spreadsheet exchange

- Support routine Excel/CSV employee import/export.
- Map columns, preview creates/updates, validate, and review possible employee matches.
  Never select a person automatically from a duplicated external identifier.
- **Blank or unmapped update cells leave existing values unchanged.**
- Clearing a value requires an explicit clear action shown in the preview, respecting
  required fields. Numeric zero is a value, not an empty cell.
- New employees still need all required fields; there is no previous value to preserve.
- Preserve identifier text, leading zeros, and supported typed values.
- Preview changes before committing; canceled or failed imports must not leave partial data.

## Reports, ID cards, and backups

- The organization name and logo will be supplied later. Keep them optional
  and editable in settings; report/card generation must work without them.
  Omit an empty branding block rather than inventing an organization identity.
- Offer PDF and editable Word reports with a selected date range and selected sections:
  employee information, service, day-based leave, and hours.
- Use the same report data and saved totals for both formats.
- Group historical records by the department saved with each record. Do not
  retroactively relabel them on an employee department change or split a record
  across department transfers. Access uses the employee's current department;
  reporting labels do not change that simple permission check.
- Include locally packaged Arabic fonts/layout support. Share files and print offline
  when the chosen output/printer is available.
- Printable employee ID cards contain name, employee number, and department.
  **No QR code or QR scanning.**
- Provide automatic weekly backups alongside a manual backup button.
- Create consistent, versioned snapshots on the authoritative phone. Show the last
  successful backup and actual failures; handle overdue work on app resume.
- Android scheduling may affect exact timing; do not promise a precise execution time.
- Restore into a fresh workspace or after an explicitly reviewed replacement operation.
  Validate archive/schema/integrity before applying it.
- Save backups in a phone folder selected by the user. Keep the latest **8
  successful automatic weekly copies**. Prune older automatic copies only after
  a new snapshot succeeds, without touching unrelated files in the folder.
- Retain manual backups and manually exported files until explicitly deleted.
  Automatic-copy retention never removes employee history from a retained snapshot.

## First-use and navigation plan

Suggested phone navigation: الرئيسية، الموظفون، الإجازات، التقويم، المزيد.
Reports, file exchange, and settings are accessible through المزيد.

The confirmed opening destination is the summary home screen. Its primary
action is **تسجيل إجازة**, with secondary employee search and hourly-entry actions
where permitted. Show useful summary information and contract/backup status.
Managers retain their read-only experience and department scope. The employee
list uses compact rows rather than large employee cards.

Follow the device's light/dark setting across screens, including date pickers
and forms. Date fields offer a picker plus direct typing, validate actual dates,
and save the same date regardless of the input method. Both methods must handle
the 30-year historical range without excessive month-by-month navigation.

On first use, choose Create workspace or Connect to a host. Create workspace
sets up the owner account, employee fields, leave types/rules, and optional
spreadsheet import. Connect to a host uses its address and a locally issued account.
After setup/sign-in, later launches resume the signed-in session until logout
or revoked access; guests reconnect and revalidate permissions with the host.

See [Android screen flow](ANDROID_SCREEN_FLOW.md) for the screen hierarchy.

## Implementation milestones

1. **Foundation and feasibility:** an installable Arabic app shell, local workspace,
   persistent storage, and basic owner/guest authentication. Test a minimal host
   over router Wi-Fi and phone hotspot, including screen-lock/background behavior.
2. **Core operations:** employees, fields, tags, bulk employee changes, day/hour
   registration and conversion, corrections/history, service/contracts, calendars,
   manual holidays, and reviewed Excel/CSV exchange.
3. **Complete daily workflow:** selectable PDF/Word reports, plain ID cards,
   sharing/printing, weekly/manual backups, restore, and complete permitted app/browser
   guest workflows.
4. **Stability and delivery:** offline and concurrent operation, interrupted writes,
   retries, archive/restore, yearly boundaries, original-rule corrections, typed fields,
   spreadsheet blank/clear semantics, Arabic output, backup recovery, app upgrades,
   compatibility checks, and a reproducible signed APK.

Size the stability fixtures for 120 employees and 30 years. Check search, selected-
year totals, calendars, long-range PDF/Word output, and complete backup/restore
with historical records, archived employees, and 3–5 connected users. This is a
validation requirement, not a performance result already obtained.

Verify the simple current-department access rule through both client types and
that saved historical department labels survive an employee department change.
Test contract expiry without automatic service
stoppage, persistent sessions plus revocation, and retention of 8 successful
weekly copies without deleting manual exports or pruning on backup failure.

These milestones are plans, not completed implementation or test results.

## Scope boundaries and remaining choices

- Group leave registration remains **maybe**, not committed first-release scope.
- Holiday export to another calendar app remains undecided; it is not a dependency.
- Device/Android compatibility and worked target/correction examples remain
  practical implementation inputs. The backup folder is
  chosen in Android; its location type and retention policy are settled.
- Organization branding will be provided later and is not a prerequisite for
  screen development or plain report/card output.
- Independent offline guest editing/synchronization, cloud services, a dedicated PC
  application, evaluations, leave allowances, employee self-service, approval workflows,
  biometric unlock, bulk leave corrections, calendar dragging, recurring holiday
  engines, QR generation/scanning, and product push notifications are outside this release.
- The host model, remaining role permissions, service-overlap/archive defaults,
  and calendar-based target convention are proposals where not explicitly chosen.
  Manager department scope, persistent sign-in, and warning-only contract expiry
  are confirmed requirements.
- Keep the first release simple: current-department manager access and saved
  report departments replace an elaborate transfer-history permission model.

## Android references

- [Room local storage](https://developer.android.com/training/data-storage/room)
- [Offline-first architecture](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission)
- [Foreground services](https://developer.android.com/develop/background-work/services/fgs)
