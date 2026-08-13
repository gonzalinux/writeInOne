package com.gonzalinux.api.data

import jakarta.validation.constraints.NotBlank

/** How the admin wants the new link handed over. */
enum class InvitationDelivery {
    /** Return the link and send nothing — the admin shares it themselves. */
    LINK,

    /** Mail the link to [CreateInvitationRequest.email]. */
    EMAIL;

    companion object {
        fun from(value: String?): InvitationDelivery? =
            if (value == null) LINK
            else entries.firstOrNull { it.name == value.uppercase() }
    }
}

data class CreateInvitationRequest(
    @field:NotBlank val role: String,
    /** Defaults to [InvitationDelivery.LINK]. */
    val delivery: String? = null,
    /**
     * Required when [delivery] is `EMAIL`, ignored otherwise. Used inline to mail the link and then
     * discarded — no column stores it, and nothing checks it at accept time.
     */
    val email: String? = null
)

data class AcceptInvitationRequest(
    @field:NotBlank val token: String
)
