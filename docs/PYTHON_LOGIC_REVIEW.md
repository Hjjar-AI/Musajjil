# Python logic review for مُسجِّل

Status: historical reference only. The user has directed the project to focus
on Android independently. This review and its migration suggestions are no
longer inputs or prerequisites for the active Android implementation plan.

Review date: 2026-11-01. Companion specification: [Android plan](ANDROID_PLAN.md).

## Scope and meaning of the recommendations

Inventoried all **58 Python files**: 54 files under `py/`, three application
launchers, and one standalone source-extraction utility. Static parsing found
**109 route declarations**. All 53 non-initializer package modules are reachable
from the `py.app` import graph, including imports inside functions. A feature
being outside the Android scope does not make its Python file safe to remove
from the running Flask application without changing its dependencies.

This is a workflow, dependency, and migration review. All files were inspected
structurally; production workflows were traced through their source. The
standalone extraction utility was classified from its structure and description,
rather than treated as employee-management functionality. Configuration was
inventoried without printing configuration secret values.

The Flask app was not started or imported: startup initializes/migrates the
database and starts maintenance work. Isolated calculation probes used selected
function definitions and an in-memory SQLite database. No existing Python source
or employee data was changed. Recommendations to omit a feature concern the
new Android product, not deletion of legacy files or historical records.

## Product decisions already established

| Area | Android behavior |
| --- | --- |
| Employee records | Typed custom fields with required flags, search, name/number/national-ID duplicate warnings without blocking or automatic merging |
| Employee lifecycle | Archive and restore; retain history; final-date entry and a new service period on return are retained planning defaults |
| Day-based leave | Suggest whole days, allow whole-day adjustment, save the confirmed amount; shorter absences use whole hours |
| Reporting years | Allocate spanning leave between years; confirm allocations after a manual day adjustment |
| Allowances | No yearly allowance, remaining leave balance, or annual-entitlement processing |
| Hours | Whole-hour entries only; one category without subtypes, one counter per employee per calendar year |
| Conversion | Configurable threshold, initially 8 hours per whole day; retain the remainder separately |
| Conversion destination | Converted day equivalents remain in the hourly category; no transfer to administrative or unpaid leave |
| Conversion timing | Automatic after hourly entry or threshold change, using current-year pending hours |
| Rule changes | Completed conversions retain the original rules; pending hours use the new threshold |
| Year end | Pending hours cannot contribute to the next year; historical records remain visible |
| Corrections | Traceable corrections using the original rules; reason text is optional, automatic history remains |
| Service | Daily calendar accrual for ongoing periods; manual deductions only; overlap counted once is the retained default |
| Contract and target | Include contract-end tracking and service targets; calendar-based target arithmetic remains a proposal |
| Operation | Offline Android owner app plus optional phone-hosted browser/app clients over Wi-Fi or hotspot |
| Files and recovery | Existing-data migration, Arabic PDF/Word reports with selected ranges/sections, sharing/printing, weekly automatic plus manual backup and restore |
| Spreadsheet updates | Blank/unmapped cells leave existing values unchanged; clearing needs an explicit previewed action |
| Confirmed additional workflows | Multiple tags/groups, bulk employee changes, routine Excel/CSV exchange, editable Word reports, printable employee ID cards without QR |
| Group leave registration | User answered maybe; not committed first-release scope |
| Calendars | Monthly plus department/team calendars; change records through forms only |
| Holiday management | Manual entry of named dates; no recurring formula engine or automatic annual generation |
| Excluded editing features | Bulk corrections to existing leave and calendar drag editing; individual corrections stay included |

## Keep, simplify, replace, or omit

### Keep as core workflows, rebuild for Android

- Employees, custom fields, archive/restore, Arabic search, employee identifiers,
  departments and classifications. Preserve imported metadata and historical
  relationships even if an optional management screen is deferred.
- Day-based leave and the single hourly register using the agreed rules above.
- Service periods, targets, manual service deductions, and contract-end summaries.
- Explicit account permissions for the owner and connected users. Rebuild around
  actions on records, with the same checks for the native and browser clients.
- Reports from confirmed saved amounts, with per-year allocations and separate
  hourly equivalents. PDF and editable Word output are both required. Arabic PDF
  generation is new implementation work; align Word output with the new model.
  Let the user select report dates and sections in both formats.
- Multiple tags/groups per employee and bulk department/classification/tag
  changes, using explicit selection, a preview, and per-employee history.
- Printable employee ID cards containing name, employee number, and department.
  QR generation is no longer required, superseding the earlier card decision.
- A single import workflow with mapping, preview, validation, and explicit match
  review; a complete, versioned backup/restore workflow with automatic weekly
  snapshots plus a manual button.
- Manually entered public-holiday dates, plus configurable weekday/public-holiday
  exclusions for each day-based leave type. Review legacy annual definitions when
  importing actual dated instances; do not silently enable an automatic engine.
- Monthly and department/team leave calendars, opening the ordinary edit form
  for an individual record. No drag-to-move or bulk leave correction interface.
- A small home screen with useful counts, contract summaries, current-year
  pending hours, and backup status, using consistent active/archive filters.

### Simplify or consolidate

- Replace several employee import routes with one reviewed pipeline. Existing
  import code can overwrite a matching employee automatically, which conflicts
  with allowing duplicate external identifiers.
- Separate employment classifications from arbitrary text fields so bulk edits,
  filters, and employee details cannot disagree about the same classification.
- Give departments stable IDs and explicit rename/archive behavior; the current
  department list and employee department text can become inconsistent.
- Replace the old setup wizard with first-run workspace creation/import and
  owner setup. It currently asks about allowances/evaluations outside this scope.
- Replace broad web-route permission prefixes, platform paths, background
  threads, and browser-session wiring with the Android architecture.
- Keep manually entered holiday dates and explicit leave-type settings as data,
  not rules inferred from Arabic names or the overloaded `is_yearly` flag.
- Use one source of truth for reports, summaries, calendar displays, and exports.

### Omit from the initial Android product under the current scope

- Annual leave entitlements, carryover, zero-balance compatibility stubs, and
  the obsolete "year not processed" prompt. Calendar-year reporting and expiry
  of pending hours still remain necessary.
- Performance evaluations, criterion scoring/import, and radar charts. This is
  already deferred; preserve historical evaluation data in the original backup.
- Rankings of employees by absence and heuristic pattern flags, department
  standard deviations, and the fixed-260-working-days absence-rate assumption.
- Random demo employees mixed into a real workspace and the production demo
  cleanup endpoints. A first-run setup screen is a different feature and stays.
- PythonAnywhere/WSGI launchers, Flask shutdown endpoint, and the standalone
  `Ultimate Parse.py` development utility from Android packaging.
- Old text-file setup imports and destructive legacy "cleanup" migrations.
- Bulk corrections to existing leave, calendar drag editing, and the advanced
  holiday rule engine. Keep bulk employee changes and single-record leave forms.
- QR generation and scanning in the Android product. Keep plain printable cards.
- Advanced-evaluation/training/OKR/scenario entries that appear in stale schema,
  validation, or permission references without implemented modules in this tree.
  Do not count menu/template names as completed backend capabilities.

### Additional workflows: current decisions

| Feature | What the Python app currently contains | Current first-release decision |
| --- | --- | --- |
| Bulk employee updates | Change department/classification and add/remove tags across selected employees | Confirmed: keep with preview and change history |
| Group leave registration | Register the same day-based leave for a selected tag/classification group | Maybe: remain undecided; do not include automatically because bulk employee edits are confirmed |
| Bulk editing existing leave | Change dates/type/notes for several existing records | Excluded: retain individual leave corrections only |
| Tags/groups | Several tags per employee, membership management, group filtering | Confirmed: keep |
| Calendar | Monthly employee/department views, team view, print, date editing | Confirmed: monthly plus department/team views; form-based edits only, no dragging |
| Advanced holiday rules | Repeating weekday/day-of-month/formula rules and stored department/classification targets | Excluded: manually enter named holiday dates instead of recurring rules |
| Routine spreadsheet exchange | Excel/CSV employee imports and exports beyond the initial database migration | Confirmed: keep; consolidate into one reviewed mapping/preview/import flow |
| Word reports | Actual DOCX report generation exists | Confirmed: keep editable Word output alongside PDF |
| Employee ID cards | Printable employee card with a QR containing text details | Confirmed: keep printable cards, remove QR generation from Android |
| Holiday calendar export | ICS holiday download | Undecided; suggested defer unless needed |

No optional feature above has been removed from the legacy application.

## Logic conflicts and implementation defects

These are reasons to rebuild or reconcile logic, not reasons to discard useful
employee records. "Probed" means an isolated reproduction; "source" means the
behavior was established by reading code and references, without a live app run.

| Finding | Evidence | Android consequence |
| --- | --- | --- |
| Leave totals have competing definitions | Source: `vacation_balance.py:5` recalculates dates, `employees_detail.py:35` sums saved values, reports use both, bulk edits recalculate again | Use the confirmed saved amount and persisted year allocations everywhere |
| Short Friday-spanning leave can fail to exclude Friday | Probed: `vacation_holidays.py:25`, November 6, 2026 alone returns 1 despite hardcoded Friday exclusion | Replace the arithmetic with tested date-based rules |
| Two holiday records on one date can subtract twice | Probed: same-date holiday rows make a one-day leave return -1 | Treat excluded dates as a set, including overlap with weekends |
| ISO-shaped invalid dates pass validation helper | Probed: `utils.py:7` accepts `2026-02-30` without checking the date | Parse a real date before saving or calculating |
| Hour conversion conflicts with every major agreed conversion rule | Source: `vacation_conversion.py:6` uses fractional days, per-type rates, no year filter, and inserts into administrative/unpaid/unexcused leave | Replace with the single yearly counter, whole-day conversion events, and a separate hourly total |
| Repeated hourly entries are currently blocked | Source: `employees_leave.py:141` checks same employee/date/type | Allow intentional multiple entries; prevent retries using operation identity instead |
| Service overlap is counted twice | Probed: January 1–6 plus January 5–10 yields 12 instead of 10 days in `employee_helpers.py:43` | Union service dates, retain period records, and subtract manual deduction once |
| Ongoing service is frozen on entry | Source: `employees_entitlements.py:55` substitutes today's date for a missing end | Represent ongoing periods explicitly and calculate through the reporting date |
| Target years/months are inconsistent | Source: employee add uses averages, edit/helper can use calendar dates, presentation uses other divisors | Persist the selected convention and anchor; preserve imported numeric targets |
| Date changes can bypass day-count review | Source: `calendar_core.py:115` updates dates without touching amount or yearly allocation; `employees_bulk.py:105` replaces saved amounts | Route edits through the same review and correction workflow |
| Individual and bulk leave apply different restrictions | Source: `employees_leave.py` warns on a holiday; `routes_bulk_leave.py:55` blocks it and also imposes whole-day maternity/sick leave restrictions | Apply the newly agreed whole-day/whole-hour entry rules consistently, preserving imported historical fractions |
| Archived records leak into some active workflows | Source: bulk leave selects `employees WHERE 1=1`; dashboards, several exports/calendars do not filter `deleted_at` | Explicit active/archive scope; never silently include archived employees in a new group operation |
| Department rename and classification updates can diverge | Source: `routes_departments.py:31` changes the dictionary only; bulk classification edit changes the ID while helper criteria lookup reads dynamic text | Keep one stable relation and a consistent displayed label |
| Import can overwrite an ambiguous duplicate | Source: `import_employees.py:14` and `routes_employee_import.py:225` use the first matching key | Review matches; never choose a person from a duplicate external identifier automatically |
| One Excel import references a missing module | Static scan: `routes_excel.py:61` imports absent `py.routes_export_import` | Consolidate the import entry points |
| New-tag import can fail | Source plus capability probe: import paths access `db.lastrowid`, but `db` is a SQLite connection | Use a coherent transactional importer and the insert result |
| Import data is held in the Flask session | Source: employee and evaluation wizards store a full dataframe in `session['import_df']`; no alternate session backend is configured in the supplied Python code | Use bounded local staging for import previews, not a full workbook in browser session state |
| "Full" JSON export omits important tables | Source: `export_reports.py:30` omits hourly logs, holidays, departments, users, year settings and holiday rules; corresponding import does not restore field definitions | It is not a complete recoverable backup; prioritize the actual database snapshot and reviewed migration |
| PDF download is disabled | Source: `reports_individual.py:85` redirects with an unavailable message; DOCX export is implemented | Implement Arabic PDFs rather than assuming the route provides them |
| Smart-holiday rules are disconnected from leave counting | Source: only preview calls `get_dates_for_rule`; the leave calculator reads `public_holidays` | A new advanced engine requires explicit integration and employee targeting |
| Smart-holiday preview has runtime and date errors | Probed: `sqlite3.Row.get` raises; after supplying a dict, the last-Monday formula omits February 2026 | Replace the engine if the feature is retained, rather than porting it unchanged |
| Holiday calendars depend on today's year | Source: `get_all_holiday_dates` expands annual dates for the current year, while calendars accept any selected year | Generate holiday instances for the requested reporting period |
| JSON API is not an independent Android backend | Source: `app.py:272` applies browser-session login checks to API endpoints; CSRF is global; API handlers alone do not enforce the complete proposed role matrix | Build and verify shared host API authentication/authorization explicitly |
| Startup migration can discard legacy tables | Source: `database.py:224` drops locked-period and advanced-evaluation tables during migration | Inspect and copy the source database without invoking legacy migrations |
| Live backup/restore is a plain file copy | Source: `maintenance_backup.py:27,85`, without a consistent-snapshot API or coordinated restore | Use a consistent snapshot and validated recovery flow |
| Demo cleanup promise is unsupported by supplied code | Source: `wizard_mode.py` says 24-hour cleanup, but only manual cleanup routes are implemented | Omit real-workspace demo mutation; do not carry the timer promise into Android |

## Isolated verification performed

All 58 files parsed with Python 3.14.4. All literal `render_template` targets
found in Python exist in the supplied template directory. One missing relative
module import was found, in the old Excel import route. Parsing and template
existence do not establish that the full application runs successfully.

| Probe | Observed result | Expected for the tested policy |
| --- | --- | --- |
| Parse February 30, 2026 | Accepted unchanged | Reject invalid date |
| Count November 6, 2026 only, with Friday exclusion | 1 day | 0 days |
| One weekday with two same-date holiday rows | -1 days | 0 days |
| Service January 1–6 and January 5–10 | 12 days | 10 distinct days |
| Smart-holiday preview with a real SQLite row | AttributeError for `.get` | Generate dates |
| Last-Monday formula, February 2026, with dict input | No February date | February 23 |
| `lastrowid` on SQLite connection | Attribute absent | Obtain ID from the INSERT cursor |

These probes used standard-library dependencies and an in-memory database, with
only the selected function ASTs executed. No Flask import, application startup,
database migration, source-data conversion, or production repair was performed.

## Decisions to make next

1. Group leave registration remains a maybe, and holiday ICS export remains
   undecided. Tags, employee bulk changes, routine spreadsheet exchange, Word,
   ID cards, monthly/team calendars, and manual holiday dates are confirmed.
2. For bulk operations, agree preview/selection and correction behavior once,
   using the same shared rules as single-record actions.
3. Verify the confirmed spreadsheet rule in examples: blank/unmapped update
   cells preserve existing values, explicit clear actions are previewed, zero
   is a supplied value, and required fields remain validated. Whole-day and
   whole-hour new-entry policies are settled; preserve legacy fractions during
   migration instead of rounding them.
4. Confirm the service calculation examples and original-rule correction
   examples before implementation. Calendar target arithmetic is still a proposal.
5. Check the actual Android device and host lifecycle behavior during the
   feasibility milestone. Source review cannot establish background-host reliability.

The module index below accounts for every Python file. "Keep" describes a
workflow or data requirement; the Android implementation still replaces Flask
routes and Python-specific infrastructure.

## Complete module index

| Module | Proposed treatment | Logic accounted for |
| --- | --- | --- |
| [Ultimate Parse.py](<../Ultimate Parse.py>) | Exclude from Android | Developer file extraction/reconstruction utility; no employee-management workflow. |
| [flask_app_home.py](<../flask_app_home.py>) | Exclude from Android | PythonAnywhere entry point and deployment-specific paths. |
| [hjjarmhmmdali_pythonanywhere_com_wsgi.py](<../hjjarmhmmdali_pythonanywhere_com_wsgi.py>) | Exclude from Android | WSGI deployment shim. |
| [local_run.py](<../local_run.py>) | Replace infrastructure | Desktop Flask launcher and initialization; native app and host lifecycle replace it. |
| [py/__init__.py](<../py/__init__.py>) | Replace infrastructure | Python package marker. |
| [py/app.py](<../py/app.py>) | Replace infrastructure | Blueprint wiring, browser guards, startup database work, logging, shutdown, backup thread. |
| [py/calendar_core.py](<../py/calendar_core.py>) | Keep views / replace editing | Confirmed monthly calendar; edits open the shared form instead of the old direct date update or dragging. |
| [py/calendar_team.py](<../py/calendar_team.py>) | Keep / rebuild | Confirmed department/team calendars; replace the implementation and its missing-import error branch. |
| [py/config.py](<../py/config.py>) | Simplify / replace storage | INI defaults, paths, security and runtime options; omit allowance and conversion-order leftovers. |
| [py/dashboard_core.py](<../py/dashboard_core.py>) | Simplify | Keep useful counts/contract/backup information; omit rankings and unsupported statistical assumptions. |
| [py/database.py](<../py/database.py>) | Replace / preserve source | Legacy schema and destructive cleanup migrations; source for a reviewed migration, not startup code to run. |
| [py/database_import.py](<../py/database_import.py>) | Omit old workflow | Text-file classification entitlement and evaluation setup; outside agreed initial scope. |
| [py/database_seed.py](<../py/database_seed.py>) | Simplify | Review field/classification/leave defaults; do not force evaluation data or old hourly subtypes. |
| [py/employee_helpers.py](<../py/employee_helpers.py>) | Keep intent / replace logic | Field lookup, name/search fields, service counting and target arithmetic; resolve conflicting sources. |
| [py/employees_bulk.py](<../py/employees_bulk.py>) | Split | Keep confirmed employee bulk changes; omit bulk editing existing leave. Use shared validation and individual leave corrections. |
| [py/employees_core.py](<../py/employees_core.py>) | Replace infrastructure | Shared Flask employee blueprint and helper imports. |
| [py/employees_detail.py](<../py/employees_detail.py>) | Keep / rebuild | Employee detail, history, service/contract summaries, archive listing and restore. |
| [py/employees_entitlements.py](<../py/employees_entitlements.py>) | Split | Keep service periods, target, manual service deduction; omit leave-entitlement screen and writes. |
| [py/employees_leave.py](<../py/employees_leave.py>) | Keep / replace rules | Single leave registration/edit/delete and hourly entry; replace subtype routing, duplicate blocking and old conversion. |
| [py/employees_list.py](<../py/employees_list.py>) | Keep / simplify | Arabic search, sorting, pagination and department/classification/tag/age/hire-date filters. |
| [py/employees_manage.py](<../py/employees_manage.py>) | Keep / rebuild | Employee create/edit/archive; preserve fields while replacing inconsistent target conversions. |
| [py/evaluation_import_helpers.py](<../py/evaluation_import_helpers.py>) | Defer | Evaluation file reading, employee matching, criteria lookup and import result handling. |
| [py/evaluations_core.py](<../py/evaluations_core.py>) | Defer | Performance evaluation entry, criteria choice, score history and deletion. |
| [py/evaluations_import.py](<../py/evaluations_import.py>) | Defer | Excel/CSV evaluation import and mapping. |
| [py/export_employees.py](<../py/export_employees.py>) | Keep / consolidate | Routine spreadsheet exchange is confirmed; retain CSV, consolidate export data, and make archive scope explicit. JSON output remains optional. |
| [py/export_reports.py](<../py/export_reports.py>) | Split / replace | Monthly leave exports are useful; the partial full-JSON dump is not a complete backup. |
| [py/import_employees.py](<../py/import_employees.py>) | Consolidate / replace matching | CSV/JSON import with automatic key-based overwrite and problematic new-tag insertion. |
| [py/import_full.py](<../py/import_full.py>) | Replace | Destructive partial JSON restore; use complete validated recovery or new-workspace migration. |
| [py/maintenance_backup.py](<../py/maintenance_backup.py>) | Keep intent / rebuild | Weekly automatic plus manual backup/restore; replace live file copying and the desktop thread, and verify actual snapshot success. |
| [py/notification_helpers.py](<../py/notification_helpers.py>) | Simplify | Keep relevant in-app contract/pending-hour status; remove obsolete unprocessed-year prompt. |
| [py/radar_chart.py](<../py/radar_chart.py>) | Defer | SVG visualization of evaluation scores. |
| [py/reports_individual.py](<../py/reports_individual.py>) | Keep reports / rebuild output | PDF/Word reports with selected dates/sections; implement PDF, share saved totals, and remove obsolete allowance columns. |
| [py/role_permissions.py](<../py/role_permissions.py>) | Rebuild | Route-prefix allow/deny lists; replace stale roles/endpoints with shared action permissions. |
| [py/routes_api.py](<../py/routes_api.py>) | Rebuild | JWT login/refresh and limited employee/leave routes; not a complete independently usable mobile backend. |
| [py/routes_auth.py](<../py/routes_auth.py>) | Keep intent / rebuild | Local user login/logout/password change, adapted for owner and guest sessions. |
| [py/routes_bulk_leave.py](<../py/routes_bulk_leave.py>) | Maybe / replace rules if kept | Group leave registration remains undecided; existing restrictions and active-employee selection need replacement if included. |
| [py/routes_classifications.py](<../py/routes_classifications.py>) | Keep / simplify | Manage employment classifications; retain stable references and historical records. |
| [py/routes_departments.py](<../py/routes_departments.py>) | Keep / rebuild relation | Manage department names; current rename does not update employee department text. |
| [py/routes_employee_import.py](<../py/routes_employee_import.py>) | Consolidate | Excel upload, mapping, preview/dry run and import; keep the guided workflow, replace matching/staging. |
| [py/routes_evaluation_criteria.py](<../py/routes_evaluation_criteria.py>) | Defer | Classification/global scoring criteria and per-score descriptions. |
| [py/routes_excel.py](<../py/routes_excel.py>) | Keep / consolidate | Routine Excel import/export is confirmed; rebuild the import path that references a missing module. |
| [py/routes_holidays.py](<../py/routes_holidays.py>) | Keep manual dates / optional ICS | Confirmed manual named-date entry; legacy annual recurrence requires migration review. External-calendar export is undecided. |
| [py/routes_id_card.py](<../py/routes_id_card.py>) | Keep cards / omit QR | Confirmed printable employee ID card with name, employee number, and department. Omit the legacy QR-generation logic from Android. |
| [py/routes_manual.py](<../py/routes_manual.py>) | Simplify | Static help page; replace with help for the agreed Android workflows. |
| [py/routes_settings.py](<../py/routes_settings.py>) | Omit old workflow | Endpoint for the legacy text-file settings importer. |
| [py/routes_settings_page.py](<../py/routes_settings_page.py>) | Replace screen | Renders a settings template; no settings persistence logic is implemented in this file. |
| [py/routes_setup.py](<../py/routes_setup.py>) | Replace | Classification/entitlement/evaluation setup and default-admin logic; use first-run owner/workspace setup. |
| [py/routes_smart_holidays.py](<../py/routes_smart_holidays.py>) | Omit from Android | Advanced holiday rules are excluded; use manual named dates. Preserve legacy source data without running the broken preview engine. |
| [py/routes_tags.py](<../py/routes_tags.py>) | Keep / rebuild | Confirmed multiple groups per employee, membership management, and group filters; preserve existing membership data. |
| [py/routes_user_manager.py](<../py/routes_user_manager.py>) | Keep intent / rebuild | Admin-managed user creation, role/password changes and removal. |
| [py/routes_vacation_types.py](<../py/routes_vacation_types.py>) | Keep / rebuild settings | Day-based leave type names; replace overloaded yearly/unit flags with explicit type policies. |
| [py/utils.py](<../py/utils.py>) | Keep intent / replace helpers | Date parsing, department lookup, and old leave conversion destination order; omit destination ordering. |
| [py/vacation_analytics.py](<../py/vacation_analytics.py>) | Defer / omit suggested | Department deviation and leave-timing heuristic flags; not core registration logic. |
| [py/vacation_balance.py](<../py/vacation_balance.py>) | Replace totals / omit stubs | Recalculated taken days with zero allowance/balance and no-op entitlement helpers. |
| [py/vacation_conversion.py](<../py/vacation_conversion.py>) | Replace entirely | Old cross-type fractional conversion; incompatible with the agreed single yearly hourly category. |
| [py/vacation_holidays.py](<../py/vacation_holidays.py>) | Keep intent / replace calculator | Holiday dates and day counting; hardcoded type names, Friday arithmetic and duplicate-date issues. |
| [py/validation.py](<../py/validation.py>) | Rebuild shared validation | Typed/required employee fields, whole-day leave, and whole-hour entries; no hourly subtypes. Preserve legacy precision and make correction reasons optional. |
| [py/wizard_mode.py](<../py/wizard_mode.py>) | Omit production feature | Random demo employees/history and manual cleanup; separate from first-run setup. |
