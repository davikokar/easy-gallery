package com.davide.seddio.easygallery.ui

import com.davide.seddio.easygallery.data.MediaIndexStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MediaIndexDateTakenState(
    mediaIndexStore: MediaIndexStore,
    scope: CoroutineScope
) {

    val indexedDateTakenByMediaUri: StateFlow<Map<String, Long?>> = mediaIndexStore
        .observeDateTakenProjection()
        .stateIn(scope, SharingStarted.Lazily, emptyMap())
}
