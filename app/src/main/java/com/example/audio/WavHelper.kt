package com.example.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavHelper {

    private fun readExactly(stream: java.io.InputStream, buf: ByteArray): Boolean {
        var offset = 0
        while (offset < buf.size) {
            val n = stream.read(buf, offset, buf.size - offset)
            if (n == -1) return false
            offset += n
        }
        return true
    }

    /**
     * Writes standard 44-byte WAV header.
     */
    fun writeWavHeader(
        channelCount: Int,
        sampleRate: Int,
        bitsPerSample: Int,
        dataSize: Long,
        outputStream: java.io.OutputStream
    ) {
        val header = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            
            // RIFF chunk descriptor
            put("RIFF".toByteArray())
            putInt((36 + dataSize).toInt()) // ChunkSize
            put("WAVE".toByteArray())
            
            // "fmt " sub-chunk
            put("fmt ".toByteArray())
            putInt(16) // Subchunk1Size for PCM
            putShort(1.toShort()) // AudioFormat: 1 for PCM
            putShort(channelCount.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channelCount * (bitsPerSample / 8)) // ByteRate
            putShort((channelCount * (bitsPerSample / 8)).toShort()) // BlockAlign
            putShort(bitsPerSample.toShort()) // BitsPerSample
            
            // "data" sub-chunk
            put("data".toByteArray())
            putInt(dataSize.toInt()) // Subchunk2Size
        }
        outputStream.write(header.array())
    }

    /**
     * Updates WAV header data size dynamically (useful if total size is unknown beforehand).
     */
    fun updateWavHeaderSizes(file: File, dataSize: Long) {
        RandomAccessFile(file, "rw").use { raf ->
            // ChunkSize at byte offset 4: 36 + dataSize
            raf.seek(4)
            val chunkSize = 36 + dataSize
            raf.write(
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkSize.toInt()).array()
            )
            
            // Subchunk2Size at byte offset 40: dataSize
            raf.seek(40)
            raf.write(
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize.toInt()).array()
            )
        }
    }

    data class WavHeaderInfo(
        val channelCount: Int,
        val sampleRate: Int,
        val bitsPerSample: Int
    )

    fun readWavHeader(file: File): WavHeaderInfo {
        file.inputStream().use { fis ->
            val header = ByteArray(44)
            var offset = 0
            while (offset < 44) {
                val n = fis.read(header, offset, 44 - offset)
                if (n == -1) break
                offset += n
            }
            if (offset < 44) {
                throw java.io.IOException("Invalid WAV header size: $offset bytes")
            }
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val channelCount = buf.getShort(22).toInt() and 0xFFFF
            val sampleRate = buf.getInt(24)
            val bitsPerSample = buf.getShort(34).toInt() and 0xFFFF
            return WavHeaderInfo(channelCount, sampleRate, bitsPerSample)
        }
    }

    /**
     * Splits multi-channel interleaved PCM data into multiple mono WAV files.
     * Returns a list of split mono WAV files, encoded at the chosen bitsPerSample.
     */
    fun splitMultichannelWav(
        inputFile: File,
        outputDir: File,
        baseName: String,
        channelCount: Int,
        sampleRate: Int,
        bitsPerSample: Int
    ): List<File> {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        // Dynamically detect input characteristics
        val headerInfo = try { readWavHeader(inputFile) } catch (e: Exception) { WavHeaderInfo(channelCount, sampleRate, 16) }
        val inputBitsPerSample = headerInfo.bitsPerSample
        val inputSampleSize = inputBitsPerSample / 8
        val inputChannelCount = headerInfo.channelCount
        val inputFrameSize = inputChannelCount * inputSampleSize

        val channelNames = when (inputChannelCount) {
            1 -> listOf("Mono")
            2 -> listOf("Left", "Right")
            6 -> listOf("Left(L)", "Right(R)", "Center(C)", "LFE", "SurroundLeft(Ls)", "SurroundRight(Rs)")
            8 -> listOf("Left", "Right", "Center", "LFE", "SurroundLeft", "SurroundRight", "BackLeft", "BackRight")
            else -> (1..inputChannelCount).map { "Channel_$it" }
        }
        
        val files = channelNames.map { name ->
            File(outputDir, "${baseName}_$name.wav")
        }
        
        val fileStreams = files.map { file ->
            val fos = FileOutputStream(file)
            writeWavHeader(1, headerInfo.sampleRate, bitsPerSample, 0, fos)
            fos
        }

        val outputSampleSize = bitsPerSample / 8

        try {
            inputFile.inputStream().buffered().use { fis ->
                fis.skip(44)
                
                val buffer = ByteArray(inputFrameSize)
                val dataSizes = LongArray(inputChannelCount) { 0L }

                while (readExactly(fis, buffer)) {
                    for (c in 0 until inputChannelCount) {
                        val sampleOffset = c * inputSampleSize
                        val sample16 = when (inputBitsPerSample) {
                            16 -> {
                                val low = buffer[sampleOffset].toInt() and 0xFF
                                val high = buffer[sampleOffset + 1].toInt()
                                ((high shl 8) or low).toShort()
                            }
                            24 -> {
                                val b0 = buffer[sampleOffset].toInt() and 0xFF
                                val b1 = buffer[sampleOffset + 1].toInt() and 0xFF
                                val b2 = buffer[sampleOffset + 2].toInt()
                                val val24 = (b2 shl 16) or (b1 shl 8) or b0
                                (val24 shr 8).toShort()
                            }
                            32 -> {
                                val b0 = buffer[sampleOffset].toInt() and 0xFF
                                val b1 = buffer[sampleOffset + 1].toInt() and 0xFF
                                val b2 = buffer[sampleOffset + 2].toInt() and 0xFF
                                val b3 = buffer[sampleOffset + 3].toInt()
                                val val32 = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
                                (val32 shr 16).toShort()
                            }
                            else -> {
                                val low = buffer[sampleOffset].toInt() and 0xFF
                                val high = buffer[sampleOffset + 1].toInt()
                                ((high shl 8) or low).toShort()
                            }
                        }
                        
                        writeSample(fileStreams[c], sample16, bitsPerSample)
                        dataSizes[c] += outputSampleSize
                    }
                }
                
                for (c in 0 until inputChannelCount) {
                    fileStreams[c].close()
                    updateWavHeaderSizes(files[c], dataSizes[c])
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            fileStreams.forEach { try { it.close() } catch (ex: Exception) {} }
        }

        return files
    }

    /**
     * Helper to write a 16-bit PCM short sample scaled to desired target bits per sample
     */
    private fun writeSample(os: java.io.OutputStream, sample: Short, bitsPerSample: Int) {
        when (bitsPerSample) {
            16 -> {
                os.write(sample.toInt() and 0xFF)
                os.write((sample.toInt() shr 8) and 0xFF)
            }
            24 -> {
                // Scale 16-bit to 24-bit PCM (shift left by 8 bits)
                val val24 = sample.toInt() shl 8
                os.write(val24 and 0xFF)
                os.write((val24 shr 8) and 0xFF)
                os.write((val24 shr 16) and 0xFF)
            }
            32 -> {
                // Scale 16-bit to 32-bit PCM (shift left by 16 bits)
                val val32 = sample.toInt() shl 16
                os.write(val32 and 0xFF)
                os.write((val32 shr 8) and 0xFF)
                os.write((val32 shr 16) and 0xFF)
                os.write((val32 shr 24) and 0xFF)
            }
            else -> {
                os.write(sample.toInt() and 0xFF)
                os.write((sample.toInt() shr 8) and 0xFF)
            }
        }
    }

    /**
     * Downmixes a 5.1/7.1 or multi-channel WAV file into Stereo/Binaural WAV file.
     */
    fun downmixToStereWav(
        inputFile: File,
        outputFile: File,
        sourceChannelCount: Int,
        sampleRate: Int,
        bitsPerSample: Int
    ): File {
        // Dynamically detect input characteristics
        val headerInfo = try { readWavHeader(inputFile) } catch (e: Exception) { WavHeaderInfo(sourceChannelCount, sampleRate, 16) }
        val inputBitsPerSample = headerInfo.bitsPerSample
        val inputSampleSize = inputBitsPerSample / 8
        val inputChannelCount = headerInfo.channelCount
        val inputFrameSize = inputChannelCount * inputSampleSize
        
        val fos = FileOutputStream(outputFile)
        writeWavHeader(2, headerInfo.sampleRate, bitsPerSample, 0, fos)
        
        var outputDataSize = 0L
        val outputSampleSize = bitsPerSample / 8

        try {
            inputFile.inputStream().buffered().use { fis ->
                fis.skip(44) // Skip WAV header
                
                val frameBuffer = ByteArray(inputFrameSize)
                
                while (readExactly(fis, frameBuffer)) {
                    val channels = ShortArray(inputChannelCount)
                    for (c in 0 until inputChannelCount) {
                        val offset = c * inputSampleSize
                        channels[c] = when (inputBitsPerSample) {
                            16 -> {
                                val low = frameBuffer[offset].toInt() and 0xFF
                                val high = frameBuffer[offset + 1].toInt()
                                ((high shl 8) or low).toShort()
                            }
                            24 -> {
                                val b0 = frameBuffer[offset].toInt() and 0xFF
                                val b1 = frameBuffer[offset + 1].toInt() and 0xFF
                                val b2 = frameBuffer[offset + 2].toInt()
                                val val24 = (b2 shl 16) or (b1 shl 8) or b0
                                (val24 shr 8).toShort()
                            }
                            32 -> {
                                val b0 = frameBuffer[offset].toInt() and 0xFF
                                val b1 = frameBuffer[offset + 1].toInt() and 0xFF
                                val b2 = frameBuffer[offset + 2].toInt() and 0xFF
                                val b3 = frameBuffer[offset + 3].toInt()
                                val val32 = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
                                (val32 shr 16).toShort()
                            }
                            else -> {
                                val low = frameBuffer[offset].toInt() and 0xFF
                                val high = frameBuffer[offset + 1].toInt()
                                ((high shl 8) or low).toShort()
                            }
                        }
                    }

                    val l: Short
                    val r: Short
                    
                    if (inputChannelCount >= 6) {
                        // 5.1 layout indices: L=0, R=1, C=2, LFE=3, Ls=4, Rs=5
                        val leftVal = channels[0].toFloat()
                        val rightVal = channels[1].toFloat()
                        val centerVal = channels[2].toFloat()
                        val lsVal = channels[4].toFloat()
                        val rsVal = channels[5].toFloat()

                        val mixedL = (leftVal + (centerVal * 0.707f) + (lsVal * 0.707f)) * 0.707f
                        val mixedR = (rightVal + (centerVal * 0.707f) + (rsVal * 0.707f)) * 0.707f

                        l = mixedL.coerceIn(-32768f, 32767f).toInt().toShort()
                        r = mixedR.coerceIn(-32768f, 32767f).toInt().toShort()
                    } else if (inputChannelCount == 1) {
                        l = channels[0]
                        r = channels[0]
                    } else {
                        l = channels[0]
                        r = channels[1]
                    }

                    // Write Stereo frame
                    writeSample(fos, l, bitsPerSample)
                    writeSample(fos, r, bitsPerSample)
                    outputDataSize += (outputSampleSize * 2)
                }
            }
        } finally {
            fos.close()
            updateWavHeaderSizes(outputFile, outputDataSize)
        }

        return outputFile
    }
}
