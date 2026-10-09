package com.davide.seddio.easygallery.data

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreVolumeSnapshotProviderTest {

    private val context: Context = mockk(relaxed = true)

    @Test
    fun `api 30 plus snapshot uses external recent versions and generations`() {
        val gateway = FakeGateway(
            externalVolumes = linkedSetOf("external_primary", "external"),
            recentVolumes = linkedSetOf("external_primary", "external", "0123-4567"),
            globalVersion = "ignored",
            perVolumeVersions = mapOf("external_primary" to "v2", "external" to "v1"),
            generations = mapOf("external_primary" to 12L, "external" to 7L)
        )

        val snapshot = AndroidMediaStoreVolumeSnapshotProvider(context, gateway).snapshot(30)

        assertEquals(MediaStoreDeltaApiBranch.API_30_PLUS, snapshot.apiBranch)
        assertEquals(setOf("external_primary", "external"), snapshot.presentVolumes)
        assertEquals(setOf("0123-4567"), snapshot.knownButUnavailableVolumes)
        assertEquals(2, snapshot.observedVolumes.size)
        assertEquals(2, gateway.perVolumeVersionCalls)
        assertEquals(2, gateway.generationCalls)
    }

    @Test
    fun `api 29 snapshot uses per-volume versions without generations`() {
        val gateway = FakeGateway(
            externalVolumes = linkedSetOf("external_primary", "external"),
            recentVolumes = emptySet(),
            globalVersion = "ignored",
            perVolumeVersions = mapOf("external_primary" to "v3", "external" to "v8"),
            generations = emptyMap()
        )

        val snapshot = AndroidMediaStoreVolumeSnapshotProvider(context, gateway).snapshot(29)

        assertEquals(MediaStoreDeltaApiBranch.API_29, snapshot.apiBranch)
        assertEquals(setOf("external_primary", "external"), snapshot.presentVolumes)
        assertTrue(snapshot.knownButUnavailableVolumes.isEmpty())
        assertEquals(listOf(null, null), snapshot.observedVolumes.map { it.generation })
        assertEquals(2, gateway.perVolumeVersionCalls)
        assertEquals(0, gateway.generationCalls)
    }

    @Test
    fun `api 28 snapshot observes only primary volume and global version`() {
        val gateway = FakeGateway(
            externalVolumes = linkedSetOf("external_primary", "external"),
            recentVolumes = emptySet(),
            globalVersion = "legacy-v1",
            perVolumeVersions = mapOf("external_primary" to "should-not-be-called"),
            generations = emptyMap()
        )

        val snapshot = AndroidMediaStoreVolumeSnapshotProvider(context, gateway).snapshot(28)

        assertEquals(MediaStoreDeltaApiBranch.API_28, snapshot.apiBranch)
        assertEquals(setOf("external_primary"), snapshot.presentVolumes)
        assertEquals(1, snapshot.observedVolumes.size)
        assertEquals("external_primary", snapshot.observedVolumes.single().volumeName)
        assertEquals("legacy-v1", snapshot.observedVolumes.single().version)
        assertEquals(1, gateway.globalVersionCalls)
        assertEquals(0, gateway.perVolumeVersionCalls)
        assertEquals(0, gateway.externalVolumesCalls)
    }

    private class FakeGateway(
        private val externalVolumes: Set<String>,
        private val recentVolumes: Set<String>,
        private val globalVersion: String?,
        private val perVolumeVersions: Map<String, String?>,
        private val generations: Map<String, Long>
    ) : MediaStoreVolumeGateway {

        var externalVolumesCalls: Int = 0
        var perVolumeVersionCalls: Int = 0
        var globalVersionCalls: Int = 0
        var generationCalls: Int = 0

        override fun externalVolumeNames(context: Context): Set<String> {
            externalVolumesCalls += 1
            return externalVolumes
        }

        override fun recentExternalVolumeNames(context: Context): Set<String> {
            return recentVolumes
        }

        override fun version(context: Context): String? {
            globalVersionCalls += 1
            return globalVersion
        }

        override fun version(context: Context, volumeName: String): String? {
            perVolumeVersionCalls += 1
            return perVolumeVersions[volumeName]
        }

        override fun generation(context: Context, volumeName: String): Long {
            generationCalls += 1
            return generations[volumeName] ?: 0L
        }
    }
}
