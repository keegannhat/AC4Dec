package com.example.audio

import android.util.Log
import java.io.File

object FfmpegExportHelper {
    private const val TAG = "FfmpegExportHelper"

    val isAvailable: Boolean by lazy {
        try {
            Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
            true
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "FFmpegKit library is not available in classpath. Falling back to pure Kotlin helpers.")
            false
        }
    }

    fun encodeWavToFlac(inputFile: File, outputFile: File): Boolean {
        if (!isAvailable) return false
        return try {
            val ffmpegKitClass = Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
            val returnCodeClass = Class.forName("com.arthenica.ffmpegkit.ReturnCode")
            val executeMethod = ffmpegKitClass.getMethod("execute", String::class.java)
            
            // Build command
            val cmd = "-y -i \"${inputFile.absolutePath}\" -c:a flac \"${outputFile.absolutePath}\""
            val session = executeMethod.invoke(null, cmd)
            
            val getReturnCodeMethod = session.javaClass.getMethod("getReturnCode")
            val returnCode = getReturnCodeMethod.invoke(session)
            
            val isSuccessMethod = returnCodeClass.getMethod("isSuccess", returnCodeClass)
            isSuccessMethod.invoke(null, returnCode) as Boolean
        } catch (e: Exception) {
            Log.e(TAG, "FFmpeg encoding to FLAC failed", e)
            false
        }
    }

    fun downmixToStereo(inputFile: File, outputFile: File): Boolean {
        if (!isAvailable) return false
        return try {
            val ffmpegKitClass = Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
            val returnCodeClass = Class.forName("com.arthenica.ffmpegkit.ReturnCode")
            val executeMethod = ffmpegKitClass.getMethod("execute", String::class.java)
            
            // Build command
            val cmd = "-y -i \"${inputFile.absolutePath}\" -ac 2 \"${outputFile.absolutePath}\""
            val session = executeMethod.invoke(null, cmd)
            
            val getReturnCodeMethod = session.javaClass.getMethod("getReturnCode")
            val returnCode = getReturnCodeMethod.invoke(session)
            
            val isSuccessMethod = returnCodeClass.getMethod("isSuccess", returnCodeClass)
            isSuccessMethod.invoke(null, returnCode) as Boolean
        } catch (e: Exception) {
            Log.e(TAG, "FFmpeg downmixing failed", e)
            false
        }
    }

    fun splitMultichannel(inputFile: File, outputDir: File, baseName: String, channelCount: Int): List<File>? {
        if (!isAvailable) return null
        val channelNames = when (channelCount) {
            1 -> listOf("Mono")
            2 -> listOf("Left", "Right")
            6 -> listOf("Left(L)", "Right(R)", "Center(C)", "LFE", "SurroundLeft(Ls)", "SurroundRight(Rs)")
            8 -> listOf("Left", "Right", "Center", "LFE", "SurroundLeft", "SurroundRight", "BackLeft", "BackRight")
            else -> (1..channelCount).map { "Channel_$it" }
        }

        val outputFiles = channelNames.map { name ->
            File(outputDir, "${baseName}_$name.wav")
        }

        try {
            val ffmpegKitClass = Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
            val returnCodeClass = Class.forName("com.arthenica.ffmpegkit.ReturnCode")
            val executeMethod = ffmpegKitClass.getMethod("execute", String::class.java)

            // Extract each channel of the input file into a separate output mono file using standard pan filter
            for (i in 0 until channelCount) {
                val outFile = outputFiles[i]
                val cmd = "-y -i \"${inputFile.absolutePath}\" -filter_complex \"[0:a]pan=mono|c0=c$i[ch$i]\" -map \"[ch$i]\" \"${outFile.absolutePath}\""
                val session = executeMethod.invoke(null, cmd)
                
                val getReturnCodeMethod = session.javaClass.getMethod("getReturnCode")
                val returnCode = getReturnCodeMethod.invoke(session)
                
                val isSuccessMethod = returnCodeClass.getMethod("isSuccess", returnCodeClass)
                val success = isSuccessMethod.invoke(null, returnCode) as Boolean
                if (!success) {
                    return null
                }
            }
            return outputFiles
        } catch (e: Exception) {
            Log.e(TAG, "FFmpeg channel splitting failed", e)
            return null
        }
    }
}
