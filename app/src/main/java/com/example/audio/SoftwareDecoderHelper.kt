package com.example.audio

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

object SoftwareDecoderHelper {

    fun copyUriToTemp(context: Context, uri: Uri, suffix: String): File {
        val tempFile = File(context.cacheDir, "sw_decode_input_$suffix")
        if (tempFile.exists()) tempFile.delete()
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Cannot open input stream for $uri")
        return tempFile
    }

    fun detectFormatKey(fileName: String, mimeType: String?): String {
        val lower = fileName.lowercase()
        val ext = lower.substringAfterLast('.', "")
        return when {
            TrueHdDecoder.isTrueHdFile(fileName) -> "truehd"
            DtsDecoder.isDtsFile(fileName) -> "dts"
            mimeType?.contains("true-hd", ignoreCase = true) == true -> "truehd"
            mimeType?.contains("dts", ignoreCase = true) == true -> "dts"
            mimeType?.contains("eac3", ignoreCase = true) == true -> "eac3"
            mimeType?.contains("ac4", ignoreCase = true) == true -> "ac4"
            ext in setOf("thd", "mlp", "truehd") -> "truehd"
            ext in setOf("dts", "dtshd", "dtsma") -> "dts"
            ext in setOf("ec3", "eac3") -> "eac3"
            ext == "ac4" -> "ac4"
            else -> "unknown"
        }
    }
}
