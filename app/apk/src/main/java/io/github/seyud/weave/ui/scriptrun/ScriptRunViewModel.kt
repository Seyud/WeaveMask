package io.github.seyud.weave.ui.scriptrun

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.seyud.weave.core.registry.DownloadVerificationException
import io.github.seyud.weave.core.registry.ExpectedFile
import io.github.seyud.weave.core.script.ParamError
import io.github.seyud.weave.core.script.PreparedScript
import io.github.seyud.weave.core.script.ReleasedException
import io.github.seyud.weave.core.script.ScriptOutcome
import io.github.seyud.weave.core.script.ScriptPackageException
import io.github.seyud.weave.core.script.ScriptRepository
import io.github.seyud.weave.core.script.ScriptRunResult
import io.github.seyud.weave.core.script.UnverifiedScriptException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

/**
 * Lifecycle of the run page. A one-shot script is prepared and run at most once per page:
 * the extraction is deleted as root when the run ends, so there is nothing left to run again.
 */
enum class ScriptRunPhase {
    /** Verifying the registry download, validating and extracting it. */
    PREPARING,
    FAILED,
    READY,

    /** action.sh is executing; the page cannot be left or re-run. */
    RUNNING,
    FINISHED,
}

/**
 * Why a script could not be prepared, kept as a value rather than as a ready-made message:
 * the localized text is resolved by the screen, so a locale change does not need a re-prepare
 * (and the ViewModel stays free of Android resources).
 */
sealed interface ScriptRunFailure {
    /** The ZIP itself is unusable; [detail] carries the parser's message where one is shown. */
    data class InvalidPackage(
        val reason: ScriptPackageException.Reason,
        val detail: String? = null,
    ) : ScriptRunFailure

    /** The registry download is not (or no longer) a verified download of the store. */
    data object Unverified : ScriptRunFailure

    /** Size or SHA-256 of the download did not match the registry entry. */
    data object VerificationFailed : ScriptRunFailure

    /** Anything else (I/O, missing file, ...); [message] is shown verbatim. */
    data class Other(val message: String?) : ScriptRunFailure
}

/** One line of the output console; stderr is rendered in the error colour. */
@Immutable
data class ScriptConsoleLine(val text: String, val isError: Boolean)

@Immutable
data class ScriptRunUiState(
    /** Shown in the app bar until module.prop has been read. */
    val displayName: String = "",
    val phase: ScriptRunPhase = ScriptRunPhase.PREPARING,
    val prepared: PreparedScript? = null,
    val failure: ScriptRunFailure? = null,
    /** Current input per param key. */
    val values: Map<String, String> = emptyMap(),
    /** Invalid param key -> reason; empty means the form may be submitted. */
    val errors: Map<String, ParamError> = emptyMap(),
    val console: List<ScriptConsoleLine> = emptyList(),
    val result: ScriptRunResult? = null,
) {
    val canRun: Boolean get() = phase == ScriptRunPhase.READY && errors.isEmpty()
}

/**
 * Holds everything the run page needs: preparation of the registry download, the parameter
 * form state, and the streamed output of the one run.
 *
 * The page (a navigation entry) owns one instance, so [owner] is also the token that owns the
 * extraction in [ScriptRepository]: it is created once here and released when the page goes
 * away, which is what keeps an abandoned preparation from leaking files.
 */
class ScriptRunViewModel : ViewModel() {

    private val _state = MutableStateFlow(ScriptRunUiState())
    val state: StateFlow<ScriptRunUiState> = _state.asStateFlow()

    /** Extraction token of this page; a single value for the whole ViewModel lifetime. */
    private val owner: String = UUID.randomUUID().toString()

    /** A re-entered composition (rotation, recomposition) must not prepare a second copy. */
    private var started = false

    /**
     * Prepares the script ZIP of this page, once per ViewModel: [registryId] makes the download
     * re-verified against the registry entry, and [expected] carries the release's size and
     * SHA-256 when the store screen already downloaded it.
     */
    fun load(zipUri: Uri, registryId: String, displayName: String, expected: ExpectedFile? = null) {
        if (started) return
        started = true
        _state.value = ScriptRunUiState(displayName = displayName, phase = ScriptRunPhase.PREPARING)
        viewModelScope.launch {
            try {
                val prepared = ScriptRepository.prepare(zipUri, registryId, owner, expected)
                val values = prepared.params.defaults
                _state.update {
                    it.copy(
                        phase = ScriptRunPhase.READY,
                        prepared = prepared,
                        values = values,
                        // Defaults come from a validated params.json; validating anyway keeps the
                        // Run button honest if a future schema ever ships a bad default.
                        errors = prepared.params.validate(values),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReleasedException) {
                // The page was left while preparing: nobody is waiting for this result.
            } catch (e: Throwable) {
                Timber.w(e, "cannot prepare one-shot script")
                _state.update { it.copy(phase = ScriptRunPhase.FAILED, failure = failureOf(e)) }
            }
        }
    }

    fun setValue(key: String, value: String) {
        _state.update { s ->
            val prepared = s.prepared ?: return@update s
            if (s.phase != ScriptRunPhase.READY) return@update s
            val values = s.values + (key to value)
            // Re-validate the whole form, not just the edited field: the value may be echoed by
            // another param later, and the map is the single source of truth for `canRun`.
            s.copy(values = values, errors = prepared.params.validate(values))
        }
    }

    fun run() {
        val current = _state.value
        val prepared = current.prepared ?: return
        if (!current.canRun) return
        _state.update { it.copy(phase = ScriptRunPhase.RUNNING, console = emptyList(), result = null) }
        viewModelScope.launch {
            val result = try {
                ScriptRepository.run(
                    prepared = prepared,
                    values = current.values,
                    owner = owner,
                    onStdout = { appendConsole(it, isError = false) },
                    onStderr = { appendConsole(it, isError = true) },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // The repository records the run and classifies the exit code itself, so this is
                // only reached when the run could not start at all.
                Timber.w(e, "cannot run one-shot script")
                appendConsole(e.message ?: e.javaClass.simpleName, isError = true)
                ScriptRunResult(EXIT_NOT_STARTED, ScriptOutcome.FAILURE)
            }
            _state.update { it.copy(phase = ScriptRunPhase.FINISHED, result = result) }
        }
    }

    /**
     * The run callbacks come from the shell's reader threads, so every append goes through
     * [MutableStateFlow.update]. Only the tail is kept: a chatty script must not be able to grow
     * the console (and the saved instance state) without bound.
     */
    private fun appendConsole(line: String, isError: Boolean) {
        _state.update { s ->
            val next = s.console + ScriptConsoleLine(line, isError)
            s.copy(console = if (next.size > MAX_CONSOLE_LINES) next.takeLast(MAX_CONSOLE_LINES) else next)
        }
    }

    override fun onCleared() {
        // A running script owns its extraction: ScriptRepository.run removes it as root when the
        // script ends, even if this ViewModel is already gone.
        if (_state.value.phase != ScriptRunPhase.RUNNING) ScriptRepository.release(owner)
    }

    private fun failureOf(e: Throwable): ScriptRunFailure = when (e) {
        is UnverifiedScriptException -> ScriptRunFailure.Unverified
        is DownloadVerificationException -> ScriptRunFailure.VerificationFailed
        is ScriptPackageException -> ScriptRunFailure.InvalidPackage(e.reason, e.cause?.message ?: e.message)
        else -> ScriptRunFailure.Other(e.message ?: e.javaClass.simpleName)
    }

    private companion object {
        /** Console lines kept on screen (the newest ones). */
        const val MAX_CONSOLE_LINES = 2000

        /** Exit code reported when the script never got to run; same convention as a failed shell. */
        const val EXIT_NOT_STARTED = -1
    }
}
