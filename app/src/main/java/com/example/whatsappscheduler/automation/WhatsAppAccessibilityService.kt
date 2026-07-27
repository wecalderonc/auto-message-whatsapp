package com.example.whatsappscheduler.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.whatsappscheduler.data.TargetType
import com.example.whatsappscheduler.util.WhatsAppPackages
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

class WhatsAppAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private suspend fun executeSend(request: SendRequest): SendResult {
        return when (request.targetType) {
            TargetType.CONTACT -> sendToContact(request)
            TargetType.GROUP -> sendToGroup(request)
        }
    }

    private suspend fun sendToContact(request: SendRequest): SendResult {
        val encoded = URLEncoder.encode(request.messageText, StandardCharsets.UTF_8.toString())
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=${request.phoneDigits}&text=$encoded")
        val launch = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(request.whatsAppPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            startActivity(launch)
            waitAndClickSend(request.messageText)
        } catch (e: Exception) {
            SendResult(false, "Unable to open WhatsApp: ${e.message}")
        }
    }

    private suspend fun sendToGroup(request: SendRequest): SendResult {
        val group = request.groupName.trim()
        if (group.isEmpty()) {
            return SendResult(false, "Group name is empty")
        }

        val launch = packageManager.getLaunchIntentForPackage(request.whatsAppPackage)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } ?: return SendResult(false, "Unable to launch WhatsApp")

        try {
            startActivity(launch)
        } catch (e: Exception) {
            return SendResult(false, "Unable to open WhatsApp: ${e.message}")
        }

        delay(1_200)

        val opened = openChatBySearch(group)
        if (!opened) {
            return SendResult(false, "Could not find WhatsApp group \"$group\"")
        }

        delay(700)
        return waitAndClickSend(request.messageText, setTextIfNeeded = true)
    }

    private suspend fun openChatBySearch(query: String): Boolean {
        val deadline = System.currentTimeMillis() + SEARCH_TIMEOUT_MS
        var typed = false
        var opened = false

        while (System.currentTimeMillis() < deadline && !opened) {
            val root = rootInActiveWindow
            if (root == null || root.packageName?.toString() !in WhatsAppPackages.ALL) {
                delay(POLL_MS)
                continue
            }

            if (!typed) {
                val searchButton = findByViewIds(
                    root,
                    listOf(
                        "com.whatsapp:id/menuitem_search",
                        "com.whatsapp.w4b:id/menuitem_search",
                        "com.whatsapp:id/search",
                        "com.whatsapp.w4b:id/search"
                    )
                ) ?: findClickableByDesc(root, listOf("Search", "Buscar"))

                if (searchButton != null) {
                    searchButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(500)
                }

                val searchField = findEditable(root)
                    ?: findByViewIds(
                        root,
                        listOf(
                            "com.whatsapp:id/search_src_text",
                            "com.whatsapp.w4b:id/search_src_text",
                            "com.whatsapp:id/search_input",
                            "com.whatsapp.w4b:id/search_input"
                        )
                    )

                if (searchField != null) {
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            query
                        )
                    }
                    searchField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    typed = true
                    delay(900)
                } else {
                    delay(POLL_MS)
                    continue
                }
            }

            val match = findExactTextNode(root, query)
                ?: root.findAccessibilityNodeInfosByText(query)
                    .firstOrNull { node ->
                        val text = node.text?.toString().orEmpty()
                        text.equals(query, ignoreCase = true) ||
                            text.contains(query, ignoreCase = true)
                    }

            if (match != null) {
                val clickable = firstClickableAncestor(match) ?: match
                if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    opened = true
                    break
                }
            }
            delay(POLL_MS)
        }
        return opened
    }

    private suspend fun waitAndClickSend(
        messageText: String,
        setTextIfNeeded: Boolean = false
    ): SendResult {
        val deadline = System.currentTimeMillis() + SEND_TIMEOUT_MS
        var sendClicked = false

        while (System.currentTimeMillis() < deadline) {
            val root = rootInActiveWindow
            if (root == null || root.packageName?.toString() !in WhatsAppPackages.ALL) {
                delay(POLL_MS)
                continue
            }

            val entry = findEditable(root)
            if (entry != null) {
                val current = entry.text?.toString().orEmpty()
                if (setTextIfNeeded || current != messageText) {
                    if (current != messageText) {
                        val args = Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                messageText
                            )
                        }
                        entry.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        delay(400)
                    }
                }
            }

            val sendNode = findSendButton(root)
            if (sendNode != null) {
                val textOk = entry?.text?.toString() == messageText ||
                    root.findAccessibilityNodeInfosByText(messageText).isNotEmpty()
                if (!textOk) {
                    delay(POLL_MS)
                    continue
                }
                val clicked = sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (!clicked) {
                    return SendResult(false, "Send button click was rejected")
                }
                sendClicked = true
                break
            }
            delay(POLL_MS)
        }

        if (!sendClicked) {
            return SendResult(false, "Timed out waiting for WhatsApp send UI")
        }
        delay(800)
        return SendResult(true)
    }

    private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable && node.isEnabled) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return findByViewIds(
            root,
            listOf(
                "com.whatsapp:id/entry",
                "com.whatsapp.w4b:id/entry",
                "com.whatsapp:id/input_edit_text",
                "com.whatsapp.w4b:id/input_edit_text"
            )
        )
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        findByViewIds(
            root,
            listOf(
                "com.whatsapp:id/send",
                "com.whatsapp.w4b:id/send",
                "com.whatsapp:id/send_button",
                "com.whatsapp.w4b:id/send_button",
                "com.whatsapp:id/conversation_entry_action_button",
                "com.whatsapp.w4b:id/conversation_entry_action_button"
            )
        )?.takeIf { it.isClickable && it.isEnabled }?.let { return it }

        listOf("Send", "Enviar", "Send message").forEach { label ->
            root.findAccessibilityNodeInfosByText(label)
                .firstOrNull { node ->
                    node.isClickable &&
                        node.isEnabled &&
                        (node.contentDescription?.contains(label, ignoreCase = true) == true ||
                            node.text?.toString()?.equals(label, ignoreCase = true) == true)
                }
                ?.let { return it }
        }
        return null
    }

    private fun findByViewIds(
        root: AccessibilityNodeInfo,
        ids: List<String>
    ): AccessibilityNodeInfo? {
        ids.forEach { id ->
            root.findAccessibilityNodeInfosByViewId(id).firstOrNull()?.let { return it }
        }
        return null
    }

    private fun findClickableByDesc(
        root: AccessibilityNodeInfo,
        labels: List<String>
    ): AccessibilityNodeInfo? {
        labels.forEach { label ->
            root.findAccessibilityNodeInfosByText(label)
                .firstOrNull {
                    it.isClickable &&
                        (it.contentDescription?.contains(label, ignoreCase = true) == true ||
                            it.text?.toString()?.equals(label, ignoreCase = true) == true)
                }
                ?.let { return it }
        }
        return null
    }

    private fun findExactTextNode(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        return root.findAccessibilityNodeInfosByText(text)
            .firstOrNull { it.text?.toString()?.equals(text, ignoreCase = true) == true }
    }

    private fun firstClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    companion object {
        private const val SEND_TIMEOUT_MS = 25_000L
        private const val SEARCH_TIMEOUT_MS = 20_000L
        private const val POLL_MS = 450L

        @Volatile
        private var instance: WhatsAppAccessibilityService? = null

        private val activeContinuation = AtomicReference<CompletableDeferred<SendResult>?>(null)

        suspend fun requestSend(context: Context, request: SendRequest): SendResult {
            val service = instance
                ?: return SendResult(false, "Accessibility service is not connected")

            val deferred = CompletableDeferred<SendResult>()
            if (!activeContinuation.compareAndSet(null, deferred)) {
                return SendResult(false, "Another send is already in progress")
            }

            return try {
                withTimeoutOrNull(SEND_TIMEOUT_MS + SEARCH_TIMEOUT_MS + 5_000L) {
                    service.executeSend(request)
                } ?: SendResult(false, "Send timed out")
            } catch (e: Exception) {
                SendResult(false, e.message ?: "Unexpected automation error")
            } finally {
                activeContinuation.compareAndSet(deferred, null)
            }
        }
    }
}
