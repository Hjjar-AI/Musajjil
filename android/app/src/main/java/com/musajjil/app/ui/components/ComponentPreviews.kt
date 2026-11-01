package com.musajjil.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.musajjil.app.domain.Employee
import com.musajjil.app.ui.theme.LocalAppDimensions
import com.musajjil.app.ui.theme.MusajjilTheme

/** Fictional samples live only in previews; the installed app starts with an empty register. */
@Preview(name = "Arabic controls · light", locale = "ar", showBackground = true, widthDp = 390)
@Composable
private fun LightComponents() = ComponentCatalog(false)

@Preview(name = "Arabic controls · dark", locale = "ar", showBackground = true, widthDp = 390)
@Composable
private fun DarkComponents() = ComponentCatalog(true)

@Composable
private fun ComponentCatalog(dark: Boolean) {
    MusajjilTheme(darkTheme = dark) {
        val space = LocalAppDimensions.current
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(space.page), verticalArrangement = Arrangement.spacedBy(space.large)) {
                ScreenHeading("مُسجِّل", "مكونات مشتركة قابلة للتخصيص")
                SectionCard(title = "الموظفون") {
                    EmployeeRow(Employee("preview", "موظف تجريبي", "0012", department = "الإدارة"), {})
                }
                AppTextField("", {}, "الاسم", hint = "اختياري")
                PrimaryAction("تسجيل إجازة", Icons.AutoMirrored.Outlined.EventNote, {}, Modifier.fillMaxWidth())
                SummaryTile("24", "أيام الإجازة المسجلة", Icons.AutoMirrored.Outlined.EventNote)
            }
        }
    }
}
