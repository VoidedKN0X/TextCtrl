package com.vknxworks.textctrl.ui.setup

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.ui.components.PillButton
import com.vknxworks.textctrl.ui.lock.AppLock

/**
 * First start: make TextCtrl the phone's SMS app (only the SMS app receives texts), then allow
 * contacts, notifications and SIM info. Shown again if another app takes over as SMS app.
 */
@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val c = context.container
    if (!c.isDefaultSmsApp) DefaultAppStep(again = c.setupDone) else PermissionsStep()
}

@Composable
private fun DefaultAppStep(again: Boolean) {
    val context = LocalContext.current
    val c = context.container
    val roles = remember { context.getSystemService(RoleManager::class.java) }
    val available = remember { runCatching { roles?.isRoleAvailable(RoleManager.ROLE_SMS) == true }.getOrDefault(false) }
    var declined by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        c.refreshDefault()
        declined = !c.isDefaultSmsApp
    }
    SetupFrame(
        icon = Icons.Outlined.Sms,
        title = stringResource(if (again) R.string.setup_again_title else R.string.setup_title),
        body = stringResource(if (again) R.string.setup_again_body else R.string.setup_body),
        points = listOf(
            Icons.Outlined.Bolt to stringResource(R.string.setup_point_instant),
            Icons.Outlined.CloudOff to stringResource(R.string.setup_point_offline),
        ),
        error = when {
            !available -> stringResource(R.string.setup_no_sms)
            declined -> stringResource(R.string.setup_declined)
            else -> null
        },
    ) {
        PillButton(
            stringResource(R.string.setup_make_default),
            onClick = {
                val intent = roles?.createRequestRoleIntent(RoleManager.ROLE_SMS) ?: return@PillButton
                AppLock.expectReturn()
                runCatching { request.launch(intent) }
            },
            enabled = available,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    val c = context.container
    val wanted = remember {
        listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
        ).filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        c.contacts.refresh()
        c.finishSetup()
    }
    if (wanted.isEmpty()) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            c.contacts.refresh()
            c.finishSetup()
        }
        return
    }
    SetupFrame(
        icon = Icons.Outlined.Notifications,
        title = stringResource(R.string.setup_permissions_title),
        body = stringResource(R.string.setup_permissions_body),
        points = listOf(
            Icons.Outlined.Contacts to stringResource(R.string.setup_point_contacts),
            Icons.Outlined.Notifications to stringResource(R.string.setup_point_notifications),
            Icons.Outlined.SimCard to stringResource(R.string.setup_point_sim),
        ),
        error = null,
    ) {
        PillButton(
            stringResource(R.string.setup_allow),
            onClick = {
                AppLock.expectReturn()
                request.launch(wanted.toTypedArray())
            },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { c.contacts.refresh(); c.finishSetup() }) { Text(stringResource(R.string.setup_skip)) }
    }
}

@Composable
private fun SetupFrame(
    icon: ImageVector,
    title: String,
    body: String,
    points: List<Pair<ImageVector, String>>,
    error: String?,
    actions: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) }
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            points.forEach { (pointIcon, text) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(pointIcon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(4.dp))
            actions()
        }
    }
}
