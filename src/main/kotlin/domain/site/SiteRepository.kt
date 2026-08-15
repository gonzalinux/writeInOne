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
        client.sql(
            """SELECT sites.*, site_members.role FROM sites 
            INNER JOIN site_members ON sites.id = site_members.site_id
             WHERE sites.id = :id AND site_members.user_id = :userId
        """
        )
            .bind("id", id)
            .bind("userId", userId)
            .fetch().first()
            .map { mapToSite(it) }

    fun findAllByUserId(userId: Long): Flux<Site> =
        client.sql(
            """SELECT sites.*, site_members.role FROM sites 
             INNER JOIN site_members ON sites.id = site_members.site_id 
             WHERE site_members.user_id = :userId 
             ORDER BY sites.created_at DESC"""
        )
            .bind("userId", userId)
            .fetch().all()
            .map { mapToSite(it) }

    fun create(
        userId: Long, name: String, domain: String,
        description: String?, stylesUrl: String?, customCss: String?, availableThemes: List<Theme>,
        languages: List<Languages>, config: SiteConfig, status: SiteStatus = SiteStatus.NOT_VERIFIED
    ): Mono<Site> =
        client.sql(
            """
            WITH new_site AS (
                INSERT INTO sites (user_id, name, domain, description, styles_url, custom_css, available_themes, languages, config, status)
                VALUES (:userId, :name, :domain, :description, :stylesUrl, :customCss, :availableThemes, :languages, :config::jsonb, :status)
                RETURNING *
            ), creator AS (
                -- The creator is the site's first admin; one statement so a site can never
                -- exist without a member row, which findById's join would make unreachable.
                INSERT INTO site_members (site_id, user_id, role)
                SELECT id, user_id, 'ADMIN' FROM new_site
            )
            SELECT new_site.*, 'ADMIN' AS role FROM new_site
        """
        )
            .bind("userId", userId)
            .bind("name", name)
            .bind("domain", domain)
            .bind("status", status.toString())
            .bindNullable<String>("description", description)
            .bindNullable<String>("stylesUrl", stylesUrl)
            .bindNullable<String>("customCss", customCss)
            .bind("availableThemes", availableThemes.map { it.value }.toTypedArray())
            .bind("languages", languages.map { it.value }.toTypedArray())
            .bind("config", objectMapper.writeValueAsString(config))
            .fetch().first()
            .map { mapToSite(it) }

    fun update(
        id: Long,
        name: String? = null,
        domain: String? = null,
        description: String? = null,
        stylesUrl: String? = null,
        customCss: String? = null,
        availableThemes: List<Theme>? = null,
        languages: List<Languages>? = null,
        config: SiteConfig? = null,
        status: SiteStatus? = null,
        prefix: String? = null,
        verifyDate: OffsetDateTime? = null
    ): Mono<Site> =
        client.sql(
            """
            UPDATE sites SET
                name             = COALESCE(:name, name),
                domain           = COALESCE(:domain, domain),
                description      = COALESCE(:description, description),
                styles_url       = COALESCE(:stylesUrl, styles_url),
                custom_css       = COALESCE(:customCss, custom_css),
                available_themes = COALESCE(:availableThemes, available_themes),
                languages        = COALESCE(:languages, languages),
                prefix           = COALESCE(:prefix, prefix),
                status           = COALESCE(:status, status),
                config           = COALESCE(:config::jsonb, config),
                verify_date      = COALESCE(:verify_date, verify_date),
                updated_at       = now()
            WHERE id = :id
            RETURNING *
        """
        )
            .bind("id", id)
            .bindNullable<String>("name", name)
            .bindNullable<String>("domain", domain)
            .bindNullable<String>("description", description)
            .bindNullable<String>("stylesUrl", stylesUrl)
            .bindNullable<String>("customCss", customCss)
            .bindNullable<String>("prefix", prefix)
            .bindNullable<String>("status", status?.toString())
            .bindNullable<Array<String>>("availableThemes", availableThemes?.map { it.value }?.toTypedArray())
            .bindNullable<Array<String>>("languages", languages?.map { it.value }?.toTypedArray())
            .bindNullable<String>("config", config?.let { objectMapper.writeValueAsString(it) })
            .bindNullable<OffsetDateTime>("verify_date", verifyDate)
            .fetch().first()
            .map { mapToSite(it) }

    fun delete(id: Long): Mono<Unit> =
        client.sql("DELETE FROM sites WHERE id = :id")
            .bind("id", id)
            .then().thenReturn(Unit)

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

    fun findAll(): Flux<Site> =
        client.sql("SELECT * FROM sites ORDER BY created_at DESC")
            .fetch().all()
            .map { mapToSite(it) }

    fun countAll(): Mono<Long> =
        client.sql("SELECT COUNT(*) as count FROM sites")
            .fetch().first()
            .map { it["count"] as Long }

    fun findRecentSites(limit: Int): Flux<Site> =
        client.sql("SELECT * FROM sites ORDER BY created_at DESC LIMIT :limit")
            .bind("limit", limit)
            .fetch().all()
            .map { mapToSite(it) }

    fun updateStatus(siteId: Long, status: SiteStatus): Mono<Site> =
        client.sql(
            """
            UPDATE sites SET status = :status, updated_at = now()
            WHERE id = :id
            RETURNING *
        """
        )
            .bind("id", siteId)
            .bind("status", status.toString())
            .fetch().first()
            .map { mapToSite(it) }

    fun getAllUsers(siteId: Long): Flux<UserSite> {
        return client.sql(
            """SELECT site_members.role, site_members.site_id, site_members.created_at, users.* 
            FROM site_members INNER JOIN users on site_members.user_id = users.id
            WHERE site_members.site_id = :siteId 
            """
        )
            .bind("siteId", siteId)
            .fetch().all()
            .map { mapToUserSite(it) }
    }

    /**
     * Direct add, skipping the token/accept round-trip — used to invite service accounts.
     * `ON CONFLICT DO NOTHING` keeps an existing member's role intact.
     */
    fun addMember(siteId: Long, userId: Long, role: Roles): Mono<Boolean> =
        client.sql(
            """
            INSERT INTO site_members (site_id, user_id, role)
            VALUES (:siteId, :userId, :role)
            ON CONFLICT (user_id, site_id) DO NOTHING
            RETURNING user_id
            """
        )
            .bind("siteId", siteId)
            .bind("userId", userId)
            .bind("role", role.name)
            .fetch().first()
            .map { true }
            .defaultIfEmpty(false)

    fun updateMemberRole(siteId: Long, userId: Long, role: Roles): Mono<Unit> =
        client.sql(
            """
           UPDATE site_members SET role = :role WHERE site_id = :siteId AND user_id = :userId
        """
        )
            .bind("siteId", siteId)
            .bind("userId", userId)
            .bind("role", role.name)
            .then().thenReturn(Unit)

    fun deleteUser(siteId: Long, userId: Long): Mono<Unit> =
        client.sql("""
           DELETE FROM site_members WHERE site_id = :siteId AND user_id = :userId 
        """)
            .bind("siteId", siteId)
            .bind("userId",userId)
            .then().thenReturn(Unit)

    @Suppress("UNCHECKED_CAST")
    private fun mapToUserSite(row: Map<String, Any>): UserSite {
        return UserSite(
            role = Roles.from(row["role"] as String)!!,
            siteId = row["site_id"] as Long,
            userId = row["id"] as Long,
            email = row["email"] as String,
            displayName = row["display_name"] as String,
            createdAt = row["created_at"] as OffsetDateTime
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToSite(row: Map<String, Any>): Site {
        val languages = (row["languages"] as Array<String>)
            .mapNotNull { v -> Languages.entries.find { it.value == v } }
        val config = when (val raw = row["config"]) {
            is String -> objectMapper.readValue(raw, SiteConfig::class.java)
            is ByteArray -> objectMapper.readValue(raw, SiteConfig::class.java)
            is Json -> objectMapper.readValue(raw.asArray(), SiteConfig::class.java)
            else -> error("Unexpected config type: ${raw?.javaClass}")
        }

        return Site(
            id = row["id"] as Long,
            userId = row["user_id"] as Long,
            name = row["name"] as String,
            domain = row["domain"] as String,
            prefix = row["prefix"] as String,
            description = row["description"] as? String,
            stylesUrl = row["styles_url"] as? String,
            customCss = row["custom_css"] as? String,
            availableThemes = (row["available_themes"] as Array<String>).mapNotNull { Theme.fromValue(it) },
            languages = languages,
            config = config,
            status = SiteStatus.valueOf(row["status"] as String),
            createdAt = row["created_at"] as OffsetDateTime,
            updatedAt = row["updated_at"] as OffsetDateTime,
            verifyDate = row["verify_date"] as OffsetDateTime,
            role = Roles.from(row["role"] as String?)
        )
    }
}
