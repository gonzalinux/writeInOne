package com.gonzalinux.api

import com.gonzalinux.api.data.AcceptInvitationRequest
import com.gonzalinux.api.data.CreateInvitationRequest
import com.gonzalinux.api.data.InvitationDelivery
import com.gonzalinux.common.BadRequestException
import com.gonzalinux.common.RequestContextHolder.getUserId
import com.gonzalinux.common.RequestValidator
import com.gonzalinux.common.pathVariableLong
import com.gonzalinux.domain.site.Roles
import com.gonzalinux.domain.site.SiteInvitation
import com.gonzalinux.domain.site.SiteInvitationService
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.bodyToMono
import reactor.core.publisher.Mono

@Component
class InvitationHandler(
    private val service: SiteInvitationService,
    private val validator: RequestValidator
) {

    fun create(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariableLong("id")
        return Mono.deferContextual { ctx ->
            request.bodyToMono<CreateInvitationRequest>()
                .map { validator.validate(it) }
                .flatMap { body -> service.create(siteId, ctx.getUserId()!!, roleOf(body), recipientOf(body)) }
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }

    /** Roles.from is lenient because this value arrives on an untrusted request body. */
    private fun roleOf(body: CreateInvitationRequest): Roles =
        Roles.from(body.role) ?: throw BadRequestException("Unknown role '${body.role}'")

    /** Null means "just give me the link" — the service mails nothing. */
    private fun recipientOf(body: CreateInvitationRequest): String? {
        val delivery = InvitationDelivery.from(body.delivery)
            ?: throw BadRequestException("Unknown delivery '${body.delivery}'")
        if (delivery == InvitationDelivery.LINK) return null
        return body.email?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw BadRequestException("An email address is required to send an invitation by email")
    }

    fun list(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariableLong("id")
        return Mono.deferContextual { ctx ->
            ServerResponse.ok().body<SiteInvitation>(service.list(siteId, ctx.getUserId()!!))
        }
    }

    fun revoke(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariableLong("id")
        val invitationId = request.pathVariableLong("invitationId")
        return Mono.deferContextual { ctx ->
            service.revoke(siteId, invitationId, ctx.getUserId()!!)
                .then(ServerResponse.ok().build())
        }
    }

    /** Not under /sites — the caller is not a member yet, so the token is the only authorization. */
    fun accept(request: ServerRequest): Mono<ServerResponse> =
        Mono.deferContextual { ctx ->
            request.bodyToMono<AcceptInvitationRequest>()
                .map { validator.validate(it) }
                .flatMap { service.accept(ctx.getUserId()!!, it.token) }
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
}
