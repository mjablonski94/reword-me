package dev.mjablonski.rewordme.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One domain operation with a single entry point. `action` holds the logic;
 * callers either await `safeAction` for a Result or fire `invoke` on a scope.
 * The dispatcher is injected because commonMain has no Dispatchers.IO -
 * composition roots pass the platform's IO dispatcher.
 */
abstract class UseCase<Type, in Params>(
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default
) where Type : Any {

    protected abstract suspend fun action(params: Params): Type

    /**
     * Cancellation is rethrown, never wrapped: a cancelled caller must unwind,
     * not act on a phantom failure Result (or render "job was cancelled" as an
     * error to the user).
     */
    suspend fun safeAction(params: Params): Result<Type> = try {
        Result.success(withContext(coroutineDispatcher) { action(params) })
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        Result.failure(failure)
    }.also { result -> doOnResult(params, result) }

    open operator fun invoke(
        coroutineScope: CoroutineScope,
        params: Params,
        onResult: (Result<Type>) -> Unit = {}
    ): Job = coroutineScope.launch {
        onResult(safeAction(params))
    }

    /** Runs extra work (caching, logging) after the fact; never alters the result. */
    protected open suspend fun doOnResult(params: Params, result: Result<Type>) = Unit
}
