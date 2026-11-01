package com.musajjil.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.musajjil.app.R
import com.musajjil.app.backup.BackupStatus
import com.musajjil.app.domain.*
import com.musajjil.app.ui.components.*
import com.musajjil.app.ui.forms.timestampText
import com.musajjil.app.ui.theme.LocalAppDimensions

@Composable
fun MoreScreen(data: RegisterData, backup: BackupStatus, onThreshold: () -> Unit, onTypes: () -> Unit,
    onHoliday: () -> Unit, onEditHoliday: (Holiday) -> Unit, onGroups: () -> Unit, onFields: () -> Unit,
    onBulk: () -> Unit, onService: () -> Unit, onHistory: () -> Unit, onBackups: () -> Unit) {
    val space = LocalAppDimensions.current
    LazyColumn(contentPadding = PaddingValues(space.page), verticalArrangement = Arrangement.spacedBy(space.large)) {
        item { ScreenHeading(stringResource(R.string.settings_title), stringResource(R.string.settings_subtitle)) }
        item { SectionCard(title = stringResource(R.string.backups)) {
            NavigationRow(stringResource(R.string.backups), onBackups)
            DetailValue(stringResource(R.string.backup_last_auto), if (backup.lastAutomatic == 0L) stringResource(R.string.backup_never) else timestampText(backup.lastAutomatic))
            if (backup.error.isNotBlank()) Text(backup.error, color = MaterialTheme.colorScheme.error)
        } }
        item { SectionCard(title = stringResource(R.string.customization)) {
            NavigationRow(stringResource(R.string.employee_fields), onFields)
            NavigationRow(stringResource(R.string.groups), onGroups)
            NavigationRow(stringResource(R.string.bulk_employees), onBulk)
        } }
        item { SectionCard {
            NavigationRow(stringResource(R.string.service), onService)
            NavigationRow(stringResource(R.string.leave_types), onTypes)
            NavigationRow(stringResource(R.string.history), onHistory)
        } }
        item {
            SectionCard(title = stringResource(R.string.hourly_settings), action = { TextButton(onThreshold) { Text(stringResource(R.string.edit)) } }) {
                Text(stringResource(R.string.conversion_rule, data.threshold), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.conversion_explanation), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            SectionCard(title = stringResource(R.string.manual_holidays), action = {
                IconButton(onHoliday) { Icon(Icons.Outlined.Add, stringResource(R.string.add_holiday)) }
            }) {
                if (data.holidays.isEmpty()) Text(stringResource(R.string.no_holidays), style = MaterialTheme.typography.bodyMedium)
                data.holidays.sortedBy { it.date }.forEach { holiday ->
                    NavigationRow(holiday.name + " · " + DateText.format(holiday.date), { onEditHoliday(holiday) })
                }
            }
        }
        item { SectionCard {
            DetailValue(stringResource(R.string.appearance), stringResource(R.string.system_appearance))
            DetailValue(stringResource(R.string.week_start), stringResource(R.string.sunday))
            DetailValue(stringResource(R.string.record_location), stringResource(R.string.on_this_phone))
        } }
    }
}
