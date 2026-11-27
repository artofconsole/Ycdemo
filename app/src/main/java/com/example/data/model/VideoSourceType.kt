package com.example.data.model

enum class VideoSourceType(val id: String, val labelTr: String) {
    LOCAL("LOCAL", "Cihaz"),
    YOUTUBE("YOUTUBE", "YouTube"),
    CLOUD_URL("CLOUD_URL", "Bulut");

    companion object {
        fun fromId(raw: String?): VideoSourceType {
            return entries.firstOrNull { it.id == raw } ?: LOCAL
        }
    }
}
