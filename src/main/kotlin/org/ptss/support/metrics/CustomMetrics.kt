package org.ptss.support.metrics

import io.micrometer.core.instrument.MeterRegistry
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class CustomMetrics @Inject constructor(private val meterRegistry: MeterRegistry) {

    private val failedRequestsCounter = meterRegistry.counter("failed_requests_total")

    // Increment the failed requests counter
    fun incrementFailedRequests() {
        failedRequestsCounter.increment()
    }
}
