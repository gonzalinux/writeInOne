package com.gonzalinux.common

import org.springframework.http.HttpStatus


class UserAlreadyExistsException(email: String) : ApiException(
    status = HttpStatus.CONFLICT,
    error = "USER_ALREADY_EXISTS",
    details = "A user with email $email already exists"
)

class UnauthorizedException() : ApiException(
    status = HttpStatus.UNAUTHORIZED,
    error = "UNAUTHORIZED",
    details = "You didn't provide valid credentials."
)

