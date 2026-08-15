package dev.mjablonski.rewordme.android.arch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The MVI contract every feature follows: the screen renders exactly one
 * [MviState], sends user actions as [MviIntent]s, and reacts to one-shot
 * [MviEffect]s (navigation, toasts, finishing an activity) that must not be
 * replayed on rotation the way state is.
 */
interface MviState
interface MviIntent
interface MviEffect

/**
 * Base for feature view models. State is a single StateFlow the UI collects;
 * effects go through a buffered channel so none is lost while the UI is
 * between collectors (e.g. during a configuration change).
 */
abstract class MviViewModel<S : MviState, I : MviIntent, E : MviEffect>(
    initialState: S
) : ViewModel() {

    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<S> = mutableState.asStateFlow()

    private val effectChannel = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = effectChannel.receiveAsFlow()

    /** Single entry point for the UI; the view model routes from here. */
    abstract fun onIntent(intent: I)

    protected val currentState: S get() = mutableState.value

    /** Atomically reduce the current state into the next one. */
    protected fun setState(reduce: S.() -> S) {
        mutableState.update(reduce)
    }

    protected fun sendEffect(effect: E) {
        // trySend never suspends; BUFFERED keeps effects ordered and intact
        // for the next collector instead of dropping them.
        if (effectChannel.trySend(effect).isFailure) {
            viewModelScope.launch { effectChannel.send(effect) }
        }
    }
}
