package com.davide.seddio.easygallery.data

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class MediaIndexTierABatchIndexerTest {

    @Test
    fun `indexDelta composes delta as read detect select and keeps pre-detect watermark for selection`() = runTest {
        val store = InMemoryMediaIndexWatermarkStore(
            mapOf(PRIMARY to MediaIndexVolumeWatermark(version = "v1", generation = 10L, dateModifiedWatermarkSeconds = null))
        )
        val snapshot = MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_30_PLUS,
            observedVolumes = listOf(
                ObservedMediaStoreVolume(volumeName = PRIMARY, version = "v1", generation = 20L)
            ),
            presentVolumes = setOf(PRIMARY),
            knownButUnavailableVolumes = emptySet()
        )
        val detector = MediaIndexDeltaDetector(
            watermarkStore = store,
            snapshotProvider = FixedSnapshotProvider(snapshot)
        )
        val rowProvider = FixedRowDeltaProvider(
            mapOf(
                PRIMARY to listOf(
                    observedRow("content://media/external_primary/images/media/1", generation = 15L)
                )
            )
        )
        val repository = FakeMediaRepository().apply {
            mediaItems = listOf(media("content://media/external/images/media/1"))
        }
        val indexStore = InMemoryMediaIndexStore()
        val indexer = MediaIndexTierABatchIndexer(
            mediaRepository = repository,
            mediaIndexStore = indexStore,
            deltaDetector = detector,
            rowDeltaProvider = rowProvider,
            reconciler = MediaIndexReconciler(indexStore),
            extractor = ConstantExtractor(),
            chunkSize = 10
        )

        val result = indexer.indexDelta(sdkInt = 30)

        assertEquals(1, result.selectedRows)
        assertEquals(1, result.indexedRows)
        assertEquals(0, result.skippedRows)

        val indexed = indexStore.getByMediaUri("content://media/external/images/media/1")
        assertEquals(1234L, indexed?.capturedDateTakenEpochMillis)
        assertEquals(MediaIndexVolumeWatermark("v1", 20L, null), store.read(PRIMARY))
    }

    @Test
    fun `indexDelta keeps batch running when extractor hits security missing-file and corrupt cases`() = runTest {
        val store = InMemoryMediaIndexWatermarkStore(
            mapOf(PRIMARY to MediaIndexVolumeWatermark(version = "v1", generation = 1L, dateModifiedWatermarkSeconds = null))
        )
        val snapshot = MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_30_PLUS,
            observedVolumes = listOf(
                ObservedMediaStoreVolume(volumeName = PRIMARY, version = "v1", generation = 10L)
            ),
            presentVolumes = setOf(PRIMARY),
            knownButUnavailableVolumes = emptySet()
        )
        val detector = MediaIndexDeltaDetector(
            watermarkStore = store,
            snapshotProvider = FixedSnapshotProvider(snapshot)
        )
        val uris = listOf(
            "content://media/external_primary/images/media/1",
            "content://media/external_primary/images/media/2",
            "content://media/external_primary/images/media/3"
        )
        val rowProvider = FixedRowDeltaProvider(
            mapOf(
                PRIMARY to uris.mapIndexed { index, uri -> observedRow(uri, generation = 2L + index) }
            )
        )
        val repository = FakeMediaRepository().apply {
            mediaItems = uris.map { media(it) }
        }
        val indexStore = InMemoryMediaIndexStore()
        val extractor = ThrowingExtractor(
            failures = mapOf(
                uris[0] to SecurityException("denied"),
                uris[1] to java.io.FileNotFoundException("gone"),
                uris[2] to IllegalStateException("corrupt")
            )
        )
        val indexer = MediaIndexTierABatchIndexer(
            mediaRepository = repository,
            mediaIndexStore = indexStore,
            deltaDetector = detector,
            rowDeltaProvider = rowProvider,
            reconciler = MediaIndexReconciler(indexStore),
            extractor = extractor,
            chunkSize = 10
        )

        val result = indexer.indexDelta(sdkInt = 30)

        assertEquals(3, result.selectedRows)
        assertEquals(3, result.indexedRows)
        assertEquals(0, result.skippedRows)
        uris.forEach { uri ->
            val row = indexStore.getByMediaUri(uri)
            assertNull(row?.capturedDateTakenEpochMillis)
            assertNull(row?.capturedLatitude)
            assertNull(row?.capturedLongitude)
            assertNull(row?.capturedWidth)
            assertNull(row?.capturedHeight)
        }
    }

    @Test
    fun `indexDelta is cancellable between chunks and does not advance api29 date watermark on cancellation`() = runTest {
        val store = InMemoryMediaIndexWatermarkStore(
            mapOf(PRIMARY to MediaIndexVolumeWatermark(version = "v1", generation = null, dateModifiedWatermarkSeconds = 10L))
        )
        val snapshot = MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_29,
            observedVolumes = listOf(
                ObservedMediaStoreVolume(volumeName = PRIMARY, version = "v1", generation = null)
            ),
            presentVolumes = setOf(PRIMARY),
            knownButUnavailableVolumes = emptySet()
        )
        val detector = MediaIndexDeltaDetector(
            watermarkStore = store,
            snapshotProvider = FixedSnapshotProvider(snapshot),
            versionProvider = FixedVersionProvider(mapOf(PRIMARY to "v1"))
        )
        val rows = (1..5).map { index ->
            MediaStoreObservedRowDelta(
                mediaUri = "content://media/external_primary/images/media/$index",
                volumeName = PRIMARY,
                generationAdded = null,
                generationModified = null,
                dateModifiedSeconds = 11L + index
            )
        }
        val rowProvider = FixedRowDeltaProvider(mapOf(PRIMARY to rows))
        val repository = FakeMediaRepository().apply {
            mediaItems = rows.map { media(it.mediaUri) }
        }
        val indexStore = InMemoryMediaIndexStore()
        val extractor = CancellingExtractor(cancelOnCall = 3)
        val indexer = MediaIndexTierABatchIndexer(
            mediaRepository = repository,
            mediaIndexStore = indexStore,
            deltaDetector = detector,
            rowDeltaProvider = rowProvider,
            reconciler = MediaIndexReconciler(indexStore),
            extractor = extractor,
            chunkSize = 2
        )

        try {
            indexer.indexDelta(sdkInt = 29)
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }

        assertEquals(2, indexStore.observeAllValue().size)
        assertEquals(10L, store.read(PRIMARY)?.dateModifiedWatermarkSeconds)
    }

    @Test
    fun `indexDelta cancellation on api30 plus does not advance generation watermark`() = runTest {
        val store = InMemoryMediaIndexWatermarkStore(
            mapOf(PRIMARY to MediaIndexVolumeWatermark(version = "v1", generation = 10L, dateModifiedWatermarkSeconds = null))
        )
        val snapshot = MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_30_PLUS,
            observedVolumes = listOf(
                ObservedMediaStoreVolume(volumeName = PRIMARY, version = "v1", generation = 20L)
            ),
            presentVolumes = setOf(PRIMARY),
            knownButUnavailableVolumes = emptySet()
        )
        val detector = MediaIndexDeltaDetector(
            watermarkStore = store,
            snapshotProvider = FixedSnapshotProvider(snapshot)
        )
        val rows = (1..5).map { index ->
            observedRow("content://media/external_primary/images/media/$index", generation = 10L + index)
        }
        val rowProvider = FixedRowDeltaProvider(mapOf(PRIMARY to rows))
        val repository = FakeMediaRepository().apply {
            mediaItems = rows.map { media(it.mediaUri.replace("/external_primary/", "/external/")) }
        }
        val indexStore = InMemoryMediaIndexStore()
        val extractor = CancellingExtractor(cancelOnCall = 3)
        val indexer = MediaIndexTierABatchIndexer(
            mediaRepository = repository,
            mediaIndexStore = indexStore,
            deltaDetector = detector,
            rowDeltaProvider = rowProvider,
            reconciler = MediaIndexReconciler(indexStore),
            extractor = extractor,
            chunkSize = 2
        )

        try {
            indexer.indexDelta(sdkInt = 30)
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }

        assertEquals(2, indexStore.observeAllValue().size)
        assertEquals(MediaIndexVolumeWatermark("v1", 10L, null), store.read(PRIMARY))
    }

    @Test
    fun `indexDelta preserves derived place fields when reindexing existing row`() = runTest {
        val store = InMemoryMediaIndexWatermarkStore(
            mapOf(PRIMARY to MediaIndexVolumeWatermark(version = "v1", generation = 5L, dateModifiedWatermarkSeconds = null))
        )
        val snapshot = MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_30_PLUS,
            observedVolumes = listOf(
                ObservedMediaStoreVolume(volumeName = PRIMARY, version = "v1", generation = 9L)
            ),
            presentVolumes = setOf(PRIMARY),
            knownButUnavailableVolumes = emptySet()
        )
        val detector = MediaIndexDeltaDetector(
            watermarkStore = store,
            snapshotProvider = FixedSnapshotProvider(snapshot)
        )
        val rowUri = "content://media/external/images/media/7"
        val rowProvider = FixedRowDeltaProvider(
            mapOf(PRIMARY to listOf(observedRow(rowUri.replace("/external/", "/external_primary/"), generation = 8L)))
        )
        val repository = FakeMediaRepository().apply {
            mediaItems = listOf(media(rowUri))
        }
        val indexStore = InMemoryMediaIndexStore().apply {
            upsert(
                listOf(
                    MediaIndexEntry(
                        mediaUri = rowUri,
                        displayName = "old-name.jpg",
                        size = 111L,
                        dateModified = 222L,
                        volumeName = PRIMARY,
                        generationAdded = 1L,
                        generationModified = 2L,
                        capturedDateTakenEpochMillis = null,
                        capturedLatitude = null,
                        capturedLongitude = null,
                        capturedWidth = null,
                        capturedHeight = null,
                        derivedCountryCode = "IT",
                        derivedRegionCode = "52",
                        derivedPlaceName = "Firenze"
                    )
                )
            )
        }
        val indexer = MediaIndexTierABatchIndexer(
            mediaRepository = repository,
            mediaIndexStore = indexStore,
            deltaDetector = detector,
            rowDeltaProvider = rowProvider,
            reconciler = MediaIndexReconciler(indexStore),
            extractor = ConstantExtractor(),
            chunkSize = 10
        )

        val result = indexer.indexDelta(sdkInt = 30)

        assertEquals(1, result.indexedRows)
        assertEquals(0, result.skippedRows)
        val indexed = indexStore.getByMediaUri(rowUri)
        assertEquals("IT", indexed?.derivedCountryCode)
        assertEquals("52", indexed?.derivedRegionCode)
        assertEquals("Firenze", indexed?.derivedPlaceName)
    }

    @Test
    fun `indexDelta keeps secondary-volume rows by canonicalizing observed uri namespace`() = runTest {
        val secondaryVolume = "0123-4567"
        val store = InMemoryMediaIndexWatermarkStore(
            mapOf(
                PRIMARY to MediaIndexVolumeWatermark(version = "v1", generation = 10L, dateModifiedWatermarkSeconds = null),
                secondaryVolume to MediaIndexVolumeWatermark(version = "v1", generation = 10L, dateModifiedWatermarkSeconds = null)
            )
        )
        val snapshot = MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_30_PLUS,
            observedVolumes = listOf(
                ObservedMediaStoreVolume(volumeName = PRIMARY, version = "v1", generation = 20L),
                ObservedMediaStoreVolume(volumeName = secondaryVolume, version = "v1", generation = 25L)
            ),
            presentVolumes = setOf(PRIMARY, secondaryVolume),
            knownButUnavailableVolumes = emptySet()
        )
        val detector = MediaIndexDeltaDetector(
            watermarkStore = store,
            snapshotProvider = FixedSnapshotProvider(snapshot)
        )
        val rowProvider = FixedRowDeltaProvider(
            mapOf(
                PRIMARY to listOf(
                    observedRow(
                        mediaUri = "content://media/external/images/media/1",
                        generation = 15L,
                        volumeName = PRIMARY
                    )
                ),
                secondaryVolume to listOf(
                    observedRow(
                        mediaUri = "content://media/0123-4567/images/media/2",
                        generation = 16L,
                        volumeName = secondaryVolume
                    )
                )
            )
        )
        val repository = FakeMediaRepository().apply {
            mediaItems = listOf(
                media("content://media/external/images/media/1"),
                media("content://media/external/images/media/2")
            )
        }
        val indexStore = InMemoryMediaIndexStore()
        val indexer = MediaIndexTierABatchIndexer(
            mediaRepository = repository,
            mediaIndexStore = indexStore,
            deltaDetector = detector,
            rowDeltaProvider = rowProvider,
            reconciler = MediaIndexReconciler(indexStore),
            extractor = ConstantExtractor(),
            chunkSize = 10
        )

        val result = indexer.indexDelta(sdkInt = 30)

        assertEquals(2, result.selectedRows)
        assertEquals(2, result.indexedRows)
        assertEquals(0, result.skippedRows)
        assertEquals(0, result.deletedRows)
        assertEquals(1, result.healedRows)
        assertEquals(
            MediaIndexVolumeWatermark("v1", 25L, null),
            store.read(secondaryVolume)
        )
    }

    @Test
    fun `indexDelta reports skipped rows and clamps generation watermark for missing live rows`() = runTest {
        val secondaryVolume = "0123-4567"
        val store = InMemoryMediaIndexWatermarkStore(
            mapOf(
                PRIMARY to MediaIndexVolumeWatermark(version = "v1", generation = 10L, dateModifiedWatermarkSeconds = null),
                secondaryVolume to MediaIndexVolumeWatermark(version = "v1", generation = 10L, dateModifiedWatermarkSeconds = null)
            )
        )
        val snapshot = MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_30_PLUS,
            observedVolumes = listOf(
                ObservedMediaStoreVolume(volumeName = PRIMARY, version = "v1", generation = 20L),
                ObservedMediaStoreVolume(volumeName = secondaryVolume, version = "v1", generation = 30L)
            ),
            presentVolumes = setOf(PRIMARY, secondaryVolume),
            knownButUnavailableVolumes = emptySet()
        )
        val detector = MediaIndexDeltaDetector(
            watermarkStore = store,
            snapshotProvider = FixedSnapshotProvider(snapshot)
        )
        val rowProvider = FixedRowDeltaProvider(
            mapOf(
                PRIMARY to listOf(
                    observedRow(
                        mediaUri = "content://media/external_primary/images/media/1",
                        generation = 11L,
                        volumeName = PRIMARY
                    )
                ),
                secondaryVolume to listOf(
                    observedRow(
                        mediaUri = "content://media/0123-4567/images/media/2",
                        generation = 12L,
                        volumeName = secondaryVolume
                    )
                )
            )
        )
        val repository = FakeMediaRepository().apply {
            mediaItems = listOf(media("content://media/external/images/media/1"))
        }
        val indexStore = InMemoryMediaIndexStore()
        val indexer = MediaIndexTierABatchIndexer(
            mediaRepository = repository,
            mediaIndexStore = indexStore,
            deltaDetector = detector,
            rowDeltaProvider = rowProvider,
            reconciler = MediaIndexReconciler(indexStore),
            extractor = ConstantExtractor(),
            chunkSize = 10
        )

        val result = indexer.indexDelta(sdkInt = 30)

        assertEquals(2, result.selectedRows)
        assertEquals(1, result.indexedRows)
        assertEquals(1, result.skippedRows)
        assertEquals(MediaIndexVolumeWatermark("v1", 20L, null), store.read(PRIMARY))
        assertEquals(MediaIndexVolumeWatermark("v1", 11L, null), store.read(secondaryVolume))
    }

    @Test
    fun `indexDelta fails when all selected rows are missing from live media`() = runTest {
        val store = InMemoryMediaIndexWatermarkStore(
            mapOf(PRIMARY to MediaIndexVolumeWatermark(version = "v1", generation = 10L, dateModifiedWatermarkSeconds = null))
        )
        val snapshot = MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_30_PLUS,
            observedVolumes = listOf(
                ObservedMediaStoreVolume(volumeName = PRIMARY, version = "v1", generation = 20L)
            ),
            presentVolumes = setOf(PRIMARY),
            knownButUnavailableVolumes = emptySet()
        )
        val detector = MediaIndexDeltaDetector(
            watermarkStore = store,
            snapshotProvider = FixedSnapshotProvider(snapshot)
        )
        val rowProvider = FixedRowDeltaProvider(
            mapOf(
                PRIMARY to listOf(
                    observedRow(
                        mediaUri = "content://media/external_primary/images/media/1",
                        generation = 11L,
                        volumeName = PRIMARY
                    )
                )
            )
        )
        val repository = FakeMediaRepository().apply {
            mediaItems = emptyList()
        }
        val indexStore = InMemoryMediaIndexStore()
        val indexer = MediaIndexTierABatchIndexer(
            mediaRepository = repository,
            mediaIndexStore = indexStore,
            deltaDetector = detector,
            rowDeltaProvider = rowProvider,
            reconciler = MediaIndexReconciler(indexStore),
            extractor = ConstantExtractor(),
            chunkSize = 10
        )

        try {
            indexer.indexDelta(sdkInt = 30)
            fail("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }

        assertEquals(MediaIndexVolumeWatermark("v1", 10L, null), store.read(PRIMARY))
    }

    @Test
    fun `indexDelta fails when selected row uri is not a MediaStore uri`() = runTest {
        val store = InMemoryMediaIndexWatermarkStore(
            mapOf(PRIMARY to MediaIndexVolumeWatermark(version = "v1", generation = 10L, dateModifiedWatermarkSeconds = null))
        )
        val snapshot = MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_30_PLUS,
            observedVolumes = listOf(
                ObservedMediaStoreVolume(volumeName = PRIMARY, version = "v1", generation = 20L)
            ),
            presentVolumes = setOf(PRIMARY),
            knownButUnavailableVolumes = emptySet()
        )
        val detector = MediaIndexDeltaDetector(
            watermarkStore = store,
            snapshotProvider = FixedSnapshotProvider(snapshot)
        )
        val rowProvider = FixedRowDeltaProvider(
            mapOf(
                PRIMARY to listOf(
                    MediaStoreObservedRowDelta(
                        mediaUri = "content://example.com/not-mediastore",
                        volumeName = PRIMARY,
                        generationAdded = 11L,
                        generationModified = 11L,
                        dateModifiedSeconds = 11L
                    )
                )
            )
        )
        val repository = FakeMediaRepository().apply {
            mediaItems = listOf(media("content://media/external/images/media/1"))
        }
        val indexStore = InMemoryMediaIndexStore()
        val indexer = MediaIndexTierABatchIndexer(
            mediaRepository = repository,
            mediaIndexStore = indexStore,
            deltaDetector = detector,
            rowDeltaProvider = rowProvider,
            reconciler = MediaIndexReconciler(indexStore),
            extractor = ConstantExtractor(),
            chunkSize = 10
        )

        try {
            indexer.indexDelta(sdkInt = 30)
            fail("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }

        assertEquals(MediaIndexVolumeWatermark("v1", 10L, null), store.read(PRIMARY))
    }

    private class FixedSnapshotProvider(
        private val snapshot: MediaStoreVolumeSnapshot
    ) : MediaStoreVolumeSnapshotProvider {
        override fun snapshot(sdkInt: Int): MediaStoreVolumeSnapshot = snapshot
    }

    private class FixedVersionProvider(
        private val versionsByVolume: Map<String, String?>
    ) : MediaStoreVolumeVersionProvider {
        override fun versionForVolume(volumeName: String): String? = versionsByVolume[volumeName]
    }

    private class FixedRowDeltaProvider(
        private val rowsByVolume: Map<String, List<MediaStoreObservedRowDelta>>
    ) : MediaStoreRowDeltaProvider {
        override fun queryObservedRows(
            volumeName: String,
            apiBranch: MediaStoreDeltaApiBranch
        ): List<MediaStoreObservedRowDelta> {
            return rowsByVolume[volumeName].orEmpty()
        }
    }

    private class ConstantExtractor : MediaIndexTierAExtractor {
        override suspend fun extract(mediaItem: MediaItem): MediaIndexTierAExtraction {
            return MediaIndexTierAExtraction(
                capturedDateTakenEpochMillis = 1234L,
                capturedLatitude = 45.0,
                capturedLongitude = 9.0,
                capturedWidth = 4032,
                capturedHeight = 3024
            )
        }
    }

    private class ThrowingExtractor(
        private val failures: Map<String, Exception>
    ) : MediaIndexTierAExtractor {
        override suspend fun extract(mediaItem: MediaItem): MediaIndexTierAExtraction {
            val uri = mediaItem.uri.toString()
            val failure = failures[uri]
            if (failure != null) throw failure
            return MediaIndexTierAExtraction.empty()
        }
    }

    private class CancellingExtractor(
        private val cancelOnCall: Int
    ) : MediaIndexTierAExtractor {
        private var calls = 0

        override suspend fun extract(mediaItem: MediaItem): MediaIndexTierAExtraction {
            calls += 1
            if (calls == cancelOnCall) {
                throw CancellationException("cancel at call $calls")
            }
            return MediaIndexTierAExtraction.empty()
        }
    }

    private fun observedRow(
        mediaUri: String,
        generation: Long,
        volumeName: String = PRIMARY
    ): MediaStoreObservedRowDelta {
        return MediaStoreObservedRowDelta(
            mediaUri = mediaUri,
            volumeName = volumeName,
            generationAdded = generation,
            generationModified = generation,
            dateModifiedSeconds = generation
        )
    }

    private fun media(mediaUri: String): MediaItem {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.toString() } returns mediaUri

        return MediaItem(
            uri = uri,
            name = "photo.jpg",
            dateAdded = 100L,
            dateModified = 200L,
            size = 300L,
            type = MediaType.IMAGE,
            bucketName = "Camera",
            folderPath = "/storage/emulated/0/DCIM/Camera",
            duration = null
        )
    }

    private suspend fun InMemoryMediaIndexStore.observeAllValue(): List<MediaIndexEntry> = observeAll().first()

    companion object {
        private const val PRIMARY = "external_primary"
    }
}