package com.gonzalinux.client

import com.gonzalinux.config.GonemailProperties
import com.gonzalinux.domain.user.EmailClient
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

@Component
class GonemailClientImpl(
    private val webClient: WebClient,
    private val props: GonemailProperties
) : EmailClient {

    override fun sendVerificationEmail(to: String, displayName: String, code: String): Mono<Void> =
        send(
            template = props.verificationTemplate,
            to = to,
            data = mapOf("display_name" to displayName, "verification_code" to code)
        )

    override fun sendPasswordResetEmail(to: String, code: String): Mono<Void> =
        send(
            template = props.passwordResetTemplate,
            to = to,
            data = mapOf("reset_code" to code)
        )

    override fun sendInvitationEmail(to: String, siteName: String, acceptUrl: String): Mono<Void> =
        send(
            template = props.invitationTemplate,
            to = to,
            data = mapOf("site_name" to siteName, "accept_url" to acceptUrl)
        )

    private fun send(template: String, to: String, data: Map<String, String>): Mono<Void> {
        val body = mapOf("app" to props.appName, "template" to template, "to" to to, "data" to data)
        return webClient.post()
            .uri("${props.baseUrl}/api/v1/send")
            .bodyValue(body)
            .retrieve()
            .toBodilessEntity()
            .doOnSuccess { logger.debug { "Email queued [template=$template, to=$to]" } }
            .doOnError { logger.warn(it) { "Failed to queue email [template=$template, to=$to]" } }
            .then()
    }
}
