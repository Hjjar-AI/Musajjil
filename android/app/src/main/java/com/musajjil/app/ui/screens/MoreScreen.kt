package com.musajjil.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.musajjil.app.R
import com.musajjil.app.domain.*
import com.musajjil.app.ui.components.*
import com.musajjil.app.ui.theme.LocalAppDimensions
import java.time.DayOfWeek

@Composable
fun MoreScreen(data: RegisterData, onThreshold: () -> Unit, onLeavePolicy: () -> Unit, onHoliday: () -> Unit) {
    val space = LocalAppDimensions.current
    val weekdays = stringArrayResource(R.array.weekdays_sunday_first)
    val excluded = listOf(DayOfWeek.SUNDAY) + DayOfWeek.values().filter { it != DayOfWeek.SUNDAY }
    LazyColumn(contentPadding = PaddingValues(space.page), verticalArrangement = Arrangement.spacedBy(space.large)) {
        item { ScreenHeading(stringResource(R.string.settings_title), stringResource(R.string.settings_subtitle)) }
        item {
            SectionCard(title = stringResource(R.string.hourly_settings), action = {
                TextButton(onThreshold) { Text(stringResource(R.string.edit)) }
            }) {
                Text(stringResource(R.string.conversion_rule, data.threshold), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.conversion_explanation), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            SectionCard(title = stringResource(R.string.leave_settings), action = {
                TextButton(onLeavePolicy) { Text(stringResource(R.string.edit)) }
            }) {
                DetailValue(stringResource(R.string.excluded_weekdays), excluded.withIndex()
                    .filter { it.value in data.leavePolicy.excludedWeekdays }.joinToString("، ") { weekdays[it.index] }
                    .ifBlank { stringResource(R.string.none) })
                DetailValue(stringResource(R.string.exclude_holidays), stringResource(
                    if (data.leavePolicy.excludeHolidays) R.string.enabled else R.string.disabled))
            }
        }
        item {
            SectionCard(title = stringResource(R.string.manual_holidays), action = {
                IconButton(onHoliday) { Icon(Icons.Outlined.Add, stringResource(R.string.add_holiday)) }
            }) {
                if (data.holidays.isEmpty()) Text(stringResource(R.string.no_holidays), style = MaterialTheme.typography.bodyMedium)
                data.holidays.sortedBy { it.date }.forEach { holiday -> DetailValue(holiday.name, DateText.format(holiday.date)) }
            }
        }
        item {
            SectionCard {
                DetailValue(stringResource(R.string.appearance), stringResource(R.string.system_appearance))
                DetailValue(stringResource(R.string.week_start), stringResource(R.string.sunday))
                DetailValue(stringResource(R.string.record_location), stringResource(R.string.on_this_phone))
            }
        }
    }
}
