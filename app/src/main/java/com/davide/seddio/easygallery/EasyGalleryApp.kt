package com.davide.seddio.easygallery

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.video.VideoFrameDecoder

class EasyGalleryApp : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
                add(AnimatedImageDecoder.Factory())
            }
            .build()
    }
}
