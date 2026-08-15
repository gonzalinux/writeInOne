package com.gonzalinux.common

import org.springframework.http.HttpStatus
import java.time.LocalDate


class UserAlreadyExistsException(email: String) : ApiException(
    status = HttpStatus.CONFLICT,
    error = "USER_ALREADY_EXISTS",
    details = "A user with email $email already exists"
)

class UnauthorizedException() : ApiException(
    status = HttpStatus.UNAUTHORIZED,
    error = "UNAUTHORIZED",
    details = "You didn't provide valid credentials. Or your credentials were expired."
)

class SiteNotFoundException(id: Long) : ApiException(
    status = HttpStatus.NOT_FOUND,
    error = "SITE_NOT_FOUND",
    details = "Site with id $id not found"
)

class SiteDomainTakenException(domain: String) : ApiException(
    status = HttpStatus.CONFLICT,
    error = "SITE_DOMAIN_TAKEN",
    details = "Domain $domain is already taken"
)

class SubdomainNotAllowedException(label: String) : ApiException(
    status = HttpStatus.CONFLICT,
    error = "SUBDOMAIN_NOT_ALLOWED",
    details = "The subdomain '$label' is reserved and cannot be registered"
)

class SubdomainHeldException(label: String, until: LocalDate) : ApiException(
    status = HttpStatus.CONFLICT,
    error = "SUBDOMAIN_HELD",
    details = "The subdomain '$label' was recently released and stays reserved for its previous owner until $until"
)

class PostNotFoundException(id: Long? = null, slug: String? = null) : ApiException(
    status = HttpStatus.NOT_FOUND,
    error = "POST_NOT_FOUND",
    details = if (id != null) "Post with id $id not found" else "Post $slug is not found"
)

class SlugAlreadyExistsException(slug: String) : ApiException(
    status = HttpStatus.CONFLICT,
    error = "SLUG_ALREADY_EXISTS",
    details = "A post with slug '$slug' already exists for this site and language"
)

class BadRequestException(details: String) : ApiException(
    status = HttpStatus.BAD_REQUEST,
    error = "BAD_REQUEST",
    details = details
)

class NotFoundException : ApiException(
    status = HttpStatus.NOT_FOUND,
    error = "NOT_FOUND"
)

class ForbiddenException(details: String) : ApiException(
    status = HttpStatus.FORBIDDEN,
    error = "FORBIDDEN",
    details = details
)

class InvitationNotFoundException(id: Long) : ApiException(
    status = HttpStatus.NOT_FOUND,
    error = "INVITATION_NOT_FOUND",
    details = "Invitation with id $id not found"
)

/** Expired, revoked or simply wrong — all indistinguishable to whoever followed the link. */
class InvalidInvitationException(details: String) : ApiException(
    status = HttpStatus.BAD_REQUEST,
    error = "INVALID_INVITATION",
    details = details
)

class ServiceAccountNotFoundException(id: Long) : ApiException(
    status = HttpStatus.NOT_FOUND,
    error = "SERVICE_ACCOUNT_NOT_FOUND",
    details = "Service account with id $id not found"
)

