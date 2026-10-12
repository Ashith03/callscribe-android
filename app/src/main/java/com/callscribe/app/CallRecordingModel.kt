package com.callscribe.app

import java.io.File

data class CallRecordingItem(
    val file: File,
    val title: String,
    val phoneNumber: String,
    val formattedDate: String,
    val sizeText: String,
    val durationText: String
)

data class StructuredCallIntelligence(
    val call_metadata: CallMetadata?,
    val executive_summary: String?,
    val key_takeaways: List<String>?,
    val action_items: List<ActionItem>?,
    val dialogue: List<DialogueTurn>?,
    val tags: List<String>?
)

data class CallMetadata(
    val title: String?,
    val call_type: String?,
    val primary_topic: String?,
    val duration_seconds: Int?,
    val sentiment: String?,
    val sentiment_score: Int?,
    val caller_phone: String?,
    val call_date: String?
)

data class ActionItem(
    val task: String?,
    val assignee: String?,
    val priority: String?,
    val due_date_or_timeline: String?
)

data class DialogueTurn(
    val timestamp: String?,
    val speaker: String?,
    val role: String?,
    val text: String?
)
