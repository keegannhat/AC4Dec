package com.example.audio

data class DecoderSupportInfo(
    val hasAc4Decoder: Boolean,
    val ac4DecoderNames: List<String>,
    val availableCodecs: List<CodecDetail>,
    val sdkInt: Int = android.os.Build.VERSION.SDK_INT,
    // Software decoder capabilities (via FFmpegKit audio build)
    val hasSoftwareTrueHd: Boolean = true,  // always true when ffmpeg-kit-android-audio is present
    val hasSoftwareDts: Boolean = true,
    val hasSoftwareEac3: Boolean = true,
    // Hardware TrueHD (very rare, found on some Qualcomm SoCs)
    val hasTrueHdHardwareDecoder: Boolean = false,
    val trueHdDecoderNames: List<String> = emptyList(),
    // Hardware DTS (rare)
    val hasDtsHardwareDecoder: Boolean = false,
    val dtsDecoderNames: List<String> = emptyList()
)

data class CodecDetail(
    val name: String,
    val mimeType: String,
    val isEncoder: Boolean,
    val maxChannels: Int = 0,
    val supportedSampleRates: List<Int> = emptyList()
)
