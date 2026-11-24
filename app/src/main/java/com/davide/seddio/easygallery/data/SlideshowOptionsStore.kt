package com.davide.seddio.easygallery.data

import android.content.Context
import android.content.SharedPreferences

interface SlideshowOptionsStore {
    fun load(): SlideshowOptions
    fun save(options: SlideshowOptions)
}

class InMemorySlideshowOptionsStore(initial: SlideshowOptions = SlideshowOptions()) : SlideshowOptionsStore {
    private var options = initial

    override fun load(): SlideshowOptions = options

    override fun save(options: SlideshowOptions) {
        this.options = options
    }
}

class SharedPreferencesSlideshowOptionsStore(context: Context) : SlideshowOptionsStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): SlideshowOptions {
        val defaults = SlideshowOptions()
        return SlideshowOptions(
            intervalSeconds = readOrDefault(defaults.intervalSeconds) { prefs.getInt(KEY_INTERVAL_SECONDS, it) }
                .coerceIn(SLIDESHOW_MIN_INTERVAL_SECONDS, SLIDESHOW_MAX_INTERVAL_SECONDS),
            transition = readEnum(KEY_TRANSITION, defaults.transition),
            includeVideos = readOrDefault(defaults.includeVideos) { prefs.getBoolean(KEY_INCLUDE_VIDEOS, it) },
            includeGifs = readOrDefault(defaults.includeGifs) { prefs.getBoolean(KEY_INCLUDE_GIFS, it) },
            order = readEnum(KEY_ORDER, defaults.order),
            loop = readOrDefault(defaults.loop) { prefs.getBoolean(KEY_LOOP, it) },
            orientation = readEnum(KEY_ORIENTATION, defaults.orientation)
        )
    }

    override fun save(options: SlideshowOptions) {
        prefs.edit()
            .putInt(KEY_INTERVAL_SECONDS, options.intervalSeconds)
            .putString(KEY_TRANSITION, options.transition.name)
            .putBoolean(KEY_INCLUDE_VIDEOS, options.includeVideos)
            .putBoolean(KEY_INCLUDE_GIFS, options.includeGifs)
            .putString(KEY_ORDER, options.order.name)
            .putBoolean(KEY_LOOP, options.loop)
            .putString(KEY_ORIENTATION, options.orientation.name)
            .apply()
    }

    // A value of another type under a key makes the typed getter throw; treat it as missing.
    private inline fun <T> readOrDefault(default: T, read: (T) -> T): T =
        try {
            read(default)
        } catch (_: ClassCastException) {
            default
        }

    private inline fun <reified T : Enum<T>> readEnum(key: String, default: T): T {
        val name = readOrDefault<String?>(null) { prefs.getString(key, it) }
        return enumValues<T>().firstOrNull { it.name == name } ?: default
    }

    private companion object {
        const val PREFS_NAME = "slideshow_options"
        const val KEY_INTERVAL_SECONDS = "interval_seconds"
        const val KEY_TRANSITION = "transition"
        const val KEY_INCLUDE_VIDEOS = "include_videos"
        const val KEY_INCLUDE_GIFS = "include_gifs"
        const val KEY_ORDER = "order"
        const val KEY_LOOP = "loop"
        const val KEY_ORIENTATION = "orientation"
    }
}
