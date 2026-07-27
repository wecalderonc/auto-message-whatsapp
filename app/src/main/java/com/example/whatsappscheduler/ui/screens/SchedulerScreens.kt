package com.example.whatsappscheduler.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.whatsappscheduler.data.FrequentTarget
import com.example.whatsappscheduler.data.ScheduleStatus
import com.example.whatsappscheduler.data.ScheduledMessage
import com.example.whatsappscheduler.data.TargetType
import com.example.whatsappscheduler.ui.CreateFormState
import com.example.whatsappscheduler.ui.ScheduleViewModel
import com.example.whatsappscheduler.util.CountryCatalog
import com.example.whatsappscheduler.util.CountryOption
import com.example.whatsappscheduler.util.PermissionSnapshot
import com.example.whatsappscheduler.util.PhoneNormalizer
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulerAppScreen(viewModel: ScheduleViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val riskAccepted by viewModel.riskAccepted.collectAsStateWithLifecycle()
    val frequentContacts by viewModel.frequentContacts.collectAsStateWithLifecycle()
    val frequentGroups by viewModel.frequentGroups.collectAsStateWithLifecycle()
    val selectedCountry by viewModel.selectedCountry.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!riskAccepted) {
        RiskDialog(onAccept = viewModel::acceptRisk)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("WhatsApp Scheduler") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = "Schedule message")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            PermissionsCard(permissions)
            Spacer(Modifier.height(12.dp))
            CountrySelectorCard(
                selected = selectedCountry,
                onSelect = viewModel::setCountry
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Scheduled messages",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            if (messages.isEmpty()) {
                Text(
                    text = "No schedules yet. Tap + to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageCard(
                            message = message,
                            onCancel = { viewModel.cancel(message.id) },
                            onDelete = { viewModel.delete(message.id) }
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateScheduleDialog(
            form = form,
            selectedCountry = selectedCountry,
            frequentContacts = frequentContacts,
            frequentGroups = frequentGroups,
            onTargetType = viewModel::setTargetType,
            onCountry = viewModel::setCountry,
            onPhoneChange = viewModel::updatePhone,
            onGroupChange = viewModel::updateGroupName,
            onMessageChange = viewModel::updateMessage,
            onTimeChange = viewModel::updateScheduledAt,
            onQuickDelay = viewModel::applyQuickDelay,
            onSearchContacts = viewModel::searchContacts,
            onSelectContact = viewModel::selectContact,
            onSelectFrequent = viewModel::selectFrequent,
            onRemoveFrequent = viewModel::removeFrequent,
            onDismiss = { showCreate = false },
            onSave = { viewModel.createSchedule { showCreate = false } }
        )
    }
}

@Composable
private fun RiskDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text("Personal / sideload only") },
        text = {
            Text(
                "This app drives the WhatsApp UI with Accessibility. " +
                    "Unauthorized automated messaging can violate WhatsApp’s terms and risk account action. " +
                    "Group sends search by exact group name and may break when WhatsApp updates. " +
                    "Use only on your own device with recipients you control."
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("I understand") }
        }
    )
}

@Composable
private fun CountrySelectorCard(
    selected: CountryOption,
    onSelect: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Default country code", style = MaterialTheme.typography.titleMedium)
            Text(
                "Used when a number has no +country prefix. Colombia (+57) is the default.",
                style = MaterialTheme.typography.bodySmall
            )
            CountryDropdown(selected = selected, onSelect = onSelect)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryDropdown(
    selected: CountryOption,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Country") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            CountryCatalog.all.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option.iso)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionsCard(permissions: PermissionSnapshot) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Setup required", style = MaterialTheme.typography.titleMedium)
            StatusLine("WhatsApp installed", permissions.whatsAppInstalled)
            StatusLine("Accessibility enabled", permissions.accessibilityEnabled)
            StatusLine("Exact alarms allowed", permissions.exactAlarmsAllowed)
            StatusLine("Notifications allowed", permissions.notificationsAllowed)
            StatusLine(
                "Battery unrestricted (recommended)",
                permissions.ignoringBatteryOptimizations
            )
            Text(
                text = if (permissions.keyguardLocked) {
                    "Device is locked now — unlock before a due send, or unlock within the wait window."
                } else {
                    "Device unlocked — automation can run when due."
                },
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!permissions.accessibilityEnabled) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    ) { Text("Accessibility") }
                }
                if (!permissions.exactAlarmsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    ) { Text("Alarms") }
                }
                if (!permissions.ignoringBatteryOptimizations) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    ) { Text("Battery") }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Text(
        text = "${if (ok) "✓" else "✗"} $label",
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun MessageCard(
    message: ScheduledMessage,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val time = remember(message.scheduledAtEpochMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(message.scheduledAtEpochMs))
    }
    val kind = if (message.targetType == TargetType.GROUP) "Group" else "Contact"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$kind · ${message.targetDescription()}",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
            Text(message.message, style = MaterialTheme.typography.bodyMedium)
            Text("When: $time", style = MaterialTheme.typography.bodySmall)
            Text("Status: ${message.status.name}", style = MaterialTheme.typography.bodySmall)
            message.failureReason?.let {
                Text("Reason: $it", color = MaterialTheme.colorScheme.error)
            }
            if (message.status == ScheduleStatus.PENDING || message.status == ScheduleStatus.WAITING_UNLOCK) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun CreateScheduleDialog(
    form: CreateFormState,
    selectedCountry: CountryOption,
    frequentContacts: List<FrequentTarget>,
    frequentGroups: List<FrequentTarget>,
    onTargetType: (TargetType) -> Unit,
    onCountry: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onGroupChange: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onTimeChange: (Long) -> Unit,
    onQuickDelay: (Long) -> Unit,
    onSearchContacts: (android.content.Context, String) -> Unit,
    onSelectContact: (com.example.whatsappscheduler.util.DeviceContact) -> Unit,
    onSelectFrequent: (FrequentTarget) -> Unit,
    onRemoveFrequent: (Long) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val formatted = remember(form.scheduledAtEpochMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(form.scheduledAtEpochMs))
    }

    var contactsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsGranted = granted
        if (granted) onSearchContacts(context, form.contactQuery)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule message") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = form.targetType == TargetType.CONTACT,
                        onClick = { onTargetType(TargetType.CONTACT) },
                        label = { Text("Contact") }
                    )
                    FilterChip(
                        selected = form.targetType == TargetType.GROUP,
                        onClick = { onTargetType(TargetType.GROUP) },
                        label = { Text("Group") }
                    )
                }

                Text("Send in", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { onQuickDelay(ScheduleViewModel.QUICK_1_MIN_MS) },
                        label = { Text("1 min") }
                    )
                    AssistChip(
                        onClick = { onQuickDelay(ScheduleViewModel.QUICK_5_MIN_MS) },
                        label = { Text("5 min") }
                    )
                    AssistChip(
                        onClick = { onQuickDelay(ScheduleViewModel.QUICK_1_HOUR_MS) },
                        label = { Text("1 hour") }
                    )
                    AssistChip(
                        onClick = { onQuickDelay(ScheduleViewModel.QUICK_12_HOURS_MS) },
                        label = { Text("12 hours") }
                    )
                }

                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = form.scheduledAtEpochMs }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                cal.set(Calendar.YEAR, y)
                                cal.set(Calendar.MONTH, m)
                                cal.set(Calendar.DAY_OF_MONTH, d)
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        cal.set(Calendar.HOUR_OF_DAY, hour)
                                        cal.set(Calendar.MINUTE, minute)
                                        cal.set(Calendar.SECOND, 0)
                                        cal.set(Calendar.MILLISECOND, 0)
                                        onTimeChange(cal.timeInMillis)
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    false
                                ).show()
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Custom time: $formatted")
                }

                if (form.targetType == TargetType.CONTACT) {
                    Text("Country for local numbers", style = MaterialTheme.typography.labelLarge)
                    CountryDropdown(selected = selectedCountry, onSelect = onCountry)
                    Text("Frequent contacts", style = MaterialTheme.typography.labelLarge)
                    FrequentRow(
                        items = frequentContacts,
                        onSelect = onSelectFrequent,
                        onRemove = onRemoveFrequent
                    )
                    if (!contactsGranted) {
                        OutlinedButton(
                            onClick = {
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Allow contacts access") }
                    } else {
                        OutlinedTextField(
                            value = form.contactQuery,
                            onValueChange = { onSearchContacts(context, it) },
                            label = { Text("Search contacts") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        form.contactResults.take(8).forEach { contact ->
                            Text(
                                text = "${contact.displayName} · ${contact.phoneDisplay}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectContact(contact) }
                                    .padding(vertical = 6.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = form.phone,
                        onValueChange = onPhoneChange,
                        label = { Text("Phone (local or +country)") },
                        placeholder = { Text("3105551234 or 573105551234") },
                        supportingText = {
                            val normalized = PhoneNormalizer.toWhatsAppDigits(
                                form.phone,
                                selectedCountry.callingCode
                            )
                            when {
                                form.contactName.isNotBlank() && normalized != null ->
                                    Text("Selected: ${form.contactName} → ${PhoneNormalizer.formatForDisplay(normalized)}")
                                normalized != null ->
                                    Text("WhatsApp will use ${PhoneNormalizer.formatForDisplay(normalized)}")
                                form.phone.isNotBlank() ->
                                    Text("Add country code if needed (selected ${selectedCountry.label})")
                                else -> Text("Local numbers get +${selectedCountry.callingCode} automatically")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("Frequent groups", style = MaterialTheme.typography.labelLarge)
                    FrequentRow(
                        items = frequentGroups,
                        onSelect = onSelectFrequent,
                        onRemove = onRemoveFrequent
                    )
                    OutlinedTextField(
                        value = form.groupName,
                        onValueChange = onGroupChange,
                        label = { Text("Exact WhatsApp group name") },
                        supportingText = {
                            Text("Must match the group title in WhatsApp")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = form.message,
                    onValueChange = onMessageChange,
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                if (form.error != null) {
                    Text(form.error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !form.saving) {
                Text(if (form.saving) "Saving…" else "Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FrequentRow(
    items: List<FrequentTarget>,
    onSelect: (FrequentTarget) -> Unit,
    onRemove: (Long) -> Unit
) {
    if (items.isEmpty()) {
        Text(
            "No frequent targets yet — they appear after you schedule.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
        return
    }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            AssistChip(
                onClick = { onSelect(item) },
                label = { Text("${item.displayLabel} (${item.useCount})") },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier
                            .clickable { onRemove(item.id) }
                            .padding(2.dp)
                    )
                }
            )
        }
    }
}
