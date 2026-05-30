package com.example.audio

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.IOException

object DtsDecoder {

    data class DecodedMetadata(
        val mimeType: String,
        val channelCount: Int,
        val sampleRate: Int,
        val durationUs: Long,
        val profile: String,
        val isSimulated: Boolean = false,
        val bitRate: Int = 0,
        val bitDepth: Int = 24,
        val presentationsCount: Int = 1,
        val jocVersion: String = "DTS Coherent Acoustics"
    )

    fun isDtsFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val lower = fileName.lowercase()
        return ext in setOf("dts", "dtshd", "dtsma", "mka", "mkv") || 
               lower.contains("dts-hd") || lower.contains("dts_hd") || 
               lower.contains("dtsx") || lower.contains("dts:x")
    }

    fun detectDtsProfile(ffprobeOutput: String, channelCount: Int): String {
        return when {
            ffprobeOutput.contains("dts:x", ignoreCase = true) || ffprobeOutput.contains("dtsx", ignoreCase = true) -> 
                "DTS:X ${channelCount}ch"
            ffprobeOutput.contains("dtshd_ma", ignoreCase = true) || ffprobeOutput.contains("DTS-HD MA", ignoreCase = true) -> 
                "DTS-HD MA ${channelCount}ch"
            else -> "DTS Core ${channelCount}ch"
        }
    }

    fun extractMetadata(context: Context, fileUri: Uri): DecodedMetadata {
        val tempFile = SoftwareDecoderHelper.copyUriToTemp(context, fileUri, "dts_probe.dts")
        return try {
            val probeSession = FFprobeKit.execute("-v quiet -print_format json -show_streams \"${tempFile.absolutePath}\"")
            var channels = 6
            var sampleRate = 48000
            var durationUs = 0L
            var bitRate = 0
            var codecName = ""
            var allOutput = ""
            try {
                val output = probeSession.output ?: ""
                allOutput = output
                val chMatch = Regex("\"channels\"\\s*:\\s*(\\d+)").find(output)
                val srMatch = Regex("\"sample_rate\"\\s*:\\s*\"(\\d+)\"").find(output)
                val durMatch = Regex("\"duration\"\\s*:\\s*\"([0-9.]+)\"").find(output)
                val brMatch = Regex("\"bit_rate\"\\s*:\\s*\"(\\d+)\"").find(output)
                val cnMatch = Regex("\"codec_name\"\\s*:\\s*\"([^\"]+)\"").find(output)

                channels = chMatch?.groupValues?.get(1)?.toIntOrNull() ?: channels
                sampleRate = srMatch?.groupValues?.get(1)?.toIntOrNull() ?: sampleRate
                durationUs = ((durMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0) * 1_000_000).toLong()
                bitRate = brMatch?.groupValues?.get(1)?.toIntOrNull() ?: bitRate
                codecName = cnMatch?.groupValues?.get(1) ?: codecName
            } catch (e: Exception) {
            }

            var isSimulated = false
            if (channels == 0 || sampleRate == 0) {
                isSimulated = true
                channels = 6
                sampleRate = 48000
            }

            // Fallback for some ffmpeg info
            val profileOutput = allOutput + " " + (fileUri.lastPathSegment ?: "")
            val profile = detectDtsProfile(profileOutput, channels)
            
            val joc = if (profile.contains("DTS:X")) "DTS:X Object Audio (core rendered to ${channels}ch bed)" else "DTS Coherent Acoustics"

            DecodedMetadata(
                mimeType = "audio/vnd.dts",
                channelCount = channels,
                sampleRate = sampleRate,
                durationUs = durationUs,
                profile = profile,
                bitRate = bitRate,
                isSimulated = isSimulated,
                jocVersion = joc
            )
        } finally {
            tempFile.delete()
        }
    }

    suspend fun decode(
        context: Context,
        inputUri: Uri,
        outputPcmFile: File,
        targetBitsPerSample: Int,
        onProgress: suspend (Float) -> Unit,
        onStatusUpdate: suspend (String) -> Unit
    ): DecodedMetadata = withContext(Dispatchers.IO) {
        val tempInput = SoftwareDecoderHelper.copyUriToTemp(context, inputUri, "dts_input_temp.dts")
        try {
            withContext(Dispatchers.Main) {
                onStatusUpdate("DTS · FFmpeg software decoder (dca)")
            }
            
            val metadata = extractMetadata(context, inputUri)
            val durationMs = metadata.durationUs / 1000.0

            val pcmCodec = when (targetBitsPerSample) {
                16 -> "pcm_s16le"
                24 -> "pcm_s24le"
                32 -> "pcm_s32le"
                else -> "pcm_s24le"
            }
            val sampleRate = metadata.sampleRate

            val cmd = "-y -i \"${tempInput.absolutePath}\" -vn -c:a $pcmCodec -ar $sampleRate \"${outputPcmFile.absolutePath}\""

            val session = FFmpegKit.executeAsync(
                cmd,
                { /* complete */ },
                { /* log */ },
                { stats ->
                    if (durationMs > 0) {
                        val pct = (stats.time / durationMs).toFloat().coerceIn(0f, 1f)
                    }
                }
            )
            
            // To be able to yield while decoding
            while (!session.state.name.equals("COMPLETED") && !session.state.name.equals("FAILED")) {
                yield()
                Thread.sleep(100)
                if (durationMs > 0) {
                    val statss = session.allStatistics
                    if (statss.isNotEmpty()) {
                        val pct = (statss.last().time / durationMs).toFloat().coerceIn(0f, 1f)
                        withContext(Dispatchers.Main) {
                            onProgress(pct)
                        }
                    }
                }
            }

            if (!ReturnCode.isSuccess(session.returnCode)) {
                throw IOException("FFmpeg DTS decode failed: ${session.failStackTrace}")
            }

            if (outputPcmFile.exists()) {
                WavHelper.updateWavHeaderSizes(outputPcmFile, outputPcmFile.length() - 44)
            }
            
            withContext(Dispatchers.Main) {
                onProgress(1f)
            }
            
            metadata

        } finally {
            tempInput.delete()
        }
    }
}
