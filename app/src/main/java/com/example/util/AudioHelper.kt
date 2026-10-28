package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Lightweight, zero-lag audio recorder, player, continuous live call streamer,
 * and profile image compressor. Designed specifically for Firebase Free Plan efficiency.
 */
class AudioHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartMs: Long = 0L
    private var mediaPlayer: MediaPlayer? = null

    // Dedicated live-call recorder & sequential playback queue
    private var callSliceRecorder: MediaRecorder? = null
    private var callSliceFile: File? = null
    private var callMediaPlayer: MediaPlayer? = null
    private val callScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val incomingCallAudioChannel = Channel<String>(capacity = 12)
    private val playedChunkIds = LinkedHashSet<String>()

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    init {
        callScope.launch {
            for (audioBase64 in incomingCallAudioChannel) {
                playSingleCallChunkSuspend(audioBase64)
            }
        }
    }

    fun startRecording(): Boolean {
        stopRecordingCleanup()
        return try {
            val outFile = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.3gp")
            recordingFile = outFile
            val recorder = createMediaRecorder()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            recorder.setAudioSamplingRate(8000)
            recorder.setAudioEncodingBitRate(12200)
            recorder.setOutputFile(outFile.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            recordingStartMs = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.e("AudioHelper", "Failed to start voice recording", e)
            stopRecordingCleanup()
            false
        }
    }

    /**
     * Records a single live call voice slice (~1.5s) on IO thread.
     * Returns Base64 audio if speech/sound was captured, or null if completely silent/failed.
     */
    suspend fun recordLiveCallSlice(durationMs: Long = 1500L, requireVoiceActivity: Boolean = true): String? =
        withContext(Dispatchers.IO) {
            stopCallSliceCleanup()
            val outFile = File(context.cacheDir, "call_slice_${System.currentTimeMillis()}.3gp")
            callSliceFile = outFile
            try {
                val recorder = createMediaRecorder()
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                recorder.setAudioSamplingRate(8000)
                recorder.setAudioEncodingBitRate(12200)
                recorder.setOutputFile(outFile.absolutePath)
                recorder.prepare()
                recorder.start()
                callSliceRecorder = recorder

                // Prime maxAmplitude (first call always returns 0)
                try {
                    recorder.maxAmplitude
                } catch (_: Exception) {
                }

                var peakAmplitude = 0
                val steps = 5
                val stepDelay = (durationMs / steps).coerceAtLeast(200L)
                repeat(steps) {
                    delay(stepDelay)
                    val amp = try {
                        recorder.maxAmplitude
                    } catch (_: Exception) {
                        0
                    }
                    if (amp > peakAmplitude) {
                        peakAmplitude = amp
                    }
                }

                try {
                    recorder.stop()
                } catch (_: Exception) {
                }
                try {
                    recorder.release()
                } catch (_: Exception) {
                }
                callSliceRecorder = null

                // Very low threshold (220) so normal/soft speaking is always picked up
                val hasSpeech = !requireVoiceActivity || peakAmplitude >= 220
                if (hasSpeech && outFile.exists() && outFile.length() > 60L) {
                    val bytes = outFile.readBytes()
                    outFile.delete()
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                } else {
                    outFile.delete()
                    null
                }
            } catch (e: Exception) {
                Log.w("AudioHelper", "Live call slice record error", e)
                stopCallSliceCleanup()
                null
            }
        }

    private fun stopCallSliceCleanup() {
        try {
            callSliceRecorder?.stop()
        } catch (_: Exception) {
        }
        try {
            callSliceRecorder?.release()
        } catch (_: Exception) {
        }
        callSliceRecorder = null
        try {
            callSliceFile?.delete()
        } catch (_: Exception) {
        }
        callSliceFile = null
    }

    /**
     * Parses a packed call chunk ("chunkId|speakerId|base64" or raw base64) and queues it
     * for immediate playback if it came from another participant and hasn't been played yet.
     */
    fun handleIncomingCallPacket(packet: String, currentUserId: String, fallbackSpeakerId: String = "") {
        if (packet.isBlank()) return
        val parts = packet.split("|", limit = 3)
        val chunkId: String
        val speakerId: String
        val audioBase64: String

        if (parts.size == 3) {
            chunkId = parts[0]
            speakerId = parts[1]
            audioBase64 = parts[2]
        } else {
            speakerId = fallbackSpeakerId
            audioBase64 = packet
            chunkId = "${speakerId}_${packet.hashCode()}"
        }

        if (speakerId.isBlank() || speakerId == currentUserId || audioBase64.isBlank()) {
            return
        }

        synchronized(playedChunkIds) {
            if (playedChunkIds.contains(chunkId)) return
            playedChunkIds.add(chunkId)
            if (playedChunkIds.size > 100) {
                val first = playedChunkIds.iterator().next()
                playedChunkIds.remove(first)
            }
        }

        incomingCallAudioChannel.trySend(audioBase64)
    }

    private suspend fun playSingleCallChunkSuspend(audioBase64: String) {
        val tempFile = withContext(Dispatchers.IO) {
            try {
                val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
                if (bytes.isEmpty()) return@withContext null
                val file = File(context.cacheDir, "call_rx_${System.nanoTime()}.3gp")
                FileOutputStream(file).use { it.write(bytes) }
                file
            } catch (e: Exception) {
                Log.w("AudioHelper", "Failed to decode call audio chunk", e)
                null
            }
        } ?: return

        suspendCancellableCoroutine { cont ->
            try {
                stopCallPlaybackOnly()
                val player = MediaPlayer()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                player.setDataSource(tempFile.absolutePath)
                player.setVolume(1.0f, 1.0f)
                player.setOnCompletionListener {
                    stopCallPlaybackOnly()
                    tempFile.delete()
                    if (cont.isActive) cont.resume(Unit)
                }
                player.setOnErrorListener { _, _, _ ->
                    stopCallPlaybackOnly()
                    tempFile.delete()
                    if (cont.isActive) cont.resume(Unit)
                    true
                }
                player.prepare()
                player.start()
                callMediaPlayer = player
            } catch (e: Exception) {
                Log.w("AudioHelper", "Failed to play incoming call chunk", e)
                stopCallPlaybackOnly()
                tempFile.delete()
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private fun stopCallPlaybackOnly() {
        try {
            if (callMediaPlayer?.isPlaying == true) {
                callMediaPlayer?.stop()
            }
        } catch (_: Exception) {
        }
        try {
            callMediaPlayer?.release()
        } catch (_: Exception) {
        }
        callMediaPlayer = null
    }

    /**
     * Stops recording and returns Pair(base64EncodedAudio, durationSeconds) or null on failure.
     */
    fun stopRecordingAndEncode(): Pair<String, Int>? {
        val recorder = mediaRecorder ?: return null
        val file = recordingFile
        val elapsedSec = ((System.currentTimeMillis() - recordingStartMs) / 1000L).toInt().coerceAtLeast(1)
        return try {
            recorder.stop()
            recorder.release()
            mediaRecorder = null
            if (file != null && file.exists() && file.length() > 0L) {
                val bytes = file.readBytes()
                file.delete()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                Pair(base64, elapsedSec.coerceAtMost(120))
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("AudioHelper", "Failed to stop voice recording", e)
            stopRecordingCleanup()
            null
        }
    }

    fun cancelRecording() {
        stopRecordingCleanup()
    }

    private fun stopRecordingCleanup() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
        try {
            recordingFile?.delete()
        } catch (_: Exception) {
        }
        recordingFile = null
    }

    fun playBase64Audio(
        audioBase64: String,
        onCompletion: () -> Unit,
        onError: (String) -> Unit
    ) {
        stopPlayback()
        if (audioBase64.isBlank()) {
            onError("ভয়েস অডিও পাওয়া যায়নি")
            return
        }
        try {
            val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
            val tempFile = File(context.cacheDir, "play_voice_${System.currentTimeMillis()}.3gp")
            FileOutputStream(tempFile).use { it.write(bytes) }

            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(tempFile.absolutePath)
            player.setVolume(1.0f, 1.0f)
            player.setOnCompletionListener {
                stopPlayback()
                tempFile.delete()
                onCompletion()
            }
            player.setOnErrorListener { _, _, _ ->
                stopPlayback()
                tempFile.delete()
                onError("ভয়েস প্লে করা যায়নি")
                true
            }
            player.prepare()
            player.start()
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e("AudioHelper", "Failed to play Base64 audio", e)
            onError("ভয়েস প্লে করতে সমস্যা হয়েছে")
        }
    }

    fun stopPlayback() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    fun configureCallAudio(speakerOn: Boolean, micMuted: Boolean) {
        try {
            // Keep MODE_NORMAL when speakerphone is ON so MediaPlayer USAGE_MEDIA plays loud and clear
            audioManager?.mode = if (speakerOn) AudioManager.MODE_NORMAL else AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = speakerOn
            audioManager?.isMicrophoneMute = micMuted
        } catch (e: Exception) {
            Log.w("AudioHelper", "Failed to configure call audio mode", e)
        }
    }

    fun resetCallAudio() {
        stopCallSliceCleanup()
        stopCallPlaybackOnly()
        try {
            audioManager?.isMicrophoneMute = false
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = false
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.w("AudioHelper", "Failed to reset call audio mode", e)
        }
    }

    fun releaseAll() {
        stopRecordingCleanup()
        stopPlayback()
        resetCallAudio()
        callScope.cancel()
    }

    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    companion object {
        /**
         * Compresses a user-selected gallery photo into a tiny 88x88 JPEG Base64 data string (< 8KB)
         * so profile pictures load instantaneously without Firebase Storage costs or phone lag.
         */
        fun compressGalleryImageToBase64(context: Context, uri: Uri): String? {
            return try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val original = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (original == null) return null

                val scaled = Bitmap.createScaledBitmap(original, 88, 88, true)
                val output = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 65, output)
                val bytes = output.toByteArray()
                "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            } catch (e: Exception) {
                Log.e("AudioHelper", "Failed to compress profile photo", e)
                null
            }
        }

        fun decodeBase64Bitmap(dataUri: String): Bitmap? {
            if (!dataUri.startsWith("data:image")) return null
            return try {
                val base64Part = dataUri.substringAfter("base64,", "")
                if (base64Part.isBlank()) return null
                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        }
    }
}
