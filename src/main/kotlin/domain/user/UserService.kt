package com.gonzalinux.domain.user

import com.gonzalinux.common.ApiException
import com.gonzalinux.common.UnauthorizedException
import com.gonzalinux.common.UserAlreadyExistsException
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty


@Service
class UserService(
    private val repo: UserRepository,
    private val encoder: BCryptPasswordEncoder,
    private val tokenService: TokenService
) {

    fun register(email: String, displayName: String, password: String): Mono<AuthTokens> {
        return repo.findByEmail(email)
            .flatMap<AuthTokens> { Mono.error(UserAlreadyExistsException(email)) }
            .switchIfEmpty(
                repo.create(
                    email = email,
                    displayName = displayName,
                    passwordHash = encoder.encode(password)!!
                ).flatMap { user -> issueTokens(user) }
            )
    }

    fun login(email: String, password: String): Mono<AuthTokens> {
        return repo.findByEmail(email)
            .switchIfEmpty {
                encoder.encode("Just to prevent timing attacks")
                Mono.empty()
            }
            .filter { encoder.matches(password, it.passwordHash) }
            .flatMap { issueTokens(it) }
            .switchIfEmpty {
                Mono.error(UnauthorizedException())
            }
    }

    private fun issueTokens(user: User): Mono<AuthTokens> {
        val accessToken = tokenService.generateAccessToken(user)
        val refreshToken = tokenService.generateRefreshToken()
        val tokenHash = tokenService.hashToken(refreshToken.value)

        return repo.saveRefreshToken(user.id, tokenHash, refreshToken.expiresAt)
            .thenReturn(AuthTokens(accessToken, refreshToken))
    }
}
