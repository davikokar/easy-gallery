package com.davide.seddio.easygallery.data

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class SlideshowOptionsStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("slideshow_options", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    private fun store() = SharedPreferencesSlideshowOptionsStore(context)

    @Test
    fun `defaults are the documented ones`() {
        val defaults = SlideshowOptions()

        assertEquals(3, defaults.intervalSeconds)
        assertEquals(SlideshowTransition.FADE, defaults.transition)
        assertEquals(true, defaults.includeVideos)
        assertEquals(false, defaults.includeGifs)
        assertEquals(SlideshowOrder.ASCENDING, defaults.order)
        assertEquals(false, defaults.loop)
        assertEquals(SlideshowOrientation.ALL, defaults.orientation)
        assertEquals(1, SLIDESHOW_MIN_INTERVAL_SECONDS)
        assertEquals(60, SLIDESHOW_MAX_INTERVAL_SECONDS)
    }

    @Test
    fun `an empty file loads the defaults`() {
        assertEquals(SlideshowOptions(), store().load())
    }

    @Test
    fun `saved options round trip through a new store instance`() {
        val options = SlideshowOptions(
            intervalSeconds = 12,
            transition = SlideshowTransition.NONE,
            includeVideos = false,
            includeGifs = true,
            order = SlideshowOrder.RANDOM,
            loop = true,
            orientation = SlideshowOrientation.PORTRAIT_ONLY
        )

        store().save(options)

        assertEquals(options, store().load())
    }

    @Test
    fun `options are written to the slideshow_options file under the documented keys`() {
        store().save(SlideshowOptions(order = SlideshowOrder.DESCENDING))

        val keys = prefs.all.keys
        assertEquals(
            setOf(
                "interval_seconds", "transition", "include_videos", "include_gifs",
                "order", "loop", "orientation"
            ),
            keys
        )
        assertEquals("DESCENDING", prefs.getString("order", null))
    }

    @Test
    fun `interval above the maximum is clamped on load`() {
        prefs.edit().putInt("interval_seconds", 500).commit()

        assertEquals(SLIDESHOW_MAX_INTERVAL_SECONDS, store().load().intervalSeconds)
    }

    @Test
    fun `interval below the minimum is clamped on load`() {
        prefs.edit().putInt("interval_seconds", 0).commit()
        assertEquals(SLIDESHOW_MIN_INTERVAL_SECONDS, store().load().intervalSeconds)

        prefs.edit().putInt("interval_seconds", -7).commit()
        assertEquals(SLIDESHOW_MIN_INTERVAL_SECONDS, store().load().intervalSeconds)
    }

    @Test
    fun `interval bounds are kept as stored`() {
        prefs.edit().putInt("interval_seconds", 1).commit()
        assertEquals(1, store().load().intervalSeconds)

        prefs.edit().putInt("interval_seconds", 60).commit()
        assertEquals(60, store().load().intervalSeconds)
    }

    @Test
    fun `unknown enum names fall back to that field's default only`() {
        prefs.edit()
            .putString("transition", "SPIN")
            .putString("order", "SIDEWAYS")
            .putString("orientation", "")
            .putInt("interval_seconds", 9)
            .putBoolean("loop", true)
            .commit()

        val loaded = store().load()

        assertEquals(SlideshowTransition.FADE, loaded.transition)
        assertEquals(SlideshowOrder.ASCENDING, loaded.order)
        assertEquals(SlideshowOrientation.ALL, loaded.orientation)
        assertEquals(9, loaded.intervalSeconds)
        assertEquals(true, loaded.loop)
    }

    @Test
    fun `an enum name is case sensitive`() {
        prefs.edit().putString("order", "random").commit()

        assertEquals(SlideshowOrder.ASCENDING, store().load().order)
    }

    @Test
    fun `values of the wrong type load as defaults instead of throwing`() {
        prefs.edit()
            .putString("interval_seconds", "fast")
            .putInt("transition", 3)
            .putString("include_videos", "yes")
            .commit()

        val loaded = store().load()

        assertEquals(SlideshowOptions(), loaded)
    }

    @Test
    fun `a missing boolean keeps its own default`() {
        prefs.edit().putString("order", "RANDOM").commit()

        val loaded = store().load()

        assertTrue(loaded.includeVideos)
        assertEquals(false, loaded.includeGifs)
        assertEquals(SlideshowOrder.RANDOM, loaded.order)
    }

    @Test
    fun `the in-memory store starts from its initial options and keeps the last save`() {
        val initial = SlideshowOptions(intervalSeconds = 20)
        val store = InMemorySlideshowOptionsStore(initial)
        assertEquals(initial, store.load())

        val updated = SlideshowOptions(loop = true)
        store.save(updated)

        assertEquals(updated, store.load())
        assertEquals(SlideshowOptions(), InMemorySlideshowOptionsStore().load())
    }
}
