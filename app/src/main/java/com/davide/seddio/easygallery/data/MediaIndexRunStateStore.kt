package com.davide.seddio.easygallery.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MediaIndexRunOutcome {
    SUCCESS,
    FAILURE,
    CANCELLED
}

data class MediaIndexRunState(
    val isRunning: Boolean,
    val selectedRows: Int,
    val processedRows: Int,
    val indexedRows: Int,
    val skippedRows: Int,
    val deletedRows: Int,
    val healedRows: Int,
    val lastRunStartedAtMillis: Long?,
    val lastRunFinishedAtMillis: Long?,
    val lastSuccessfulRunFinishedAtMillis: Long?,
    val lastRunOutcome: MediaIndexRunOutcome?,
    val lastRunErrorMessage: String?
) {
    companion object {
        fun initial(): MediaIndexRunState {
            return MediaIndexRunState(
                isRunning = false,
                selectedRows = 0,
                processedRows = 0,
                indexedRows = 0,
                skippedRows = 0,
                deletedRows = 0,
                healedRows = 0,
                lastRunStartedAtMillis = null,
                lastRunFinishedAtMillis = null,
                lastSuccessfulRunFinishedAtMillis = null,
                lastRunOutcome = null,
                lastRunErrorMessage = null
            )
        }
    }
}

interface MediaIndexRunStateStore {
    val state: StateFlow<MediaIndexRunState>

    fun markStarted(startedAtMillis: Long)

    fun updateProgress(
        selectedRows: Int,
        processedRows: Int,
        indexedRows: Int,
        skippedRows: Int
    )

    fun markSucceeded(finishedAtMillis: Long, result: MediaIndexTierABatchResult)

    fun markFailed(finishedAtMillis: Long)

    fun markCancelled(finishedAtMillis: Long)
}

class InMemoryMediaIndexRunStateStore(
    initial: MediaIndexRunState = MediaIndexRunState.initial()
) : MediaIndexRunStateStore {

    private val mutableState = MutableStateFlow(initial)

    override val state: StateFlow<MediaIndexRunState> = mutableState.asStateFlow()

    override fun markStarted(startedAtMillis: Long) {
        mutableState.value = mutableState.value.copy(
            isRunning = true,
            selectedRows = 0,
            processedRows = 0,
            indexedRows = 0,
            skippedRows = 0,
            deletedRows = 0,
            healedRows = 0,
            lastRunStartedAtMillis = startedAtMillis,
            lastRunFinishedAtMillis = null,
            lastRunOutcome = null,
            lastRunErrorMessage = null
        )
    }

    override fun updateProgress(
        selectedRows: Int,
        processedRows: Int,
        indexedRows: Int,
        skippedRows: Int
    ) {
        mutableState.value = mutableState.value.copy(
            selectedRows = selectedRows,
            processedRows = processedRows,
            indexedRows = indexedRows,
            skippedRows = skippedRows
        )
    }

    override fun markSucceeded(finishedAtMillis: Long, result: MediaIndexTierABatchResult) {
        mutableState.value = mutableState.value.copy(
            isRunning = false,
            selectedRows = result.selectedRows,
            processedRows = result.indexedRows + result.skippedRows,
            indexedRows = result.indexedRows,
            skippedRows = result.skippedRows,
            deletedRows = result.deletedRows,
            healedRows = result.healedRows,
            lastRunFinishedAtMillis = finishedAtMillis,
            lastSuccessfulRunFinishedAtMillis = finishedAtMillis,
            lastRunOutcome = MediaIndexRunOutcome.SUCCESS,
            lastRunErrorMessage = null
        )
    }

    override fun markFailed(finishedAtMillis: Long) {
        mutableState.value = mutableState.value.copy(
            isRunning = false,
            lastRunFinishedAtMillis = finishedAtMillis,
            lastRunOutcome = MediaIndexRunOutcome.FAILURE,
            lastRunErrorMessage = null
        )
    }

    override fun markCancelled(finishedAtMillis: Long) {
        mutableState.value = mutableState.value.copy(
            isRunning = false,
            lastRunFinishedAtMillis = finishedAtMillis,
            lastRunOutcome = MediaIndexRunOutcome.CANCELLED,
            lastRunErrorMessage = null
        )
    }
}

class SharedPreferencesMediaIndexRunStateStore(context: Context) : MediaIndexRunStateStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mutableState = MutableStateFlow(loadFromPrefs())

    override val state: StateFlow<MediaIndexRunState> = mutableState.asStateFlow()

    override fun markStarted(startedAtMillis: Long) {
        update {
            it.copy(
                isRunning = true,
                selectedRows = 0,
                processedRows = 0,
                indexedRows = 0,
                skippedRows = 0,
                deletedRows = 0,
                healedRows = 0,
                lastRunStartedAtMillis = startedAtMillis,
                lastRunFinishedAtMillis = null,
                lastRunOutcome = null,
                lastRunErrorMessage = null
            )
        }
    }

    override fun updateProgress(
        selectedRows: Int,
        processedRows: Int,
        indexedRows: Int,
        skippedRows: Int
    ) {
        update {
            it.copy(
                selectedRows = selectedRows,
                processedRows = processedRows,
                indexedRows = indexedRows,
                skippedRows = skippedRows
            )
        }
    }

    override fun markSucceeded(finishedAtMillis: Long, result: MediaIndexTierABatchResult) {
        update {
            it.copy(
                isRunning = false,
                selectedRows = result.selectedRows,
                processedRows = result.indexedRows + result.skippedRows,
                indexedRows = result.indexedRows,
                skippedRows = result.skippedRows,
                deletedRows = result.deletedRows,
                healedRows = result.healedRows,
                lastRunFinishedAtMillis = finishedAtMillis,
                lastSuccessfulRunFinishedAtMillis = finishedAtMillis,
                lastRunOutcome = MediaIndexRunOutcome.SUCCESS,
                lastRunErrorMessage = null
            )
        }
    }

    override fun markFailed(finishedAtMillis: Long) {
        update {
            it.copy(
                isRunning = false,
                lastRunFinishedAtMillis = finishedAtMillis,
                lastRunOutcome = MediaIndexRunOutcome.FAILURE,
                lastRunErrorMessage = null
            )
        }
    }

    override fun markCancelled(finishedAtMillis: Long) {
        update {
            it.copy(
                isRunning = false,
                lastRunFinishedAtMillis = finishedAtMillis,
                lastRunOutcome = MediaIndexRunOutcome.CANCELLED,
                lastRunErrorMessage = null
            )
        }
    }

    private fun update(transform: (MediaIndexRunState) -> MediaIndexRunState) {
        val updated = transform(mutableState.value)
        mutableState.value = updated
        saveToPrefs(updated)
    }

    private fun loadFromPrefs(): MediaIndexRunState {
        val startedAt = readLongOrNull(KEY_LAST_RUN_STARTED_AT)
        val finishedAt = readLongOrNull(KEY_LAST_RUN_FINISHED_AT)
        val lastSuccessAt = readLongOrNull(KEY_LAST_SUCCESSFUL_RUN_FINISHED_AT)
        val outcome = prefs.getString(KEY_LAST_RUN_OUTCOME, null)
            ?.let { value -> runCatching { MediaIndexRunOutcome.valueOf(value) }.getOrNull() }

        return MediaIndexRunState(
            isRunning = false,
            selectedRows = prefs.getInt(KEY_SELECTED_ROWS, 0),
            processedRows = prefs.getInt(KEY_PROCESSED_ROWS, 0),
            indexedRows = prefs.getInt(KEY_INDEXED_ROWS, 0),
            skippedRows = prefs.getInt(KEY_SKIPPED_ROWS, 0),
            deletedRows = prefs.getInt(KEY_DELETED_ROWS, 0),
            healedRows = prefs.getInt(KEY_HEALED_ROWS, 0),
            lastRunStartedAtMillis = startedAt,
            lastRunFinishedAtMillis = finishedAt,
            lastSuccessfulRunFinishedAtMillis = lastSuccessAt,
            lastRunOutcome = outcome,
            lastRunErrorMessage = null
        )
    }

    private fun saveToPrefs(state: MediaIndexRunState) {
        prefs.edit()
            .putInt(KEY_SELECTED_ROWS, state.selectedRows)
            .putInt(KEY_PROCESSED_ROWS, state.processedRows)
            .putInt(KEY_INDEXED_ROWS, state.indexedRows)
            .putInt(KEY_SKIPPED_ROWS, state.skippedRows)
            .putInt(KEY_DELETED_ROWS, state.deletedRows)
            .putInt(KEY_HEALED_ROWS, state.healedRows)
            .putString(KEY_LAST_RUN_OUTCOME, state.lastRunOutcome?.name)
            .apply {
                putLongOrRemove(KEY_LAST_RUN_STARTED_AT, state.lastRunStartedAtMillis)
                putLongOrRemove(KEY_LAST_RUN_FINISHED_AT, state.lastRunFinishedAtMillis)
                putLongOrRemove(KEY_LAST_SUCCESSFUL_RUN_FINISHED_AT, state.lastSuccessfulRunFinishedAtMillis)
                remove(KEY_IS_RUNNING)
                remove(KEY_LAST_RUN_ERROR)
            }
            .apply()
    }

    private fun SharedPreferences.Editor.putLongOrRemove(key: String, value: Long?): SharedPreferences.Editor {
        return if (value == null) remove(key) else putLong(key, value)
    }

    private fun readLongOrNull(key: String): Long? {
        return if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    }

    private companion object {
        const val PREFS_NAME = "media_index_run_state"
        const val KEY_IS_RUNNING = "is_running"
        const val KEY_SELECTED_ROWS = "selected_rows"
        const val KEY_PROCESSED_ROWS = "processed_rows"
        const val KEY_INDEXED_ROWS = "indexed_rows"
        const val KEY_SKIPPED_ROWS = "skipped_rows"
        const val KEY_DELETED_ROWS = "deleted_rows"
        const val KEY_HEALED_ROWS = "healed_rows"
        const val KEY_LAST_RUN_STARTED_AT = "last_run_started_at"
        const val KEY_LAST_RUN_FINISHED_AT = "last_run_finished_at"
        const val KEY_LAST_SUCCESSFUL_RUN_FINISHED_AT = "last_successful_run_finished_at"
        const val KEY_LAST_RUN_OUTCOME = "last_run_outcome"
        const val KEY_LAST_RUN_ERROR = "last_run_error"
    }
}
