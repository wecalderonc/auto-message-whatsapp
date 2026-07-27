package com.example.whatsappscheduler.automation

import com.example.whatsappscheduler.data.TargetType

data class SendRequest(
    val messageId: Long,
    val attemptToken: String,
    val targetType: TargetType,
    val phoneDigits: String,
    val groupName: String,
    val messageText: String,
    val whatsAppPackage: String
)

data class SendResult(
    val success: Boolean,
    val error: String? = null
)
