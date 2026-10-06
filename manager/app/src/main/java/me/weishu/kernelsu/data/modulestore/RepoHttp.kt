package me.weishu.kernelsu.data.modulestore

import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import me.weishu.kernelsu.ksuApp
import okhttp3.Request

/** Raised when a store request fails at the HTTP layer. */
class RepoHttpException(message: String) : IOException(message)

/**
 * Shared HTTP access for the module store.
 *
 * Responses are cached in memory for a short TTL, concurrent requests for the same URL are
 * de-duplicated, and transient failures are retried with exponential backoff. Client errors and
 * DNS failures fail immediately.
 */
object RepoHttp {

    private const val CACHE_TTL_MS = 5 * 60 * 1000L
    private const val MAX_ATTEMPTS = 3
    private const val INITIAL_BACKOFF_MS = 1000L

    private data class CacheEntry(val timestamp: Long, val body: String)

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val inFlight = ConcurrentHashMap<String, Deferred<Result<String>>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fetches [url] as text, serving a fresh cached body when available. */
    suspend fun get(url: String): Result<String> {
        if (!url.isHttpUrl()) {
            return Result.failure(RepoHttpException("unsupported url: $url"))
        }
        cache[url]?.let { entry ->
            if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
                return Result.success(entry.body)
            }
        }
        val deferred = inFlight.computeIfAbsent(url) {
            scope.async {
                try {
                    fetch(url).onSuccess { body ->
                        cache[url] = CacheEntry(System.currentTimeMillis(), body)
                    }
                } finally {
                    inFlight.remove(url)
                }
            }
        }
        return deferred.await()
    }

    private suspend fun fetch(url: String): Result<String> {
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                delay(INITIAL_BACKOFF_MS shl (attempt - 1))
            }
            try {
                val request = Request.Builder().url(url).build()
                ksuApp.okhttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return Result.success(response.body.string())
                    }
                    if (response.code in 400..499) {
                        return Result.failure(RepoHttpException("HTTP ${response.code} for $url"))
                    }
                    lastError = RepoHttpException("HTTP ${response.code} for $url")
                }
            } catch (e: UnknownHostException) {
                return Result.failure(e)
            } catch (e: IOException) {
                lastError = e
            }
        }
        return Result.failure(lastError ?: RepoHttpException("request failed: $url"))
    }
}
