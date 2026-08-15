package com.gonzalinux.api

import com.gonzalinux.api.data.CreateServiceAccountRequest
import com.gonzalinux.api.data.ServiceAccountCreatedResponse
import com.gonzalinux.api.data.ServiceAccountResponse
import com.gonzalinux.api.data.ServiceAccountTokenResponse
import com.gonzalinux.common.RequestContextHolder.getUserId
import com.gonzalinux.common.RequestValidator
import com.gonzalinux.common.pathVariableLong
import com.gonzalinux.domain.user.ServiceAccountService
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.bodyToMono
import reactor.core.publisher.Mono

@Component
class ServiceAccountHandler(
    private val service: ServiceAccountService,
    private val validator: RequestValidator
) {

    fun create(request: ServerRequest): Mono<ServerResponse> =
        Mono.deferContextual { ctx ->
            request.bodyToMono<CreateServiceAccountRequest>()
                .map { validator.validate(it) }
                .flatMap { body -> service.create(ctx.getUserId()!!, body.name) }
                .flatMap { created ->
                    ServerResponse.ok().bodyValue(
                        ServiceAccountCreatedResponse(
                            id = created.account.id,
                            name = created.account.displayName,
                            token = created.token,
                            createdAt = created.account.createdAt
                        )
                    )
                }
        }

    fun list(request: ServerRequest): Mono<ServerResponse> =
        Mono.deferContextual { ctx ->
            ServerResponse.ok().body<ServiceAccountResponse>(
                service.list(ctx.getUserId()!!)
                    .map { ServiceAccountResponse(it.id, it.displayName, it.createdAt) }
            )
        }

    fun revoke(request: ServerRequest): Mono<ServerResponse> {
        val id = request.pathVariableLong("id")
        return Mono.deferContextual { ctx ->
            service.revoke(id, ctx.getUserId()!!)
                .then(ServerResponse.ok().build())
        }
    }

    fun rotate(request: ServerRequest): Mono<ServerResponse> {
        val id = request.pathVariableLong("id")
        return Mono.deferContextual { ctx ->
            service.rotate(id, ctx.getUserId()!!)
                .flatMap { ServerResponse.ok().bodyValue(ServiceAccountTokenResponse(it)) }
        }
    }
}
