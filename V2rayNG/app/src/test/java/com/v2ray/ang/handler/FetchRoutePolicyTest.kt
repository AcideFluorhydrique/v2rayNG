package com.v2ray.ang.handler

import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.handler.FetchRoutePolicy.Outcome
import com.v2ray.ang.handler.FetchRoutePolicy.Route.DIRECT
import com.v2ray.ang.handler.FetchRoutePolicy.Route.PROXY
import com.v2ray.ang.handler.FetchRoutePolicy.Trigger
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchRoutePolicyTest {

    private val proxyThenDirect = listOf(PROXY, DIRECT)
    private val directThenProxy = listOf(DIRECT, PROXY)

    // ---------- plan ----------

    @Test
    fun withoutTheStrictPolicyEveryCallerKeepsItsOwnOrder() {
        for (trigger in Trigger.entries) {
            for (listening in listOf(true, false)) {
                assertEquals(proxyThenDirect, FetchRoutePolicy.plan(false, proxyThenDirect, trigger, listening))
                assertEquals(directThenProxy, FetchRoutePolicy.plan(false, directThenProxy, trigger, listening))
            }
        }
    }

    @Test
    fun strictUserFetchUsesOnlyTheProxyWhileItListens() {
        assertEquals(listOf(PROXY), FetchRoutePolicy.plan(true, directThenProxy, Trigger.USER, true))
    }

    @Test
    fun strictUserFetchGoesDirectOnlyWhenTheProxyIsNotListening() {
        assertEquals(listOf(DIRECT), FetchRoutePolicy.plan(true, proxyThenDirect, Trigger.USER, false))
    }

    @Test
    fun strictBackgroundFetchNeverGoesDirect() {
        assertEquals(listOf(PROXY), FetchRoutePolicy.plan(true, proxyThenDirect, Trigger.BACKGROUND, true))
        assertEquals(emptyList<FetchRoutePolicy.Route>(), FetchRoutePolicy.plan(true, proxyThenDirect, Trigger.BACKGROUND, false))
    }

    @Test
    fun confirmedDirectRetryGoesDirectEvenWhileTheProxyListens() {
        assertEquals(listOf(DIRECT), FetchRoutePolicy.plan(true, proxyThenDirect, Trigger.USER_CONFIRMED_DIRECT, true))
        assertEquals(listOf(DIRECT), FetchRoutePolicy.plan(true, proxyThenDirect, Trigger.USER_CONFIRMED_DIRECT, false))
    }

    // ---------- fetch ----------

    private class Recorder(private val results: Map<Int, Any?>) {
        val ports = mutableListOf<Int>()
        val failures = mutableListOf<FetchRoutePolicy.Route>()

        fun attempt(port: Int): String? {
            ports += port
            return when (val result = results[port]) {
                is Exception -> throw result
                else -> result as String?
            }
        }
    }

    private fun fetch(
        strict: Boolean,
        listening: Boolean,
        trigger: Trigger,
        legacyOrder: List<FetchRoutePolicy.Route>,
        recorder: Recorder,
    ): Outcome<String> = FetchRoutePolicy.fetch(
        strict = strict,
        httpPort = PORT,
        proxyListening = { listening },
        trigger = trigger,
        legacyOrder = legacyOrder,
        attempt = recorder::attempt,
        onAttemptFailed = { route, _ -> recorder.failures += route },
    )

    @Test
    fun strictBackgroundFetchSendsNothingWhenTheProxyIsNotListening() {
        val recorder = Recorder(mapOf(0 to "direct"))
        val outcome = fetch(true, false, Trigger.BACKGROUND, proxyThenDirect, recorder)
        assertEquals(Outcome.SkippedNoProxy, outcome)
        assertEquals(emptyList<Int>(), recorder.ports)
    }

    @Test
    fun strictFetchThroughTheProxyIsNotReportedAsDirect() {
        val recorder = Recorder(mapOf(PORT to "proxied"))
        val outcome = fetch(true, true, Trigger.USER, proxyThenDirect, recorder)
        assertEquals(Outcome.Fetched("proxied", directWithoutProxy = false), outcome)
        assertEquals(listOf(PORT), recorder.ports)
    }

    @Test
    fun strictProxyFailureOffersADirectRetryInsteadOfTakingIt() {
        val recorder = Recorder(mapOf(PORT to null, 0 to "direct"))
        val outcome = fetch(true, true, Trigger.USER, proxyThenDirect, recorder)
        assertEquals(Outcome.Failed(canRetryDirect = true), outcome)
        assertEquals(listOf(PORT), recorder.ports)
    }

    @Test
    fun strictProxyExceptionIsAFailureAndIsReported() {
        val recorder = Recorder(mapOf(PORT to IllegalStateException("proxy down"), 0 to "direct"))
        val outcome = fetch(true, true, Trigger.BACKGROUND, proxyThenDirect, recorder)
        assertEquals(Outcome.Failed(canRetryDirect = true), outcome)
        assertEquals(listOf(PROXY), recorder.failures)
        assertEquals(listOf(PORT), recorder.ports)
    }

    @Test
    fun strictUserFetchWithoutTheProxyGoesDirectAndSaysSo() {
        val recorder = Recorder(mapOf(0 to "direct"))
        val outcome = fetch(true, false, Trigger.USER, proxyThenDirect, recorder)
        assertEquals(Outcome.Fetched("direct", directWithoutProxy = true), outcome)
        assertEquals(listOf(0), recorder.ports)
    }

    @Test
    fun strictDirectFailureOffersNoFurtherDirectRetry() {
        val recorder = Recorder(mapOf(0 to null))
        val outcome = fetch(true, false, Trigger.USER, proxyThenDirect, recorder)
        assertEquals(Outcome.Failed(canRetryDirect = false), outcome)
    }

    @Test
    fun confirmedDirectRetryUsesOnlyTheDirectRoute() {
        val recorder = Recorder(mapOf(PORT to "proxied", 0 to "direct"))
        val outcome = fetch(true, true, Trigger.USER_CONFIRMED_DIRECT, proxyThenDirect, recorder)
        assertEquals(Outcome.Fetched("direct", directWithoutProxy = true), outcome)
        assertEquals(listOf(0), recorder.ports)
    }

    @Test
    fun withoutTheStrictPolicyAProxyFailureFallsBackDirectlyAsUpstreamDoes() {
        val recorder = Recorder(mapOf(PORT to null, 0 to "direct"))
        val outcome = fetch(false, false, Trigger.BACKGROUND, proxyThenDirect, recorder)
        assertEquals(Outcome.Fetched("direct", directWithoutProxy = false), outcome)
        assertEquals(listOf(PORT, 0), recorder.ports)
    }

    @Test
    fun withoutTheStrictPolicyTheProxyIsNotProbed() {
        var probed = false
        FetchRoutePolicy.fetch(
            strict = false,
            httpPort = PORT,
            proxyListening = { probed = true; true },
            trigger = Trigger.USER,
            legacyOrder = proxyThenDirect,
            attempt = { "ok" },
            onAttemptFailed = { _, _ -> },
        )
        assertFalse(probed)
    }

    @Test
    fun withoutTheStrictPolicyFailuresNeverOfferADirectRetry() {
        val recorder = Recorder(mapOf(PORT to null, 0 to null))
        assertEquals(Outcome.Failed(canRetryDirect = false), fetch(false, true, Trigger.USER, directThenProxy, recorder))
        assertEquals(listOf(0, PORT), recorder.ports)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotSwallowed() {
        fetch(true, true, Trigger.USER, proxyThenDirect, Recorder(mapOf(PORT to CancellationException("cancelled"))))
    }

    // ---------- wiring ----------

    @Test
    fun strictPolicyIsOnExactlyInTheFDroidBuild() {
        assertEquals(BuildConfig.DISTRIBUTION == "F-Droid", BuildConfig.STRICT_PROXY_FETCH)
    }

    @Test
    fun subscriptionResultsKeepEveryDirectRetryCandidateWhenCombined() {
        val combined = SubscriptionUpdateResult(failureCount = 1, directRetrySubIds = listOf("a")) +
                SubscriptionUpdateResult(successCount = 1, configCount = 3) +
                SubscriptionUpdateResult(failureCount = 1, directRetrySubIds = listOf("b"))
        assertEquals(listOf("a", "b"), combined.directRetrySubIds)
        assertEquals(2, combined.failureCount)
        assertEquals(1, combined.successCount)
        assertTrue(SubscriptionUpdateResult().directRetrySubIds.isEmpty())
    }

    private companion object {
        const val PORT = 10809
    }
}
