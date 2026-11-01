package com.musajjil.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.musajjil.app.R
import com.musajjil.app.ui.theme.LocalAppDimensions

/** Uses the same untinted transparent bitmap as the launcher. */
@Composable
fun AppBrand(modifier: Modifier = Modifier) {
    val space = LocalAppDimensions.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(space.small), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.mipmap.ic_launcher), contentDescription = null, modifier = Modifier.size(space.brandIcon))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
    }
}
