package com.fahad.easypeasy.engine

/** Immutable, reviewed artifacts only. Never fill a hash with zeroes or guess a gated URL. */
data class ModelSpec(val id: String, val label: String, val url: String?, val sha256: String?, val bytes: Long, val license: String) {
    val downloadable get() = url?.startsWith("https://") == true && sha256?.matches(Regex("[a-f0-9]{64}")) == true && bytes > 0
}
object ModelCatalog {
    val silero = ModelSpec(
        "silero-vad", "Silero VAD (16 kHz)",
        "https://raw.githubusercontent.com/snakers4/silero-vad/1e261b036686cd0017d500ee96acd1c4ba572a9d/src/silero_vad/data/silero_vad.onnx",
        "1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3", 2327524, "MIT",
    )
    val stt = ModelSpec("korean-stt", "Korean + English STT — adapter not installed", null, null, 0, "Model-dependent; not distributed")
    val gemma1b = ModelSpec("gemma-1b", "Gemma 3 1B — verified MediaPipe conversion required", null, null, 0, "Gemma Terms of Use")
    val gemma4b = ModelSpec("gemma-4b", "Gemma 3 4B — verified MediaPipe conversion required", null, null, 0, "Gemma Terms of Use")
    val all = listOf(silero, stt, gemma1b, gemma4b)
    fun recommended(totalRamBytes: Long) = if (totalRamBytes >= 8L * 1024 * 1024 * 1024) gemma4b else gemma1b
}
