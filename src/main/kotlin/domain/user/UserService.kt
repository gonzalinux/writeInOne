package com.gonzalinux.domain.user

import com.gonzalinux.common.ApiException
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

class UserAlreadyExistsException(email: String) : ApiException(
    status = HttpStatus.CONFLICT,
    error = "USER_ALREADY_EXISTS",
    details = "A user with email $email already exists"
)

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

    private fun issueTokens(user: User): Mono<AuthTokens> {
        val accessToken = tokenService.generateAccessToken(user)
        val refreshToken = tokenService.generateRefreshToken()
        val tokenHash = tokenService.hashToken(refreshToken.value)

        return repo.saveRefreshToken(user.id, tokenHash, refreshToken.expiresAt)
            .thenReturn(AuthTokens(accessToken, refreshToken))
    }
}
