package com.example.whatsappscheduler.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.whatsappscheduler.SchedulerApp
import com.example.whatsappscheduler.data.FrequentTarget
import com.example.whatsappscheduler.data.ScheduleStatus
import com.example.whatsappscheduler.data.ScheduledMessage
import com.example.whatsappscheduler.data.TargetType
import com.example.whatsappscheduler.util.ContactDirectory
import com.example.whatsappscheduler.util.CountryOption
import com.example.whatsappscheduler.util.DeviceContact
import com.example.whatsappscheduler.util.PermissionChecks
import com.example.whatsappscheduler.util.PermissionSnapshot
import com.example.whatsappscheduler.util.PhoneNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CreateFormState(
    val targetType: TargetType = TargetType.CONTACT,
    val phone: String = "",
    val contactName: String = "",
    val groupName: String = "",
    val message: String = "",
    val scheduledAtEpochMs: Long = System.currentTimeMillis() + 5 * 60_000L,
    val contactQuery: String = "",
    val contactResults: List<DeviceContact> = emptyList(),
    val error: String? = null,
    val saving: Boolean = false
)

class ScheduleViewModel(
    private val app: SchedulerApp
) : ViewModel() {
    val messages: StateFlow<List<ScheduledMessage>> = app.repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val frequentContacts: StateFlow<List<FrequentTarget>> =
        app.repository.observeFrequentContacts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val frequentGroups: StateFlow<List<FrequentTarget>> =
        app.repository.observeFrequentGroups()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _permissions = MutableStateFlow(PermissionChecks.snapshot(app))
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    private val _selectedCountry = MutableStateFlow(app.countryPreferences.getSelectedCountry())
    val selectedCountry: StateFlow<CountryOption> = _selectedCountry.asStateFlow()

    private val _form = MutableStateFlow(CreateFormState())
    val form: StateFlow<CreateFormState> = _form.asStateFlow()

    private val _riskAccepted = MutableStateFlow(false)
    val riskAccepted: StateFlow<Boolean> = _riskAccepted.asStateFlow()

    fun refreshPermissions() {
        _permissions.value = PermissionChecks.snapshot(app)
        _selectedCountry.value = app.countryPreferences.getSelectedCountry()
    }

    fun setCountry(iso: String) {
        app.countryPreferences.setSelectedIso(iso)
        _selectedCountry.value = app.countryPreferences.getSelectedCountry()
        // Re-run contact search with the new calling code if needed.
        val query = _form.value.contactQuery
        if (query.isNotBlank() || _form.value.phone.isNotBlank()) {
            // Contact digits already stored may be local; leave phone field as typed.
        }
    }

    fun acceptRisk() {
        _riskAccepted.value = true
    }

    fun setTargetType(type: TargetType) {
        _form.value = _form.value.copy(targetType = type, error = null)
    }

    fun updatePhone(value: String) {
        _form.value = _form.value.copy(phone = value, contactName = "", error = null)
    }

    fun updateGroupName(value: String) {
        _form.value = _form.value.copy(groupName = value, error = null)
    }

    fun updateMessage(value: String) {
        _form.value = _form.value.copy(message = value, error = null)
    }

    fun updateScheduledAt(epochMs: Long) {
        _form.value = _form.value.copy(scheduledAtEpochMs = epochMs, error = null)
    }

    fun applyQuickDelay(delayMs: Long) {
        _form.value = _form.value.copy(
            scheduledAtEpochMs = System.currentTimeMillis() + delayMs,
            error = null
        )
    }

    fun selectContact(contact: DeviceContact) {
        _form.value = _form.value.copy(
            targetType = TargetType.CONTACT,
            phone = contact.phoneDigits,
            contactName = contact.displayName,
            contactQuery = "",
            contactResults = emptyList(),
            error = null
        )
    }

    fun selectFrequent(target: FrequentTarget) {
        when (target.targetType) {
            TargetType.CONTACT -> {
                _form.value = _form.value.copy(
                    targetType = TargetType.CONTACT,
                    phone = target.targetKey,
                    contactName = target.displayLabel,
                    error = null
                )
            }
            TargetType.GROUP -> {
                _form.value = _form.value.copy(
                    targetType = TargetType.GROUP,
                    groupName = target.targetKey,
                    error = null
                )
            }
        }
    }

    fun removeFrequent(id: Long) {
        viewModelScope.launch { app.repository.removeFrequent(id) }
    }

    fun searchContacts(context: Context, query: String) {
        _form.value = _form.value.copy(contactQuery = query)
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                try {
                    ContactDirectory.search(context, query)
                } catch (_: SecurityException) {
                    emptyList()
                }
            }
            if (_form.value.contactQuery == query) {
                _form.value = _form.value.copy(contactResults = results)
            }
        }
    }

    fun createSchedule(onDone: () -> Unit) {
        viewModelScope.launch {
            val form = _form.value
            when {
                form.message.isBlank() -> {
                    _form.value = form.copy(error = "Message cannot be empty")
                }
                form.scheduledAtEpochMs <= System.currentTimeMillis() + 10_000L -> {
                    _form.value = form.copy(error = "Pick a time at least 10 seconds from now")
                }
                !app.alarmScheduler.canScheduleExactAlarms() -> {
                    _form.value = form.copy(error = "Exact alarm permission is required")
                }
                form.targetType == TargetType.CONTACT -> {
                    val country = _selectedCountry.value.callingCode
                    val digits = PhoneNormalizer.toWhatsAppDigits(form.phone, country)
                    if (digits == null) {
                        _form.value = form.copy(
                            error = "Pick a contact or enter a valid phone number " +
                                "(country ${_selectedCountry.value.label})"
                        )
                        return@launch
                    }
                    save(
                        targetType = TargetType.CONTACT,
                        phone = digits,
                        groupName = "",
                        label = form.contactName.ifBlank { PhoneNormalizer.formatForDisplay(digits) },
                        message = form.message.trim(),
                        at = form.scheduledAtEpochMs,
                        onDone = onDone
                    )
                }
                form.targetType == TargetType.GROUP -> {
                    val group = form.groupName.trim()
                    if (group.isEmpty()) {
                        _form.value = form.copy(error = "Enter the exact WhatsApp group name")
                        return@launch
                    }
                    save(
                        targetType = TargetType.GROUP,
                        phone = "",
                        groupName = group,
                        label = group,
                        message = form.message.trim(),
                        at = form.scheduledAtEpochMs,
                        onDone = onDone
                    )
                }
            }
        }
    }

    private suspend fun save(
        targetType: TargetType,
        phone: String,
        groupName: String,
        label: String,
        message: String,
        at: Long,
        onDone: () -> Unit
    ) {
        _form.value = _form.value.copy(saving = true, error = null)
        val created = app.repository.create(
            targetType = targetType,
            phoneE164 = phone,
            groupName = groupName,
            displayLabel = label,
            message = message,
            scheduledAtEpochMs = at
        )
        app.alarmScheduler.schedule(created)
        _form.value = CreateFormState()
        onDone()
    }

    fun cancel(id: Long) {
        viewModelScope.launch {
            app.alarmScheduler.cancel(id)
            app.repository.cancel(id)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            app.alarmScheduler.cancel(id)
            app.repository.delete(id)
        }
    }

    fun canEdit(message: ScheduledMessage): Boolean =
        message.status == ScheduleStatus.PENDING

    companion object {
        const val QUICK_1_MIN_MS = 60_000L
        const val QUICK_5_MIN_MS = 5 * 60_000L
        const val QUICK_1_HOUR_MS = 60 * 60_000L
        const val QUICK_12_HOURS_MS = 12 * 60 * 60_000L

        fun factory(app: SchedulerApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ScheduleViewModel(app) as T
                }
            }
    }
}
