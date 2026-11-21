package com.davide.seddio.easygallery.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.davide.seddio.easygallery.data.AlbumEvaluationStatus
import com.davide.seddio.easygallery.data.AlbumRule
import com.davide.seddio.easygallery.data.AlbumRuleClause
import com.davide.seddio.easygallery.data.AlbumRuleJson
import com.davide.seddio.easygallery.data.FACE_GROUPING_VERSION
import com.davide.seddio.easygallery.data.FACE_PIPELINE_VERSION
import com.davide.seddio.easygallery.data.FaceBox
import com.davide.seddio.easygallery.data.FaceDetection
import com.davide.seddio.easygallery.data.FaceEmbedding
import com.davide.seddio.easygallery.data.FaceGroup
import com.davide.seddio.easygallery.data.FaceIndexOutcome
import com.davide.seddio.easygallery.data.FaceIndexWrite
import com.davide.seddio.easygallery.data.InMemoryAlbumStore
import com.davide.seddio.easygallery.data.InMemoryMediaStoreVersionStore
import com.davide.seddio.easygallery.data.InMemoryPeopleAvailabilitySource
import com.davide.seddio.easygallery.data.MediaIndexDatabase
import com.davide.seddio.easygallery.data.MediaIndexEntry
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaStoreVersionProvider
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.RoomMediaIndexStore
import com.davide.seddio.easygallery.data.toMediaIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Plan 3.5, Task 3, exit criterion 5: over a real Room index database, a people album is re-evaluated by a face
 * write alone. No Tier A row is written after the first upsert, so only the face projection can move the album.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class PeopleAlbumReevaluationTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: MediaIndexDatabase
    private lateinit var indexStore: RoomMediaIndexStore
    private var viewModel: AlbumsViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MediaIndexDatabase::class.java
        ).allowMainThreadQueries().build()
        indexStore = RoomMediaIndexStore(database)
    }

    @After
    fun tearDown() {
        viewModel?.viewModelScope?.cancel()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `a grouping write alone moves a people album from waiting to complete and a regrouping write moves its members`() = runTest {
        val anchor = photo(1)
        val samePerson = photo(2)
        val someoneElse = photo(3)
        val albumStore = InMemoryAlbumStore()
        val albumId = albumStore.createAlbum("Her", 1_000L, peopleRuleText(anchor))
        // Anchored where the anchor photo has no face: it leaves WAITING_ON_INDEX only once the face rows are evaluated.
        val probeId = albumStore.createAlbum("Nobody", 1_000L, peopleRuleText(anchor, PROBE_BOX))
        val vm = createViewModel(albumStore)
        vm.setAllMedia(listOf(anchor, samePerson, someoneElse))
        vm.selectAlbum(albumId)

        val photos = listOf(anchor, samePerson, someoneElse)
        indexStore.upsert(photos.map { entryOf(it) })
        val ids = photos.map { indexFace(it) }

        awaitStatus(vm, probeId, AlbumEvaluationStatus.UNEVALUATABLE)
        awaitAlbum(vm, albumId, AlbumEvaluationStatus.WAITING_ON_INDEX, emptySet())

        indexStore.writeFaceGroups(
            listOf(FaceGroup(ids[0], listOf(ids[0], ids[1])), FaceGroup(ids[2], listOf(ids[2]))),
            FACE_PIPELINE_VERSION,
            FACE_GROUPING_VERSION
        ) { true }

        awaitAlbum(vm, albumId, AlbumEvaluationStatus.COMPLETE, setOf("1.jpg", "2.jpg"))

        indexStore.writeFaceGroups(
            listOf(FaceGroup(ids[0], listOf(ids[0])), FaceGroup(ids[1], listOf(ids[1], ids[2]))),
            FACE_PIPELINE_VERSION,
            FACE_GROUPING_VERSION
        ) { true }

        awaitAlbum(vm, albumId, AlbumEvaluationStatus.COMPLETE, setOf("1.jpg"))
    }

    @Test
    fun `deleting all face data turns a complete people album back to waiting`() = runTest {
        val anchor = photo(1)
        val albumStore = InMemoryAlbumStore()
        val albumId = albumStore.createAlbum("Her", 1_000L, peopleRuleText(anchor))
        val vm = createViewModel(albumStore)
        vm.setAllMedia(listOf(anchor))
        vm.selectAlbum(albumId)
        indexStore.upsert(listOf(entryOf(anchor)))
        val id = indexFace(anchor)
        indexStore.writeFaceGroups(listOf(FaceGroup(id, listOf(id))), FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION) { true }
        awaitAlbum(vm, albumId, AlbumEvaluationStatus.COMPLETE, setOf("1.jpg"))

        indexStore.deleteAllFaceData()

        awaitAlbum(vm, albumId, AlbumEvaluationStatus.WAITING_ON_INDEX, emptySet())
    }

    private fun createViewModel(albumStore: InMemoryAlbumStore): AlbumsViewModel {
        val application: Application = ApplicationProvider.getApplicationContext()
        return AlbumsViewModel(
            application = application,
            albumStore = albumStore,
            nowProvider = { 1_000L },
            mediaStoreVersionStore = InMemoryMediaStoreVersionStore(),
            mediaStoreVersionProvider = object : MediaStoreVersionProvider {
                override fun currentVersion(context: Context): String = "v1"
            },
            mediaIndexStore = indexStore,
            evaluationDispatcher = dispatcher,
            peopleAvailabilitySource = InMemoryPeopleAvailabilitySource()
        ).also { viewModel = it }
    }

    // Room delivers on its own threads, so the state is polled on a real clock rather than advanced.
    private suspend fun awaitAlbum(
        vm: AlbumsViewModel,
        albumId: Long,
        status: AlbumEvaluationStatus,
        memberNames: Set<String>
    ) {
        fun current(): Pair<AlbumEvaluationStatus?, Set<String>> =
            vm.albums.value.firstOrNull { it.id == albumId }?.status to
                vm.selectedAlbumMembers.value.map { it.mediaItem.name }.toSet()

        try {
            withContext(Dispatchers.Default) {
                withTimeout(AWAIT_TIMEOUT_MS) {
                    while (current() != status to memberNames) delay(20)
                }
            }
        } catch (_: TimeoutCancellationException) {
            fail("expected $status with $memberNames, last was ${current()}")
        }
    }

    private suspend fun awaitStatus(vm: AlbumsViewModel, albumId: Long, status: AlbumEvaluationStatus) {
        fun current() = vm.albums.value.firstOrNull { it.id == albumId }?.status

        try {
            withContext(Dispatchers.Default) {
                withTimeout(AWAIT_TIMEOUT_MS) {
                    while (current() != status) delay(20)
                }
            }
        } catch (_: TimeoutCancellationException) {
            fail("expected $status, last was ${current()}")
        }
    }

    private suspend fun indexFace(item: MediaItem): Long {
        val row = entryOf(item)
        indexStore.writeFaceIndexResults(
            listOf(
                FaceIndexWrite(
                    row.toMediaIdentity(),
                    FACE_PIPELINE_VERSION,
                    FaceIndexOutcome.INDEXED,
                    listOf(FaceDetection(FACE_BOX, FaceEmbedding(floatArrayOf(1f, 0f, 0f))))
                )
            ),
            faceDataEnabled = { true }
        )
        return indexStore.listFaces(row.mediaUri).single().id
    }

    private fun peopleRuleText(anchor: MediaItem, box: FaceBox = FACE_BOX): String = AlbumRuleJson.encode(
        AlbumRule.Evaluatable(
            listOf(AlbumRuleClause.People(anchor.toMediaIdentity(), box, FACE_PIPELINE_VERSION))
        )
    )

    private fun photo(id: Long): MediaItem = MediaItem(
        uri = Uri.parse("content://media/external/images/media/$id"),
        name = "$id.jpg",
        dateAdded = id,
        dateModified = id,
        size = id,
        type = MediaType.IMAGE,
        bucketName = "Camera",
        folderPath = "/storage/emulated/0/DCIM/Camera"
    )

    private fun entryOf(item: MediaItem) = MediaIndexEntry(
        mediaUri = item.uri.toString(),
        displayName = item.name,
        size = item.size,
        dateModified = item.dateModified,
        volumeName = "external_primary",
        generationAdded = null,
        generationModified = null,
        capturedDateTakenEpochMillis = null,
        capturedLatitude = null,
        capturedLongitude = null,
        capturedWidth = null,
        capturedHeight = null,
        derivedCountryCode = null,
        derivedRegionCode = null,
        derivedPlaceName = null
    )

    private companion object {
        val FACE_BOX = FaceBox(0.1f, 0.2f, 0.3f, 0.5f)
        val PROBE_BOX = FaceBox(0.6f, 0.6f, 0.9f, 0.9f)
        const val AWAIT_TIMEOUT_MS = 10_000L
    }
}
