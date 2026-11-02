package com.example.model

enum class UploadStage {
    IDLE,
    SLICING,
    UPLOADING,
    FINALIZING,
    COMPLETED,
    ERROR
}

data class VideoUploadProgress(
    val stage: UploadStage = UploadStage.IDLE,
    val sliceProgress: Float = 0f,
    val uploadProgress: Float = 0f,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val statusMessage: String = ""
)
