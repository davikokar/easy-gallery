package com.davide.seddio.easygallery.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

// Pure Kotlin, no android.* imports. Face ids, embeddings and groups never reach a log, a message or a toString
// (ADR-0024 §6): the one line this pass writes is a fixed word.

/** Grouping starts no later than this after the worker began; the worker's own stop comes at about ten minutes. */
const val FACE_GROUPING_START_DEADLINE_MILLIS = 8L * 60L * 1000L

const val FACE_GROUPING_PAGE_SIZE = 500

/** How a grouping run ended, in the words its one log line uses. Nothing is written unless it is [GROUPED]. */
enum class FaceGroupingStop { GROUPED, NOT_NEEDED, SETTING_OFF, PAST_DEADLINE }

/**
 * Groups the faces at [FACE_PIPELINE_VERSION] when grouping is needed (plan 3.4, *When grouping is needed*): reads them
 * paged by id outside any transaction, runs [groupFaces], and writes every group at once through
 * [MediaIndexStore.writeFaceGroups], which reads the setting inside its transaction. It checks cancellation and the
 * setting between phases and holds nothing once it returns; a stop writes nothing, and the next run repeats the work.
 * The caller holds the Tier C lock, has checked capability and the disclosure, and decides after which pass stops to
 * run it; this class takes no lock of its own. A thrown error propagates, with no log line, for the caller to turn into
 * its one fixed failure message.
 */
class FaceGroupingPass(
    private val store: MediaIndexStore,
    private val settingStore: FaceDataSettingStore,
    private val log: FaceIndexLog,
    private val clock: () -> Long = System::currentTimeMillis,
    private val startDeadlineMillis: Long = FACE_GROUPING_START_DEADLINE_MILLIS,
    private val pageSize: Int = FACE_GROUPING_PAGE_SIZE
) {

    init {
        require(pageSize > 0) { "pageSize must be > 0" }
    }

    /** [workStartedAtMillis] is the worker's start, on [clock]'s time base: the start deadline counts from it. */
    suspend fun run(workStartedAtMillis: Long): FaceGroupingStop {
        val stop = group(workStartedAtMillis)
        log.line(FaceIndexLogLines.groupingFinished(stop))
        return stop
    }

    private suspend fun group(workStartedAtMillis: Long): FaceGroupingStop {
        currentCoroutineContext().ensureActive()
        if (!settingStore.isEnabled()) return FaceGroupingStop.SETTING_OFF
        if (clock() - workStartedAtMillis >= startDeadlineMillis) return FaceGroupingStop.PAST_DEADLINE
        if (!store.isFaceGroupingNeeded(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION)) return FaceGroupingStop.NOT_NEEDED

        val faces = ArrayList<FaceGroupingInput>()
        var cursor: Long? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!settingStore.isEnabled()) return FaceGroupingStop.SETTING_OFF
            val page = store.listFacesForGrouping(FACE_PIPELINE_VERSION, cursor, pageSize)
            faces.addAll(page)
            if (page.size < pageSize) break
            cursor = page.last().faceId
        }
        if (faces.isEmpty()) return FaceGroupingStop.NOT_NEEDED

        val result = groupFaces(faces, settingStore::isEnabled) ?: return FaceGroupingStop.SETTING_OFF

        currentCoroutineContext().ensureActive()
        val written = store.writeFaceGroups(
            result.groups,
            FACE_PIPELINE_VERSION,
            FACE_GROUPING_VERSION,
            settingStore::isEnabled
        )
        // Nothing written with the setting off is the store's backstop having fired between the last check and the write.
        return if (written == 0 && !settingStore.isEnabled()) FaceGroupingStop.SETTING_OFF else FaceGroupingStop.GROUPED
    }
}
