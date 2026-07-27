package com.gonzalinux.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "subdomains")
data class SubdomainProperties(
    val baseDomain: String = "writeinone.com",
    val minLength: Int = 3,
    val maxLength: Int = 30,
    val reservationDays: Long = 7,
    val purgeIntervalMin: Long = 60,
    val reserved: Set<String> = emptySet()
) {
    private val suffix = ".$baseDomain"

    val reservedLabels: Set<String> = reserved.map { it.trim().lowercase() }.toSet()

    /** Host headers may carry a port and arbitrary casing; site domains are stored lowercase. */
    fun normalizeHost(host: String): String = host.trim().lowercase().removeSuffix(".")

    /** True for the app's own front door — the landing page and the admin UI live here. */
    fun isHomeDomain(host: String): Boolean = hostOnly(host).let {
        it == baseDomain || it == "www.$baseDomain" || it == "localhost"
    }

    /** True for anything under the base domain, including malformed multi-label hosts. */
    fun isManaged(domain: String): Boolean = hostOnly(domain).endsWith(suffix)

    /** The single label of a domain we own, or null when the shape is anything else. */
    fun labelOf(domain: String): String? {
        val host = hostOnly(domain)
        if (!host.endsWith(suffix)) return null
        return host.removeSuffix(suffix).takeIf { it.isNotEmpty() && !it.contains('.') }
    }

    fun domainFor(label: String): String = "$label$suffix"

    private fun hostOnly(host: String): String = normalizeHost(host).substringBefore(':')
}
