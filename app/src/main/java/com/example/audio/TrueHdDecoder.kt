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

object TrueHdDecoder {

    data class DecodedMetadata(
        val mimeType: String,           // "audio/true-hd"
        val channelCount: Int,
        val sampleRate: Int,
        val durationUs: Long,
        val profile: String,            // e.g. "TrueHD 7.1 Atmos" or "TrueHD 5.1"
        val isSimulated: Boolean = false,
        val bitRate: Int = 0,
        val bitDepth: Int = 24,         // TrueHD is 24-bit lossless
        val presentationsCount: Int = 1,
        val jocVersion: String = "TrueHD Lossless (MLP Core)"
    )

    fun isTrueHdFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val lower = fileName.lowercase()
        return ext in setOf("thd", "mlp", "truehd", "mka", "mkv") || lower.contains("truehd") || lower.contains("atmos")
    }

    fun extractMetadata(context: Context, fileUri: Uri): DecodedMetadata {
        val tempFile = SoftwareDecoderHelper.copyUriToTemp(context, fileUri, "truehd_probe.thd")
        return try {
            val probeSession = FFprobeKit.execute("-v quiet -print_format json -show_streams \"${tempFile.absolutePath}\"")
            var channels = 8
            var sampleRate = 48000
            var durationUs = 0L
            var bitRate = 0
            var codecName = ""
            try {
                val output = probeSession.output ?: ""
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
                channels = 8
                sampleRate = 48000
            }

            val isAtmos = codecName.contains("atmos", ignoreCase = true) || 
                          fileUri.lastPathSegment?.contains("atmos", ignoreCase = true) == true
            
            val profile = if (isAtmos) "TrueHD $channels.1 Atmos" else "TrueHD $channels.1"

            DecodedMetadata(
                mimeType = "audio/true-hd",
                channelCount = channels,
                sampleRate = sampleRate,
                durationUs = durationUs,
                profile = profile,
                bitRate = bitRate,
                isSimulated = isSimulated
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
        val tempInput = SoftwareDecoderHelper.copyUriToTemp(context, inputUri, "truehd_input_temp.thd")
        try {
            withContext(Dispatchers.Main) {
                onStatusUpdate("TrueHD lossless · FFmpeg software decoder")
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
                throw IOException("FFmpeg TrueHD decode failed: ${session.failStackTrace}")
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
