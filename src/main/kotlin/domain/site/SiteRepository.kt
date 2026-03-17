package com.gonzalinux.domain.site

import com.fasterxml.jackson.databind.ObjectMapper
import com.gonzalinux.common.bindNullable
import com.gonzalinux.domain.Languages
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
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
        description: String?, stylesUrl: String?,
        languages: List<Languages>, config: SiteConfig
    ): Mono<Site> =
        client.sql("""
            INSERT INTO sites (user_id, name, domain, description, styles_url, languages, config)
            VALUES (:userId, :name, :domain, :description, :stylesUrl, :languages, :config::jsonb)
            RETURNING *
        """)
            .bind("userId", userId)
            .bind("name", name)
            .bind("domain", domain)
            .bindNullable<String>("description", description)
            .bindNullable<String>("stylesUrl", stylesUrl)
            .bind("languages", languages.map { it.value }.toTypedArray())
            .bind("config", objectMapper.writeValueAsString(config))
            .fetch().first()
            .map { mapToSite(it) }

    fun update(
        id: Long, userId: Long, name: String?, description: String?,
        stylesUrl: String?, languages: List<Languages>?, config: SiteConfig?
    ): Mono<Site> =
        client.sql("""
            UPDATE sites SET
                name        = COALESCE(:name, name),
                description = COALESCE(:description, description),
                styles_url  = COALESCE(:stylesUrl, styles_url),
                languages   = COALESCE(:languages, languages),
                config      = COALESCE(:config::jsonb, config),
                updated_at  = now()
            WHERE id = :id AND user_id = :userId
            RETURNING *
        """)
            .bind("id", id)
            .bind("userId", userId)
            .bindNullable<String>("name", name)
            .bindNullable<String>("description", description)
            .bindNullable<String>("stylesUrl", stylesUrl)
            .bindNullable<Array<String>>("languages", languages?.map { it.value }?.toTypedArray())
            .bindNullable<String>("config", config?.let { objectMapper.writeValueAsString(it) })
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

    @Suppress("UNCHECKED_CAST")
    private fun mapToSite(row: Map<String, Any>): Site {
        val languages = (row["languages"] as Array<String>)
            .mapNotNull { v -> Languages.entries.find { it.value == v } }
        val config = objectMapper.readValue(row["config"].toString(), SiteConfig::class.java)

        return Site(
            id = row["id"] as Long,
            userId = row["user_id"] as Long,
            name = row["name"] as String,
            domain = row["domain"] as String,
            description = row["description"] as? String,
            stylesUrl = row["styles_url"] as? String,
            languages = languages,
            config = config,
            createdAt = row["created_at"] as OffsetDateTime,
            updatedAt = row["updated_at"] as OffsetDateTime
        )
    }
}
