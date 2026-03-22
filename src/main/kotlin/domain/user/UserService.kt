package com.gonzalinux.domain.user

import com.gonzalinux.common.UnauthorizedException
import com.gonzalinux.common.UserAlreadyExistsException
import com.gonzalinux.config.PasswordEncoder
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty

private val logger = KotlinLogging.logger {}

@Service
class UserService(
    private val repo: UserRepository,
    private val encoder: PasswordEncoder,
    private val tokenService: TokenService,
    private val registry: MeterRegistry
) {

    fun register(email: String, displayName: String, password: String): Mono<AuthTokens> {
        return repo.findByEmail(email)
            .flatMap<AuthTokens> { Mono.error(UserAlreadyExistsException(email)) }
            .switchIfEmpty(
                repo.create(
                    email = email,
                    displayName = displayName,
                    passwordHash = encoder.encode(password)
                ).flatMap { user ->
                    logger.info { "User registered [userId=${user.id}, email=$email]" }
                    issueTokens(user.id)
                }.doOnSuccess { registry.counter("auth.registrations").increment() }
            )
    }

    fun login(email: String, password: String): Mono<AuthTokens> {
        return repo.findByEmail(email)
            .switchIfEmpty {
                encoder.encode("Just to prevent timing attacks")
                Mono.empty()
            }
            .filter { encoder.matches(password, it.passwordHash) }
            .flatMap { user ->
                logger.info { "User logged in [userId=${user.id}]" }
                issueTokens(user.id)
            }
            .doOnSuccess { registry.counter("auth.logins", "result", "success").increment() }
            .switchIfEmpty {
                logger.warn { "Failed login attempt [email=$email]" }
                registry.counter("auth.logins", "result", "failure").increment()
                Mono.error(UnauthorizedException())
            }
    }

    fun logout(refreshToken: String): Mono<Void> {
        val tokenHash = tokenService.hashToken(refreshToken)
        return repo.deleteRefreshToken(tokenHash)
            .doOnSuccess { logger.info { "User logged out" } }
    }

    fun refreshToken(refreshToken: String): Mono<AuthTokens> {
        val hashedToken = tokenService.hashToken(refreshToken)
        return repo.findRefreshToken(hashedToken)
            .switchIfEmpty {
                Mono.error(UnauthorizedException())
            }
            .flatMap { stored ->
                val newAccessToken = tokenService.generateAccessToken(stored.userId)
                logger.debug { "Token refreshed [userId=${stored.userId}]" }
                Mono.just(AuthTokens(newAccessToken, RefreshToken(refreshToken, stored.expiresAt)))
            }
            .doOnSuccess { registry.counter("auth.token.refreshes").increment() }
    }

    private fun issueTokens(userId: Long): Mono<AuthTokens> {
        val accessToken = tokenService.generateAccessToken(userId)
        val refreshToken = tokenService.generateRefreshToken()
        val tokenHash = tokenService.hashToken(refreshToken.value)

        return repo.saveRefreshToken(userId, tokenHash, refreshToken.expiresAt)
            .thenReturn(AuthTokens(accessToken, refreshToken))
    }
}
