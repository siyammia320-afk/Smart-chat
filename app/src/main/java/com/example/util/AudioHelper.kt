package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Lightweight, zero-lag voice message recorder, player,
 * and profile image compressor. Designed specifically for Firebase Free Plan efficiency.
 */
class AudioHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartMs: Long = 0L
    private var mediaPlayer: MediaPlayer? = null

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

    fun releaseAll() {
        stopRecordingCleanup()
        stopPlayback()
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
