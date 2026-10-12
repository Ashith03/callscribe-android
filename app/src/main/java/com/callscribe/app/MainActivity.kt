package com.callscribe.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.callscribe.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val geminiService = GeminiAiService()
    private val recordingList = mutableListOf<CallRecordingItem>()
    private lateinit var adapter: RecordingsAdapter
    private var mediaPlayer: MediaPlayer? = null
    private var selectedAudioFile: File? = null
    private lateinit var prefs: SharedPreferences

    // Audio file picker contract
    private val audioPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleSelectedAudioUri(it) }
    }

    // Permission request contract
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val phoneStateGranted = permissions[Manifest.permission.READ_PHONE_STATE] ?: false
        if (recordAudioGranted && phoneStateGranted) {
            Toast.makeText(this, "Permissions active for auto call recording", Toast.LENGTH_SHORT).show()
        }
        loadAllCallRecordings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("callscribe_prefs", Context.MODE_PRIVATE)

        setupRecyclerView()
        setupListeners()
        checkAndRequestPermissions()

        // Check if opened from notification with audio file
        intent?.getStringExtra("selected_audio_path")?.let { path ->
            val file = File(path)
            if (file.exists()) {
                selectFileForOrganizing(file)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = RecordingsAdapter(recordingList,
            onPlayClicked = { item -> playRecordingAudio(item.file) },
            onOrganizeClicked = { item -> selectFileForOrganizing(item.file) }
        )
        binding.rvCallRecordings.layoutManager = LinearLayoutManager(this)
        binding.rvCallRecordings.adapter = adapter
    }

    private fun setupListeners() {
        // Select Audio File from phone storage
        binding.btnSelectAudioFile.setOnClickListener {
            audioPickerLauncher.launch("audio/*")
        }

        // Start organizing selected audio
        binding.btnStartOrganize.setOnClickListener {
            selectedAudioFile?.let { file ->
                executeOrganizeCall(file)
            }
        }

        // API Key Settings Modal
        binding.btnApiKeySettings.setOnClickListener {
            showApiKeyDialog()
        }

        // Copy and Share Notes
        binding.btnCopyNotes.setOnClickListener {
            copyNotesToClipboard()
        }

        binding.btnShareNotes.setOnClickListener {
            shareNotesIntent()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionsLauncher.launch(needed.toTypedArray())
        } else {
            loadAllCallRecordings()
        }
    }

    private fun loadAllCallRecordings() {
        recordingList.clear()

        // 1. Check CallScribe directory
        val callScribeDir = File(getExternalFilesDir(Environment.DIRECTORY_RECORDINGS), "CallScribe")
        scanDirectoryForRecordings(callScribeDir)

        // 2. Check Appstar Automatic Call Recorder directory (/sdcard/CallRecordings)
        @Suppress("DEPRECATION")
        val appstarDir = File(Environment.getExternalStorageDirectory(), "CallRecordings")
        scanDirectoryForRecordings(appstarDir)

        adapter.notifyDataSetChanged()
        binding.tvEmptyList.visibility = if (recordingList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun scanDirectoryForRecordings(dir: File) {
        if (!dir.exists() || !dir.isDirectory) return

        dir.listFiles()?.filter {
            it.isFile && (it.extension.equals("m4a", true) ||
                          it.extension.equals("amr", true) ||
                          it.extension.equals("mp3", true) ||
                          it.extension.equals("wav", true))
        }?.sortedByDescending { it.lastModified() }?.forEach { file ->
            val phoneMatch = Regex("(\\+?[0-9]{8,15})").find(file.name)?.value ?: "Direct Call"
            val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
            val sizeStr = "${(file.length() / 1024)} KB"

            recordingList.add(
                CallRecordingItem(
                    file = file,
                    title = file.name,
                    phoneNumber = phoneMatch,
                    formattedDate = dateStr,
                    sizeText = sizeStr,
                    durationText = "${file.extension.uppercase()} Audio"
                )
            )
        }
    }

    private fun handleSelectedAudioUri(uri: Uri) {
        try {
            val contentResolver = applicationContext.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
            val tempFile = File(cacheDir, "Selected_Call_${System.currentTimeMillis()}.m4a")
            val outputStream = FileOutputStream(tempFile)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            selectFileForOrganizing(tempFile)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open audio file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectFileForOrganizing(file: File) {
        selectedAudioFile = file
        binding.cardSelectedAudio.visibility = View.VISIBLE
        binding.tvSelectedFilename.text = file.name
        binding.tvSelectedFileSize.text = "${file.extension.uppercase()} • ${(file.length() / 1024)} KB"
        binding.cardResults.visibility = View.GONE
        Toast.makeText(this, "Ready to organize: ${file.name}", Toast.LENGTH_SHORT).show()
    }

    private fun executeOrganizeCall(file: File) {
        val apiKey = prefs.getString("gemini_api_key", null)
        if (apiKey.isNullOrEmpty()) {
            showApiKeyDialog(onSaved = { executeOrganizeCall(file) })
            return
        }

        binding.layoutLoading.visibility = View.VISIBLE
        binding.cardResults.visibility = View.GONE
        binding.btnStartOrganize.isEnabled = false

        lifecycleScope.launch {
            val result = geminiService.transcribeAndOrganizeAudio(file, apiKey)
            binding.layoutLoading.visibility = View.GONE
            binding.btnStartOrganize.isEnabled = true

            result.onSuccess { data ->
                renderOrganizedResults(data)
            }.onFailure { err ->
                Toast.makeText(this@MainActivity, "Error: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderOrganizedResults(data: StructuredCallIntelligence) {
        binding.cardResults.visibility = View.VISIBLE

        binding.tvResultTitle.text = data.call_metadata?.title ?: "Call Intelligence"
        binding.tvResultSentiment.text = "${data.call_metadata?.sentiment ?: "Positive"} (${data.call_metadata?.sentiment_score ?: 85}%)"
        binding.tvResultTopic.text = "• ${data.call_metadata?.primary_topic ?: "Call Recording"}"

        binding.tvExecutiveSummary.text = data.executive_summary ?: "No summary available."

        val takeawaysText = data.key_takeaways?.joinToString("\n") { "• $it" } ?: "None"
        binding.tvKeyTakeaways.text = takeawaysText

        val actionItemsText = data.action_items?.joinToString("\n") {
            "[ ] ${it.task} (${it.assignee ?: "Unassigned"} - ${it.due_date_or_timeline ?: "ASAP"})"
        } ?: "None"
        binding.tvActionItems.text = actionItemsText

        val dialogueText = data.dialogue?.joinToString("\n\n") {
            "[${it.timestamp ?: "00:00"}] ${it.speaker ?: "Speaker"}: ${it.text ?: ""}"
        } ?: "None"
        binding.tvDialogueTranscript.text = dialogueText

        Toast.makeText(this, "Call organized into text successfully!", Toast.LENGTH_SHORT).show()
    }

    private fun copyNotesToClipboard() {
        val text = buildString {
            append("📞 ${binding.tvResultTitle.text}\n\n")
            append("EXECUTIVE SUMMARY:\n${binding.tvExecutiveSummary.text}\n\n")
            append("KEY TAKEAWAYS:\n${binding.tvKeyTakeaways.text}\n\n")
            append("ACTION ITEMS:\n${binding.tvActionItems.text}\n\n")
            append("FULL TRANSCRIPT:\n${binding.tvDialogueTranscript.text}")
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Call Notes", text))
        Toast.makeText(this, "Notes copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    private fun shareNotesIntent() {
        val text = buildString {
            append("📞 ${binding.tvResultTitle.text}\n\n")
            append("SUMMARY:\n${binding.tvExecutiveSummary.text}\n\n")
            append("ACTION ITEMS:\n${binding.tvActionItems.text}\n\n")
            append("TRANSCRIPT:\n${binding.tvDialogueTranscript.text}")
        }
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Share Call Notes"))
    }

    private fun playRecordingAudio(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
            Toast.makeText(this, "Playing ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not play audio: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showApiKeyDialog(onSaved: (() -> Unit)? = null) {
        val currentKey = prefs.getString("gemini_api_key", "")
        val editText = EditText(this).apply {
            hint = "AIzaSy..."
            setText(currentKey)
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Google Gemini API Key")
            .setMessage("Enter your Gemini API key to enable high-speed speech transcription and text organization:")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newKey = editText.text.toString().trim()
                prefs.edit().putString("gemini_api_key", newKey).apply()
                Toast.makeText(this, "API Key saved", Toast.LENGTH_SHORT).show()
                onSaved?.invoke()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        super.onDestroy()
    }

    // RecyclerView Adapter
    inner class RecordingsAdapter(
        private val items: List<CallRecordingItem>,
        private val onPlayClicked: (CallRecordingItem) -> Unit,
        private val onOrganizeClicked: (CallRecordingItem) -> Unit
    ) : RecyclerView.Adapter<RecordingsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvPhone: TextView = view.findViewById(R.id.tvPhoneNumber)
            val tvDetails: TextView = view.findViewById(R.id.tvCallDetails)
            val btnPlay: ImageButton = view.findViewById(R.id.btnPlayAudio)
            val btnOrganize: View = view.findViewById(R.id.btnOrganizeThis)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_call_recording, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvPhone.text = item.phoneNumber
            holder.tvDetails.text = "${item.formattedDate} • ${item.sizeText}"
            holder.btnPlay.setOnClickListener { onPlayClicked(item) }
            holder.btnOrganize.setOnClickListener { onOrganizeClicked(item) }
        }

        override fun getItemCount() = items.size
    }
}
