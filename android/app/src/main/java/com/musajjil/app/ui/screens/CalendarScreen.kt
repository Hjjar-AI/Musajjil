package com.musajjil.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musajjil.app.R
import com.musajjil.app.domain.*
import com.musajjil.app.ui.components.*
import com.musajjil.app.ui.theme.LocalAppDimensions
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(data: RegisterData, today: LocalDate, onEditLeave: (DayLeave) -> Unit) {
    val space = LocalAppDimensions.current
    val context = LocalContext.current
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var dayText by rememberSaveable { mutableStateOf(today.toString()) }
    var department by rememberSaveable { mutableStateOf("") }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(dayText)
    val names = data.employees.associate { it.id to it.name }
    val departments = data.leaves.map { it.department }.filter(String::isNotBlank).distinct().sorted()
    val leaves = data.leaves.filter { department.isBlank() || it.department == department }
    val records = leaves.filter { !selected.isBefore(it.start) && !selected.isAfter(it.end) }
    val weekdays = stringArrayResource(R.array.weekdays_sunday_first)
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value % 7
    fun selectMonth(value: YearMonth) { monthText = value.toString(); dayText = value.atDay(1).toString() }
    LazyColumn(contentPadding = PaddingValues(space.page), verticalArrangement = Arrangement.spacedBy(space.large)) {
        item { ScreenHeading(stringResource(R.string.calendar), action = {
            TextButton(onClick = { monthText = YearMonth.from(today).toString(); dayText = today.toString() }) { Text(stringResource(R.string.today)) }
        }) }
        item { AppChoiceField(listOf(ChoiceOption("", stringResource(R.string.all_departments))) + departments.map { ChoiceOption(it, it) },
            department, { department = it }, stringResource(R.string.department)) }
        item { SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectMonth(month.minusMonths(1)) }) { Icon(Icons.Outlined.ChevronRight, stringResource(R.string.previous_month)) }
                Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM uuuu", Locale("ar"))),
                    Modifier.weight(1f).clickable { showDatePicker(context, selected) { monthText = YearMonth.from(it).toString(); dayText = it.toString() } },
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { selectMonth(month.plusMonths(1)) }) { Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.next_month)) }
            }
            Row(Modifier.fillMaxWidth()) { weekdays.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            val cells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
            (0 until cells step 7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space.tiny)) {
                    (week until week + 7).forEach { index ->
                        val number = index - offset + 1
                        if (number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp))
                        else {
                            val date = month.atDay(number)
                            val active = date == selected
                            val hasLeave = leaves.any { !date.isBefore(it.start) && !date.isAfter(it.end) }
                            Surface(onClick = { dayText = date.toString() }, modifier = Modifier.weight(1f).height(48.dp)
                                .semantics { contentDescription = DateText.format(date) },
                                shape = MaterialTheme.shapes.small,
                                color = if (active) MaterialTheme.colorScheme.primary else if (date == today)
                                    MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(number.toString(), style = MaterialTheme.typography.bodyMedium)
                                    if (hasLeave) Surface(Modifier.size(4.dp), shape = CircleShape,
                                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary) {}
                                    else Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }
            Text(stringResource(R.string.calendar_legend), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { SectionCard(title = DateText.format(selected)) {
            if (records.isEmpty()) EmptyState(stringResource(R.string.no_leave_on_day), stringResource(R.string.no_leave_on_day_body), Icons.Outlined.EventAvailable)
            records.forEach { leave -> DayLeaveRow(leave, names[leave.employeeId].orEmpty()) { onEditLeave(leave) } }
        } }
    }
}
