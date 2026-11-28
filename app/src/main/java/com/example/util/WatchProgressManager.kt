package com.example.util

import android.content.Context
import com.example.model.VideoWatchProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Persiste el progreso de reproducción de vídeos y marca automáticamente episodios como vistos
 * al superar el 85% o completar el episodio.
 */
class WatchProgressManager(context: Context) {

    private val prefs = context.getSharedPreferences("video_watch_progress_prefs", Context.MODE_PRIVATE)
    private val lock = Any()

    private val _progressMap = MutableStateFlow<Map<Int, VideoWatchProgress>>(loadAllLocked())
    val progressMap: StateFlow<Map<Int, VideoWatchProgress>> = _progressMap.asStateFlow()

    private fun loadAllLocked(): Map<Int, VideoWatchProgress> {
        val raw = prefs.getString("progress_json", "").orEmpty()
        if (raw.isBlank()) return emptyMap()
        return try {
            val json = JSONObject(raw)
            val result = LinkedHashMap<Int, VideoWatchProgress>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val postId = k.toIntOrNull() ?: continue
                val obj = json.optJSONObject(k) ?: continue
                result[postId] = VideoWatchProgress(
                    postId = postId,
                    chunkIndex = obj.optInt("chunk", 0),
                    positionMs = obj.optLong("pos", 0L),
                    globalPositionMs = obj.optLong("gpos", obj.optLong("pos", 0L)),
                    durationMs = obj.optLong("dur", 0L),
                    isWatched = obj.optBoolean("watched", false),
                    updatedAt = obj.optLong("updated", 0L)
                )
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun persistLocked(map: Map<Int, VideoWatchProgress>) {
        try {
            val trimmed = if (map.size > 1500) {
                map.values.sortedByDescending { it.updatedAt }.take(1500).associateBy { it.postId }
            } else map

            val json = JSONObject()
            trimmed.forEach { (id, item) ->
                json.put(
                    id.toString(),
                    JSONObject().apply {
                        put("chunk", item.chunkIndex)
                        put("pos", item.positionMs)
                        put("gpos", item.globalPositionMs)
                        put("dur", item.durationMs)
                        put("watched", item.isWatched)
                        put("updated", item.updatedAt)
                    }
                )
            }
            prefs.edit().putString("progress_json", json.toString()).apply()
        } catch (_: Exception) {}
    }

    fun updateProgress(
        postId: Int,
        chunkIndex: Int,
        localPositionMs: Long,
        globalPositionMs: Long,
        totalDurationMs: Long,
        markEnded: Boolean = false
    ) {
        if (postId <= 0) return
        synchronized(lock) {
            val currentMap = _progressMap.value.toMutableMap()
            val prev = currentMap[postId]
            val safeDuration = maxOf(totalDurationMs, prev?.durationMs ?: 0L)
            val completedByThreshold = safeDuration > 3_000L && globalPositionMs >= (safeDuration * 0.85).toLong()
            val isWatchedNow = markEnded || (prev?.isWatched == true) || completedByThreshold

            val nextItem = VideoWatchProgress(
                postId = postId,
                chunkIndex = if (markEnded) 0 else chunkIndex.coerceAtLeast(0),
                positionMs = if (markEnded) 0L else localPositionMs.coerceAtLeast(0L),
                globalPositionMs = if (markEnded && safeDuration > 0L) safeDuration else globalPositionMs.coerceAtLeast(0L),
                durationMs = safeDuration,
                isWatched = isWatchedNow,
                updatedAt = System.currentTimeMillis()
            )
            currentMap[postId] = nextItem
            _progressMap.value = currentMap
            persistLocked(currentMap)
        }
    }

    fun setWatched(postId: Int, watched: Boolean) {
        if (postId <= 0) return
        synchronized(lock) {
            val currentMap = _progressMap.value.toMutableMap()
            val prev = currentMap[postId] ?: VideoWatchProgress(postId = postId)
            val next = prev.copy(
                isWatched = watched,
                positionMs = if (!watched) 0L else prev.positionMs,
                globalPositionMs = if (!watched) 0L else prev.globalPositionMs,
                updatedAt = System.currentTimeMillis()
            )
            currentMap[postId] = next
            _progressMap.value = currentMap
            persistLocked(currentMap)
        }
    }

    fun getProgress(postId: Int): VideoWatchProgress? = _progressMap.value[postId]
}
