package com.davide.seddio.easygallery

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.davide.seddio.easygallery.data.EasyGalleryDatabase
import com.davide.seddio.easygallery.data.AndroidFaceIndexLog
import com.davide.seddio.easygallery.data.AndroidMediaIndexTierAExtractor
import com.davide.seddio.easygallery.data.AndroidMediaStoreRowDeltaProvider
import com.davide.seddio.easygallery.data.AndroidMediaStoreVolumeSnapshotProvider
import com.davide.seddio.easygallery.data.FACE_PIPELINE_VERSION
import com.davide.seddio.easygallery.data.FaceCapabilityChecker
import com.davide.seddio.easygallery.data.FaceCapabilityKey
import com.davide.seddio.easygallery.data.FaceDataSettingStore
import com.davide.seddio.easygallery.data.FaceDataStartupDeletion
import com.davide.seddio.easygallery.data.FaceDeviceStateStore
import com.davide.seddio.easygallery.data.FaceGroupingPass
import com.davide.seddio.easygallery.data.FaceIndexLog
import com.davide.seddio.easygallery.data.FaceIndexPass
import com.davide.seddio.easygallery.data.FacePipeline
import com.davide.seddio.easygallery.data.MediaIndexDatabase
import com.davide.seddio.easygallery.data.GazetteerAssets
import com.davide.seddio.easygallery.data.MediaIndexDeltaDetector
import com.davide.seddio.easygallery.data.MediaIndexPlaceResolver
import com.davide.seddio.easygallery.data.MediaIndexReconciler
import com.davide.seddio.easygallery.data.MediaIndexRunStateStore
import com.davide.seddio.easygallery.data.MediaIndexStore
import com.davide.seddio.easygallery.data.MediaIndexTierABatchIndexer
import com.davide.seddio.easygallery.data.MediaIndexWatermarkStore
import com.davide.seddio.easygallery.data.MediaStoreDataSource
import com.davide.seddio.easygallery.data.OpenCvFacePipeline
import com.davide.seddio.easygallery.data.PeopleAvailabilitySource
import com.davide.seddio.easygallery.data.RoomMediaIndexStore
import com.davide.seddio.easygallery.data.RoomMediaIndexWatermarkStore
import com.davide.seddio.easygallery.data.SharedPreferencesFaceDataSettingStore
import com.davide.seddio.easygallery.data.SharedPreferencesFaceDeviceStateStore
import com.davide.seddio.easygallery.data.SharedPreferencesMediaIndexRunStateStore
import com.davide.seddio.easygallery.data.StoredPeopleAvailabilitySource
import com.davide.seddio.easygallery.data.WorkManagerMediaIndexingWorkScheduler
import com.davide.seddio.easygallery.data.MediaIndexingWorkScheduler
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.video.VideoFrameDecoder
import kotlinx.coroutines.sync.Mutex

class EasyGalleryApp : Application(), SingletonImageLoader.Factory {
    val database: EasyGalleryDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EasyGalleryDatabase.create(this)
    }

    val mediaIndexDatabase: MediaIndexDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MediaIndexDatabase.create(this)
    }

    val mediaIndexStore: MediaIndexStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomMediaIndexStore(mediaIndexDatabase)
    }

    val mediaIndexWatermarkStore: MediaIndexWatermarkStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomMediaIndexWatermarkStore(mediaIndexDatabase.mediaIndexDao())
    }

    val mediaIndexRunStateStore: MediaIndexRunStateStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SharedPreferencesMediaIndexRunStateStore(this)
    }

    val mediaIndexingWorkScheduler: MediaIndexingWorkScheduler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WorkManagerMediaIndexingWorkScheduler(this)
    }

    val faceDataSettingStore: FaceDataSettingStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SharedPreferencesFaceDataSettingStore(this)
    }

    val faceDeviceStateStore: FaceDeviceStateStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SharedPreferencesFaceDeviceStateStore(this)
    }

    // Held by the Tier C pass for its whole run; the face-data switch takes it after persisting off (ADR-0024 §3).
    val faceIndexLock: Mutex = Mutex()

    val faceIndexLog: FaceIndexLog = AndroidFaceIndexLog

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
                add(AnimatedImageDecoder.Factory())
            }
            .build()
    }

    fun createMediaIndexPlaceResolver(): MediaIndexPlaceResolver {
        return MediaIndexPlaceResolver(
            store = mediaIndexStore,
            gazetteerLoader = { GazetteerAssets.load(this) }
        )
    }

    fun createFaceDataStartupDeletion(): FaceDataStartupDeletion {
        return FaceDataStartupDeletion(mediaIndexStore, faceDataSettingStore, faceIndexLog)
    }

    // The one place the key is built, so the checker's record and the availability's read can never disagree.
    fun faceCapabilityKey(): FaceCapabilityKey {
        val versionCode = PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
        return FaceCapabilityKey(versionCode, FACE_PIPELINE_VERSION, Build.FINGERPRINT)
    }

    fun createFaceCapabilityChecker(): FaceCapabilityChecker {
        return FaceCapabilityChecker(
            store = faceDeviceStateStore,
            key = faceCapabilityKey(),
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            pipelineLoader = ::loadFacePipeline
        )
    }

    fun createPeopleAvailabilitySource(): PeopleAvailabilitySource {
        return StoredPeopleAvailabilitySource(faceDataSettingStore, faceDeviceStateStore, faceCapabilityKey().asStoredKey())
    }

    fun createFaceIndexPass(): FaceIndexPass {
        return FaceIndexPass(
            store = mediaIndexStore,
            mediaRepository = MediaStoreDataSource(this),
            settingStore = faceDataSettingStore,
            pipelineLoader = ::loadFacePipeline,
            log = faceIndexLog
        )
    }

    fun createFaceGroupingPass(): FaceGroupingPass {
        return FaceGroupingPass(store = mediaIndexStore, settingStore = faceDataSettingStore, log = faceIndexLog)
    }

    private fun loadFacePipeline(): FacePipeline = OpenCvFacePipeline.load(this)

    fun createMediaIndexTierABatchIndexer(): MediaIndexTierABatchIndexer {
        val mediaRepository = MediaStoreDataSource(this)
        val detector = MediaIndexDeltaDetector(
            watermarkStore = mediaIndexWatermarkStore,
            snapshotProvider = AndroidMediaStoreVolumeSnapshotProvider(this)
        )

        return MediaIndexTierABatchIndexer(
            mediaRepository = mediaRepository,
            mediaIndexStore = mediaIndexStore,
            deltaDetector = detector,
            rowDeltaProvider = AndroidMediaStoreRowDeltaProvider(this),
            reconciler = MediaIndexReconciler(mediaIndexStore),
            extractor = AndroidMediaIndexTierAExtractor(this)
        )
    }
}
