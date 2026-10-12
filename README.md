# 📱 CallScribe AI — Native Android App (Auto Call Recording & Organized Text)

A native Android application written in **Kotlin** that combines **automatic phone call recording** with **AI speech-to-text intelligence** to turn calls and audio recordings into organized notes, summaries, and action items.

---

## 🌟 Features

1. **🎙️ Automatic Call Recording**:
   - Background [`CallReceiver`](file:///c:/apk/android_app/app/src/main/java/com/callscribe/app/CallReceiver.kt) detects incoming & outgoing calls.
   - Foreground [`CallRecordService`](file:///c:/apk/android_app/app/src/main/java/com/callscribe/app/CallRecordService.kt) records the audio using `MediaRecorder`.
   - Saves recordings to storage tagged with phone number, timestamp, and duration.
   - Notifies you immediately when a call finishes: *"Call Recorded: Tap to Get Organized Text"*.

2. **📁 Select Any Audio File**:
   - Built-in file picker allows selecting any existing audio recording on your phone (`.m4a`, `.amr`, `.mp3`, `.wav`), including calls recorded by **Automatic Call Recorder (`com.appstar.callrecorder`)**.

3. **🧠 AI Speech Diarization & Text Organization**:
   - Powered by **Google Gemini 2.0 Flash Multimodal Audio API**.
   - Extracts:
     - **Speaker 1 (Caller)** vs. **Speaker 2 (Receiver)** dialogue with timestamps.
     - **Executive Summary** (concise 2–3 sentence overview).
     - **Key Decisions & Takeaways**.
     - **Action Items Checklist** with assignees and due timelines.
     - **Sentiment Analysis & Topic Classification**.

4. **📤 1-Tap Share & Copy**:
   - Copy notes directly to clipboard.
   - Share formatted intelligence report directly to WhatsApp, Gmail, Slack, or Notes.

---

## 🏗️ Architecture

- [`app/src/main/AndroidManifest.xml`](file:///c:/apk/android_app/app/src/main/AndroidManifest.xml): Configures audio recording, telephony state, and foreground service permissions.
- [`MainActivity.kt`](file:///c:/apk/android_app/app/src/main/java/com/callscribe/app/MainActivity.kt): Primary UI handling recorded calls list, audio selection, audio playback, and organized results display.
- [`CallReceiver.kt`](file:///c:/apk/android_app/app/src/main/java/com/callscribe/app/CallReceiver.kt): BroadcastReceiver listening for `PHONE_STATE` changes.
- [`CallRecordService.kt`](file:///c:/apk/android_app/app/src/main/java/com/callscribe/app/CallRecordService.kt): Foreground Service managing `MediaRecorder` lifecycle.
- [`GeminiAiService.kt`](file:///c:/apk/android_app/app/src/main/java/com/callscribe/app/GeminiAiService.kt): Handles asynchronous audio encoding and Gemini multimodal API requests.

---

## 🚀 How to Build the `.apk` File

### Method 1: Build in Android Studio (Recommended)
1. Open **Android Studio**.
2. Select **Open an Existing Project** and choose the folder:
   ```
   c:\apk\android_app
   ```
3. Wait for Gradle sync to complete.
4. Click **Build** ➡️ **Build Bundle(s) / APK(s)** ➡️ **Build APK(s)**.
5. Android Studio will generate the `.apk` file at:
   ```
   c:\apk\android_app\app\build\outputs\apk\debug\app-debug.apk
   ```
6. Transfer this file to your phone and tap to install!

### Method 2: Free Cloud Build with GitHub Actions (No Android Studio Needed!)
1. Push this `android_app` folder to a GitHub repository.
2. The included workflow [`.github/workflows/build-apk.yml`](file:///c:/apk/android_app/.github/workflows/build-apk.yml) will trigger automatically.
3. Once the build finishes (~90 seconds), download the compiled **`CallScribe-AI-v1.0.apk`** directly from the **Actions** tab on GitHub!

### Method 3: Command Line (If Android SDK & JDK 17 are installed)
Run the following in the `android_app` directory:
```bash
./gradlew assembleDebug
```
The APK will be generated in `app/build/outputs/apk/debug/app-debug.apk`.
