package com.gonzalinux.api

import com.gonzalinux.api.AuthHandler.Companion.ACCESS_TOKEN_COOKIE
import com.gonzalinux.client.TestEmailClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SubdomainIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var db: DatabaseClient

    @Autowired
    lateinit var emailClient: TestEmailClient

    private var accessTokenCookie: String = ""
    private var otherUserCookie: String = ""
    private lateinit var testEmail: String
    private lateinit var otherEmail: String
    private var ts: Long = 0L

    private val base = "writeinone.com"

    /** Unique per run, and short enough to stay inside the length bounds. */
    private fun label(suffix: String) = "it$suffix${ts % 1_000_000}"

    @BeforeEach
    fun setup() {
        ts = System.currentTimeMillis()
        testEmail = "subtest-$ts@integrationtest.com"
        otherEmail = "subother-$ts@integrationtest.com"
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
        accessTokenCookie = webTestClient.registerVerifiedUser(emailClient, testEmail)
        otherUserCookie = webTestClient.registerVerifiedUser(emailClient, otherEmail)
    }

    @AfterEach
    fun cleanup() {
        db.sql("DELETE FROM sites WHERE user_id IN (SELECT id FROM users WHERE email LIKE :pattern)")
            .bind("pattern", "sub%-$ts@integrationtest.com").then().block()
        // subdomain_reservations cascades from users
        db.sql("DELETE FROM users WHERE email LIKE :pattern")
            .bind("pattern", "sub%-$ts@integrationtest.com").then().block()
        emailClient.clear()
    }

    private fun createSite(name: String, domain: String, cookie: String = accessTokenCookie) =
        webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name, "domain" to domain))
            .exchange()

    private fun createSiteId(name: String, domain: String): Long {
        val body = createSite(name, domain)
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!
        return (body["id"] as Number).toLong()
    }

    // ── the lookup endpoint ────────────────────────────────────────────────

    @Test
    fun `subdomain endpoint describes the rules when no name is given`() {
        webTestClient.get().uri("/sites/subdomain")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.baseDomain").isEqualTo(base)
            .jsonPath("$.minLength").isNumber
            .jsonPath("$.maxLength").isNumber
    }

    /** It sits before /sites/{id}, which would otherwise swallow it and fail to parse the id. */
    @Test
    fun `subdomain endpoint is not shadowed by the site id route`() {
        webTestClient.get().uri("/sites/subdomain?name=${label("free")}")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.available").isEqualTo(true)
            .jsonPath("$.domain").isEqualTo("${label("free")}.$base")
    }

    @Test
    fun `subdomain endpoint reports a reserved label as unavailable`() {
        webTestClient.get().uri("/sites/subdomain?name=admin")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.available").isEqualTo(false)
            .jsonPath("$.reason").exists()
    }

    @Test
    fun `subdomain endpoint requires authentication`() {
        webTestClient.get().uri("/sites/subdomain")
            .exchange()
            .expectStatus().isUnauthorized
    }

    // ── creating a site on a managed subdomain ─────────────────────────────

    @Test
    fun `site on a managed subdomain is verified immediately and served at the root`() {
        createSite("Sub Blog", "${label("ok")}.$base")
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.domain").isEqualTo("${label("ok")}.$base")
            .jsonPath("$.status").isEqualTo("VERIFIED")
            .jsonPath("$.prefix").isEqualTo("")
    }

    @Test
    fun `reserved labels are rejected whichever field they arrive in`() {
        createSite("Sneaky", "admin.$base")
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `nested labels under the base domain are rejected`() {
        createSite("Nested", "a.b.$base")
            .expectStatus().isBadRequest
    }

    @Test
    fun `the base domain itself cannot be registered`() {
        createSite("Home", base)
            .expectStatus().isBadRequest

        createSite("Home WWW", "www.$base")
            .expectStatus().isBadRequest
    }

    @Test
    fun `a taken subdomain is unavailable to everyone else`() {
        createSite("Mine", "${label("taken")}.$base").expectStatus().isOk

        webTestClient.get().uri("/sites/subdomain?name=${label("taken")}")
            .cookie(ACCESS_TOKEN_COOKIE, otherUserCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.available").isEqualTo(false)

        createSite("Yours", "${label("taken")}.$base", otherUserCookie)
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    // ── the one-week hold after a rename ───────────────────────────────────

    @Test
    fun `renaming parks the old label for its owner but blocks everyone else`() {
        val siteId = createSiteId("Renamer", "${label("old")}.$base")

        webTestClient.patch().uri("/sites/$siteId")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("domain" to "${label("new")}.$base"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.domain").isEqualTo("${label("new")}.$base")
            .jsonPath("$.status").isEqualTo("VERIFIED")

        // The previous owner can still come back to it...
        webTestClient.get().uri("/sites/subdomain?name=${label("old")}")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.available").isEqualTo(true)

        // ...but nobody else can take it during the hold.
        webTestClient.get().uri("/sites/subdomain?name=${label("old")}")
            .cookie(ACCESS_TOKEN_COOKIE, otherUserCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.available").isEqualTo(false)

        createSite("Squatter", "${label("old")}.$base", otherUserCookie)
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `deleting a site parks its label instead of freeing it instantly`() {
        val siteId = createSiteId("Doomed", "${label("del")}.$base")

        webTestClient.delete().uri("/sites/$siteId")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk

        createSite("Squatter", "${label("del")}.$base", otherUserCookie)
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }
}
