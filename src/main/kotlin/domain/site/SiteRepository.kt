package com.gonzalinux.domain.site

import com.gonzalinux.common.bindNullable
import com.gonzalinux.domain.Languages
import io.r2dbc.postgresql.codec.Json
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import java.time.OffsetDateTime

@Repository
class SiteRepository(private val client: DatabaseClient, private val objectMapper: ObjectMapper) {

    fun findById(id: Long, userId: Long): Mono<Site> =
        client.sql("SELECT * FROM sites WHERE id = :id AND user_id = :userId")
            .bind("id", id)
            .bind("userId", userId)
            .fetch().first()
            .map { mapToSite(it) }

    fun findAllByUserId(userId: Long): Flux<Site> =
        client.sql("SELECT * FROM sites WHERE user_id = :userId ORDER BY created_at DESC")
            .bind("userId", userId)
            .fetch().all()
            .map { mapToSite(it) }

    fun create(
        userId: Long, name: String, domain: String,
        description: String?, stylesUrl: String?, availableThemes: List<Theme>,
        languages: List<Languages>, config: SiteConfig
    ): Mono<Site> =
        client.sql("""
            INSERT INTO sites (user_id, name, domain, description, styles_url, available_themes, languages, config)
            VALUES (:userId, :name, :domain, :description, :stylesUrl, :availableThemes, :languages, :config::jsonb)
            RETURNING *
        """)
            .bind("userId", userId)
            .bind("name", name)
            .bind("domain", domain)
            .bindNullable<String>("description", description)
            .bindNullable<String>("stylesUrl", stylesUrl)
            .bind("availableThemes", availableThemes.map { it.value }.toTypedArray())
            .bind("languages", languages.map { it.value }.toTypedArray())
            .bind("config", objectMapper.writeValueAsString(config))
            .fetch().first()
            .map { mapToSite(it) }

    fun update(
        id: Long,
        userId: Long,
        name: String? = null,
        domain: String? = null,
        description: String? = null,
        stylesUrl: String? = null,
        availableThemes: List<Theme>? = null,
        languages: List<Languages>? = null,
        config: SiteConfig? = null,
        status:  SiteStatus? = null,
        prefix: String? = null,
        verifyDate: OffsetDateTime? = null
    ): Mono<Site> =
        client.sql("""
            UPDATE sites SET
                name             = COALESCE(:name, name),
                domain           = COALESCE(:domain, domain),
                description      = COALESCE(:description, description),
                styles_url       = COALESCE(:stylesUrl, styles_url),
                available_themes = COALESCE(:availableThemes, available_themes),
                languages        = COALESCE(:languages, languages),
                prefix           = COALESCE(:prefix, prefix),
                status           = COALESCE(:status, status),
                config           = COALESCE(:config::jsonb, config),
                verify_date      = COALESCE(:verify_date, verify_date),
                updated_at       = now()
            WHERE id = :id AND user_id = :userId
            RETURNING *
        """)
            .bind("id", id)
            .bind("userId", userId)
            .bindNullable<String>("name", name)
            .bindNullable<String>("domain", domain)
            .bindNullable<String>("description", description)
            .bindNullable<String>("stylesUrl", stylesUrl)
            .bindNullable<String>("prefix", prefix)
            .bindNullable<String>("status", status?.toString())
            .bindNullable<Array<String>>("availableThemes", availableThemes?.map { it.value }?.toTypedArray())
            .bindNullable<Array<String>>("languages", languages?.map { it.value }?.toTypedArray())
            .bindNullable<String>("config", config?.let { objectMapper.writeValueAsString(it) })
            .bindNullable<OffsetDateTime>("verify_date", verifyDate)
            .fetch().first()
            .map { mapToSite(it) }

    fun delete(id: Long, userId: Long): Mono<Void> =
        client.sql("DELETE FROM sites WHERE id = :id AND user_id = :userId")
            .bind("id", id)
            .bind("userId", userId)
            .then()

    fun findByDomain(domain: String): Mono<Site> =
        client.sql("SELECT * FROM sites WHERE domain = :domain")
            .bind("domain", domain)
            .fetch().first()
            .map { mapToSite(it) }

    fun existsByDomain(domain: String): Mono<Boolean> =
        client.sql("SELECT 1 FROM sites WHERE domain = :domain LIMIT 1")
            .bind("domain", domain)
            .fetch().first()
            .map { true }
            .defaultIfEmpty(false)

    fun notVerified(): Flux<Site> {
        val rangeDate = OffsetDateTime.now().minusDays(2)

        return client.sql("SELECT * FROM sites where status = 'NOT_VERIFIED' AND verify_date > :range_date")
            .bind("range_date", rangeDate)
            .fetch().all().map { mapToSite(it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToSite(row: Map<String, Any>): Site {
        val languages = (row["languages"] as Array<String>)
            .mapNotNull { v -> Languages.entries.find { it.value == v } }
        val config = when (val raw = row["config"]) {
            is String -> objectMapper.readValue(raw, SiteConfig::class.java)
            is ByteArray -> objectMapper.readValue(raw, SiteConfig::class.java)
            is Json -> objectMapper.readValue(raw.asArray(), SiteConfig::class.java)
            else ->  error("Unexpected config type: ${raw?.javaClass}")
        }

        return Site(
            id = row["id"] as Long,
            userId = row["user_id"] as Long,
            name = row["name"] as String,
            domain = row["domain"] as String,
            prefix = row["prefix"] as String,
            description = row["description"] as? String,
            stylesUrl = row["styles_url"] as? String,
            availableThemes = (row["available_themes"] as Array<String>).mapNotNull { Theme.fromValue(it) },
            languages = languages,
            config = config,
            status = SiteStatus.valueOf(row["status"] as String),
            createdAt = row["created_at"] as OffsetDateTime,
            updatedAt = row["updated_at"] as OffsetDateTime,
            verifyDate = row["verify_date"] as OffsetDateTime,
        )
    }
}
