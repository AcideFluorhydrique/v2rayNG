package com.v2ray.ang.handler

import android.os.SystemClock
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastInfo
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Decides how the app fetches what it needs for itself (subscriptions, geo files, the
 * per-app proxy list): through the core's local HTTP proxy, or directly.
 *
 * The app's own traffic never enters the VPN tunnel (`CoreVpnService` disallows its own
 * package to avoid a loop), so a request that does not name the local proxy port leaves
 * the device directly, from the user's real address.
 *
 * Upstream retries directly whenever the proxy attempt fails, without telling the user,
 * including in scheduled background subscription updates run while disconnected. With
 * [BuildConfig.STRICT_PROXY_FETCH] (the F-Droid build) a direct connection is never
 * silent:
 * - proxy listening: fetch through it only; on failure the user may choose a direct retry;
 * - proxy not listening, user-initiated: fetch directly, and tell the user here, so that
 *   every caller does, including those that discard the outcome;
 * - proxy not listening, background: skip, and try again next time.
 *
 * Without it (upstream's flavor), each caller keeps its own order of attempts unchanged.
 *
 * This is separate from [SettingsManager], which owns the proxy's settings, because it
 * owns a different decision: where a single app-initiated request is sent.
 */
object FetchRoutePolicy {

    enum class Route { PROXY, DIRECT }

    enum class Trigger {
        /** Scheduled or otherwise not started by the user. */
        BACKGROUND,

        /** Started by the user. */
        USER,

        /** The user chose to retry without the proxy after it failed. */
        USER_CONFIRMED_DIRECT,
    }

    sealed interface Outcome<out T> {
        /**
         * @param directWithoutProxy fetched directly under the strict policy, which the user
         * should be told about.
         */
        data class Fetched<T>(val value: T, val directWithoutProxy: Boolean) : Outcome<T>

        /** Nothing was sent: the proxy is not listening and the trigger does not allow direct. */
        data object SkippedNoProxy : Outcome<Nothing>

        /** @param canRetryDirect the attempt through the proxy failed under the strict policy. */
        data class Failed(val canRetryDirect: Boolean) : Outcome<Nothing>
    }

    /**
     * The routes to try, in order.
     *
     * @param legacyOrder the caller's order without the strict policy.
     */
    internal fun plan(
        strict: Boolean,
        legacyOrder: List<Route>,
        trigger: Trigger,
        proxyListening: Boolean,
    ): List<Route> {
        if (!strict) return legacyOrder
        return when (trigger) {
            Trigger.USER_CONFIRMED_DIRECT -> listOf(Route.DIRECT)
            Trigger.USER -> listOf(if (proxyListening) Route.PROXY else Route.DIRECT)
            Trigger.BACKGROUND -> if (proxyListening) listOf(Route.PROXY) else emptyList()
        }
    }

    /**
     * Fetches with [attempt], called with the HTTP proxy port to use, or 0 for a direct
     * connection; it returns null or throws on failure. Blocks: call off the main thread.
     */
    fun <T : Any> fetch(
        trigger: Trigger,
        legacyOrder: List<Route>,
        attempt: (httpPort: Int) -> T?,
    ): Outcome<T> {
        val httpPort = SettingsManager.getHttpPort()
        val outcome = fetch(
            strict = BuildConfig.STRICT_PROXY_FETCH,
            httpPort = httpPort,
            proxyListening = { isLocalProxyListening(httpPort) },
            trigger = trigger,
            legacyOrder = legacyOrder,
            attempt = attempt,
            onAttemptFailed = { route, e ->
                LogUtil.e(AppConfig.TAG, "Fetch failed: route=$route, trigger=$trigger", e)
            },
        )
        when {
            outcome == Outcome.SkippedNoProxy ->
                LogUtil.i(AppConfig.TAG, "Fetch skipped: local proxy not listening, trigger=$trigger")

            outcome is Outcome.Fetched && outcome.directWithoutProxy && trigger == Trigger.USER ->
                noticeDirect()
        }
        return outcome
    }

    @Volatile
    private var lastDirectNoticeAt = 0L

    /** Once per burst: updating every subscription would otherwise repeat it for each. */
    private fun noticeDirect() {
        val now = SystemClock.elapsedRealtime()
        if (lastDirectNoticeAt != 0L && now - lastDirectNoticeAt < DIRECT_NOTICE_INTERVAL_MS) return
        lastDirectNoticeAt = now
        AppLocaleManager.localizedContext(AngApplication.application)
            .toastInfo(R.string.fetch_direct_notice, long = true)
    }

    /** The decision without Android dependencies, for tests. */
    internal fun <T : Any> fetch(
        strict: Boolean,
        httpPort: Int,
        proxyListening: () -> Boolean,
        trigger: Trigger,
        legacyOrder: List<Route>,
        attempt: (httpPort: Int) -> T?,
        onAttemptFailed: (Route, Exception) -> Unit,
    ): Outcome<T> {
        // Only the strict policy needs to know; upstream's order does not depend on it.
        val listening = strict && proxyListening()
        val routes = plan(strict, legacyOrder, trigger, listening)
        if (routes.isEmpty()) return Outcome.SkippedNoProxy
        for (route in routes) {
            val port = if (route == Route.PROXY) httpPort else 0
            val value = try {
                attempt(port)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onAttemptFailed(route, e)
                null
            }
            if (value != null) {
                return Outcome.Fetched(value, directWithoutProxy = strict && route == Route.DIRECT)
            }
        }
        return Outcome.Failed(canRetryDirect = strict && routes.last() == Route.PROXY)
    }

    /**
     * Whether something accepts connections on the local proxy port, which is what matters
     * here: the UI process must not decide from cached state whether the core runs.
     */
    private fun isLocalProxyListening(port: Int): Boolean {
        if (port <= 0) return false
        return try {
            Socket().use { it.connect(InetSocketAddress(AppConfig.LOOPBACK, port), PROBE_TIMEOUT_MS) }
            true
        } catch (e: IOException) {
            false
        }
    }

    private const val PROBE_TIMEOUT_MS = 500
    private const val DIRECT_NOTICE_INTERVAL_MS = 10_000L
}
