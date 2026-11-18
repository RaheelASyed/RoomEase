package edu.virginia.cs4720.roomease.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/**
 * Current grant state of [permission], re-checked every time the screen resumes
 * (the user may have changed it in system Settings while we were in the background).
 */
@Composable
fun rememberPermissionGranted(permission: String): Pair<Boolean, (Boolean) -> Unit> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(context.hasPermission(permission)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = context.hasPermission(permission)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted to { granted = it }
}

/**
 * Lecture 10: ask in context, explain why in one sentence, and keep working when the answer is no.
 */
@Composable
fun PermissionExplainer(
    title: String,
    reason: String,
    denied: Boolean,
    onRequest: () -> Unit,
    fallbackLabel: String,
    onFallback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    SectionCard(modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                if (denied) "$reason Permission is currently off. You can allow it, or continue without it." else reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PrimaryButton(if (denied) "Try again" else "Allow", onClick = onRequest)
            if (denied) {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Open app settings") }
            }
            OutlinedButton(onClick = onFallback, modifier = Modifier.heightIn(min = 48.dp)) { Text(fallbackLabel) }
        }
    }
}
