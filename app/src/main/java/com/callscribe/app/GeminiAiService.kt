package com.callscribe.app

import android.util.Base64
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class GeminiAiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun transcribeAndOrganizeAudio(
        audioFile: File,
        apiKey: String
    ): Result<StructuredCallIntelligence> = withContext(Dispatchers.IO) {
        try {
            val audioBytes = audioFile.readBytes()
            val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            val mimeType = when (audioFile.extension.lowercase()) {
                "m4a" -> "audio/mp4"
                "wav" -> "audio/wav"
                "amr" -> "audio/amr"
                else -> "audio/mp3"
            }

            val prompt = """
                You are an expert Audio Transcriptionist and Call Intelligence Analyst.
                Transcribe this telephone recording and organize it with precision.
                Identify speakers (e.g. Speaker 1 / Speaker 2 or names).
                Return ONLY a valid JSON object matching this schema:
                {
                  "call_metadata": {
                    "title": "Clear short title",
                    "call_type": "Inbound / Outbound / Support / Sales",
                    "primary_topic": "Core subject",
                    "duration_seconds": 60,
                    "sentiment": "Positive" | "Neutral" | "Negative",
                    "sentiment_score": 88
                  },
                  "executive_summary": "2-4 sentence executive recap",
                  "key_takeaways": ["Point 1", "Point 2"],
                  "action_items": [
                    { "task": "Action task", "assignee": "Speaker 1", "priority": "High", "due_date_or_timeline": "Immediate" }
                  ],
                  "dialogue": [
                    { "timestamp": "00:00", "speaker": "Speaker 1", "role": "Caller", "text": "Exact speech..." }
                  ],
                  "tags": ["tag1", "tag2"]
                }
            """.trimIndent()

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

            val inlineData = JSONObject().apply {
                put("mime_type", mimeType)
                put("data", base64Audio)
            }
            val textPart = JSONObject().apply {
                put("text", prompt)
            }
            val partsArray = JSONArray().apply {
                put(JSONObject().apply { put("inline_data", inlineData) })
                put(textPart)
            }
            val contentObj = JSONObject().apply {
                put("parts", partsArray)
            }
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().put(contentObj))
                put("generationConfig", JSONObject().apply {
                    put("response_mime_type", "application/json")
                    put("temperature", 0.2)
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gemini API error (${response.code}): ${response.body?.string()}"))
            }

            val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response from AI"))
            val responseJson = JSONObject(responseBody)
            val candidateText = responseJson
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val cleanedJson = candidateText.replace(Regex("^```json|```$"), "").trim()
            val parsedResult = gson.fromJson(cleanedJson, StructuredCallIntelligence::class.java)

            Result.success(parsedResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
