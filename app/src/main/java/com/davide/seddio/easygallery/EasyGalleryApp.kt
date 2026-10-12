package com.davide.seddio.easygallery

import android.app.Application
import android.content.Context
import com.davide.seddio.easygallery.data.EasyGalleryDatabase
import com.davide.seddio.easygallery.data.AndroidMediaIndexTierAExtractor
import com.davide.seddio.easygallery.data.AndroidMediaStoreRowDeltaProvider
import com.davide.seddio.easygallery.data.AndroidMediaStoreVolumeSnapshotProvider
import com.davide.seddio.easygallery.data.MediaIndexDatabase
import com.davide.seddio.easygallery.data.MediaIndexDeltaDetector
import com.davide.seddio.easygallery.data.MediaIndexReconciler
import com.davide.seddio.easygallery.data.MediaIndexRunStateStore
import com.davide.seddio.easygallery.data.MediaIndexStore
import com.davide.seddio.easygallery.data.MediaIndexTierABatchIndexer
import com.davide.seddio.easygallery.data.MediaIndexWatermarkStore
import com.davide.seddio.easygallery.data.MediaStoreDataSource
import com.davide.seddio.easygallery.data.RoomMediaIndexStore
import com.davide.seddio.easygallery.data.RoomMediaIndexWatermarkStore
import com.davide.seddio.easygallery.data.SharedPreferencesMediaIndexRunStateStore
import com.davide.seddio.easygallery.data.WorkManagerMediaIndexingWorkScheduler
import com.davide.seddio.easygallery.data.MediaIndexingWorkScheduler
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.video.VideoFrameDecoder

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
