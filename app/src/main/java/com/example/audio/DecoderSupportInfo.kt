package com.example.audio

data class DecoderSupportInfo(
    val hasAc4Decoder: Boolean,
    val ac4DecoderNames: List<String>,
    val availableCodecs: List<CodecDetail>,
    val sdkInt: Int = android.os.Build.VERSION.SDK_INT
)

data class CodecDetail(
    val name: String,
    val mimeType: String,
    val isEncoder: Boolean,
    val maxChannels: Int = 0,
    val supportedSampleRates: List<Int> = emptyList()
)
