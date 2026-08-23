package com.meridiangoods.recordpaymentresult

import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.gateway.CommandGateway

/**
 * Translation: Payment Provider Adapter. See `slices/record-payment-result.md`.
 *
 * Externally triggered, no internal event to subscribe to (per `axon-translation-patterns`):
 * this is a plain class a real webhook controller would call with the deserialized provider
 * payload — no `EventProcessorModule` involved. Two distinct failure modes, both from the doc's
 * Alternate & Error Flows:
 *
 * - **Malformed/partial payload**: [PaymentCaptureWebhookMapper] returns `null`; this is
 *   rejected *at the translation boundary*, logged, and dropped — it never becomes a command
 *   attempt.
 * - **Unknown paymentId (INV-RPR-3)**: the mapping succeeds, but the command handler rejects
 *   it. That exception is deliberately allowed to propagate out of [onWebhook] — the doc calls
 *   for this to be "surfaced back to the provider integration as a failure response" (so a real
 *   webhook controller can return a non-2xx and let the provider alert), not swallowed here.
 */
class PaymentProviderAdapter(private val commandGateway: CommandGateway) {

    private val logger = KotlinLogging.logger {}

    fun onWebhook(payload: PaymentProviderCapturePayload) {
        val command = PaymentCaptureWebhookMapper.toCommand(payload)
        if (command == null) {
            logger.warn { "Rejected malformed/partial payment-provider webhook payload: $payload" }
            return
        }
        commandGateway.sendAndWait(command)
    }
}
