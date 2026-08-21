package com.gonzalinux.api

import com.gonzalinux.api.AuthHandler.Companion.ACCESS_TOKEN_COOKIE
import com.gonzalinux.client.TestEmailClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class McpIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var db: DatabaseClient

    @Autowired
    lateinit var emailClient: TestEmailClient

    private var accessTokenCookie: String = ""
    private var siteId: Long = 0L
    private var serviceAccountToken: String = ""

    @BeforeEach
    fun setup() {
        val ts = System.currentTimeMillis()
        val testEmail = "mcptest-$ts@integrationtest.com"
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

        accessTokenCookie = webTestClient.registerVerifiedUser(emailClient, testEmail)
        siteId = createSite("MCP Test Blog", "mcptest-$ts.example.com")
        serviceAccountToken = createServiceAccountGrantedOnSite("mcp-agent-$ts", siteId)
    }

    @AfterEach
    fun cleanup() {
        db.sql("DELETE FROM users WHERE email LIKE '%@integrationtest.com'").then().block()
        db.sql("DELETE FROM users WHERE email LIKE 'sa-%@service.internal'").then().block()
        emailClient.clear()
    }

    @Test
    fun `initialize returns protocol info with no session header`() {
        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("jsonrpc" to "2.0", "id" to 1, "method" to "initialize", "params" to emptyMap<String, Any>()))
            .exchange()
            .expectStatus().isOk
            .expectHeader().doesNotExist("Mcp-Session-Id")
            .expectBody()
            .jsonPath("$.result.protocolVersion").isEqualTo("2025-06-18")
            .jsonPath("$.result.serverInfo.name").isEqualTo("writeinone")

    }

    @Test
    fun `notifications initialized returns 202 with no body`() {
        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("jsonrpc" to "2.0", "method" to "notifications/initialized"))
            .exchange()
            .expectStatus().isEqualTo(202)
            .expectBody().isEmpty
    }

    @Test
    fun `tools list returns the thirteen v1 tools`() {
        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("jsonrpc" to "2.0", "id" to 1, "method" to "tools/list"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.tools.length()").isEqualTo(13)
            .jsonPath("$.result.tools[?(@.name == 'create_draft')]").exists()
            .jsonPath("$.result.tools[?(@.name == 'edit')]").exists()
            .jsonPath("$.result.tools[?(@.name == 'list_versions')]").exists()
            .jsonPath("$.result.tools[?(@.name == 'publish')]").exists()
            .jsonPath("$.result.tools[?(@.name == 'unpublish')]").exists()
            .jsonPath("$.result.tools[?(@.name == 'schedule')]").exists()
            .jsonPath("$.result.tools[?(@.name == 'update_site_config')]").exists()
            .jsonPath("$.result.tools[?(@.name == 'list_docs')]").exists()
            .jsonPath("$.result.tools[?(@.name == 'get_doc')]").exists()
            .jsonPath("$.result.tools[?(@.name == 'propose_edit')]").doesNotExist()
            .jsonPath("$.result.tools[?(@.name == 'publish_version')]").doesNotExist()
    }

    @Test
    fun `tools call list_sites returns the granted site`() {
        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 1, "method" to "tools/call",
                    "params" to mapOf("name" to "list_sites", "arguments" to emptyMap<String, Any>())
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("\"id\":$siteId")) { "expected site $siteId in $text" }
            }
    }

    @Test
    fun `tools call create_draft then get_post round-trips after publishing`() {
        val createResult = webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 1, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "create_draft",
                        "arguments" to mapOf(
                            "siteId" to siteId,
                            "translations" to mapOf(
                                "en" to mapOf("title" to "MCP Post", "body" to "Body", "slug" to "mcp-post")
                            )
                        )
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!

        @Suppress("UNCHECKED_CAST")
        val result = createResult["result"] as Map<String, Any>
        assert((result["isError"] as? Boolean) != true) { "create_draft returned isError: $result" }

        val postId = extractPostId(result)

        webTestClient.post().uri("/sites/$siteId/posts/$postId/publish")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 2, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "get_post",
                        "arguments" to mapOf("siteId" to siteId, "lang" to "en", "slug" to "mcp-post")
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("MCP Post")) { "expected published title in $text" }
            }
    }

    @Test
    fun `tools call edit creates a draft version without touching live content`() {
        val createResult = webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 1, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "create_draft",
                        "arguments" to mapOf(
                            "siteId" to siteId,
                            "translations" to mapOf(
                                "en" to mapOf("title" to "Original", "body" to "Body", "slug" to "propose-edit-post")
                            )
                        )
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!

        @Suppress("UNCHECKED_CAST")
        val postId = extractPostId(createResult["result"] as Map<String, Any>)

        webTestClient.post().uri("/sites/$siteId/posts/$postId/publish")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 2, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "edit",
                        "arguments" to mapOf(
                            "siteId" to siteId,
                            "postId" to postId,
                            "translations" to mapOf("en" to mapOf("title" to "Updated", "body" to "New body"))
                        )
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("\"status\":\"DRAFT\"")) { "expected a draft version in $text" }
            }

        // Live content is untouched — get_post still returns the original title.
        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 3, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "get_post",
                        "arguments" to mapOf("siteId" to siteId, "lang" to "en", "slug" to "propose-edit-post")
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("Original")) { "expected live content unchanged in $text" }
                assert(!text.contains("Updated")) { "live content should not reflect the draft edit: $text" }
            }

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 4, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "list_versions",
                        "arguments" to mapOf("siteId" to siteId, "postId" to postId, "lang" to "en")
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("\"versionNumber\":2")) { "expected two versions in $text" }
            }
    }

    @Test
    fun `tools call publish is forbidden for a writer-role service account`() {
        val postId = createDraftViaMcp(serviceAccountToken, "Writer Cannot Publish", "writer-cannot-publish")
        val versionId = getLatestVersionId(serviceAccountToken, postId, "en")

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 3, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "publish",
                        "arguments" to mapOf("siteId" to siteId, "postId" to postId, "lang" to "en", "versionId" to versionId)
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.error.code").isEqualTo(-32002)
    }

    @Test
    fun `tools call publish brings a brand-new post live, and a later edit is republished the same way`() {
        val editorToken = createServiceAccountGrantedOnSite("mcp-editor-${System.currentTimeMillis()}", siteId, "editor")
        val postId = createDraftViaMcp(editorToken, "Before Edit", "publish-post")
        val v1Id = getLatestVersionId(editorToken, postId, "en")

        // Publishing the very first version brings the whole post live, not just the translation.
        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 2, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "publish",
                        "arguments" to mapOf("siteId" to siteId, "postId" to postId, "lang" to "en", "versionId" to v1Id)
                    )
                )
            )
            .exchange()
            .expectStatus().isOk

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 3, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "get_post",
                        "arguments" to mapOf("siteId" to siteId, "lang" to "en", "slug" to "publish-post")
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("Before Edit")) { "expected the post live in $text" }
            }

        // A further edit creates a new draft version that doesn't touch the now-live content...
        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 4, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "edit",
                        "arguments" to mapOf(
                            "siteId" to siteId,
                            "postId" to postId,
                            "translations" to mapOf(
                                "en" to mapOf(
                                    "title" to "After Edit",
                                    "body" to "New body",
                                    "slug" to "publish-post"
                                )
                            )
                        )
                    )
                )
            )
            .exchange()
            .expectStatus().isOk

        val v2Id = getLatestVersionId(editorToken, postId, "en")
        assert(v2Id != v1Id) { "expected a second version, got $v2Id again" }

        // ...until publish pushes it live too, this time without changing the post's status again.
        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 5, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "publish",
                        "arguments" to mapOf("siteId" to siteId, "postId" to postId, "lang" to "en", "versionId" to v2Id)
                    )
                )
            )
            .exchange()
            .expectStatus().isOk

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 6, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "get_post",
                        "arguments" to mapOf("siteId" to siteId, "lang" to "en", "slug" to "publish-post")
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("After Edit")) { "expected the republished edit live in $text" }
            }
    }

    @Test
    fun `tools call publish with a versions map publishes multiple languages in one call`() {
        val editorToken = createServiceAccountGrantedOnSite("mcp-editor-${System.currentTimeMillis()}", siteId, "editor")

        val createResult = webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 1, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "create_draft",
                        "arguments" to mapOf(
                            "siteId" to siteId,
                            "translations" to mapOf(
                                "en" to mapOf("title" to "Batch EN", "body" to "Body", "slug" to "batch-publish-en"),
                                "es" to mapOf("title" to "Batch ES", "body" to "Cuerpo", "slug" to "batch-publish-es")
                            )
                        )
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!

        @Suppress("UNCHECKED_CAST")
        val postId = extractPostId(createResult["result"] as Map<String, Any>)
        val enVersionId = getLatestVersionId(editorToken, postId, "en")
        val esVersionId = getLatestVersionId(editorToken, postId, "es")

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 2, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "publish",
                        "arguments" to mapOf(
                            "siteId" to siteId,
                            "postId" to postId,
                            "versions" to mapOf("en" to enVersionId, "es" to esVersionId)
                        )
                    )
                )
            )
            .exchange()
            .expectStatus().isOk

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 3, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "get_post",
                        "arguments" to mapOf("siteId" to siteId, "lang" to "en", "slug" to "batch-publish-en")
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("Batch EN")) { "expected en live in $text" }
            }

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 4, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "get_post",
                        "arguments" to mapOf("siteId" to siteId, "lang" to "es", "slug" to "batch-publish-es")
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("Batch ES")) { "expected es live in $text" }
            }
    }

    @Test
    fun `tools call publish requires either lang+versionId or versions`() {
        val editorToken = createServiceAccountGrantedOnSite("mcp-editor-${System.currentTimeMillis()}", siteId, "editor")
        val postId = createDraftViaMcp(editorToken, "No Version Given", "no-version-given")

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 2, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "publish",
                        "arguments" to mapOf("siteId" to siteId, "postId" to postId)
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.error.code").isEqualTo(-32602)
    }

    @Test
    fun `tools call edit with coverUrl changes the live post immediately, no draft involved`() {
        val postId = createDraftViaMcp(serviceAccountToken, "Cover Test", "cover-test-post")

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 2, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "edit",
                        "arguments" to mapOf(
                            "siteId" to siteId,
                            "postId" to postId,
                            "coverUrl" to "https://example.com/cover.jpg"
                        )
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("https://example.com/cover.jpg")) { "expected the new cover in $text" }
            }
    }

    @Test
    fun `tools call unpublish is forbidden for a writer-role service account`() {
        val postId = createDraftViaMcp(serviceAccountToken, "Writer Cannot Unpublish", "writer-cannot-unpublish")

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 2, "method" to "tools/call",
                    "params" to mapOf("name" to "unpublish", "arguments" to mapOf("siteId" to siteId, "postId" to postId))
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.error.code").isEqualTo(-32002)
    }

    @Test
    fun `tools call unpublish takes a post down without losing its content`() {
        val editorToken = createServiceAccountGrantedOnSite("mcp-editor-${System.currentTimeMillis()}", siteId, "editor")
        val postId = createDraftViaMcp(editorToken, "Unpublish Me", "unpublish-post")
        val versionId = getLatestVersionId(editorToken, postId, "en")

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 2, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "publish",
                        "arguments" to mapOf("siteId" to siteId, "postId" to postId, "lang" to "en", "versionId" to versionId)
                    )
                )
            )
            .exchange()
            .expectStatus().isOk

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 3, "method" to "tools/call",
                    "params" to mapOf("name" to "unpublish", "arguments" to mapOf("siteId" to siteId, "postId" to postId))
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("\"status\":\"DRAFT\"")) { "expected draft status in $text" }
            }

        // Taken down — no longer reachable as a published post.
        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 4, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "get_post",
                        "arguments" to mapOf("siteId" to siteId, "lang" to "en", "slug" to "unpublish-post")
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.error.code").isEqualTo(-32001)

        // Content and version history survive — publishing the same version brings it right back.
        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 5, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "publish",
                        "arguments" to mapOf("siteId" to siteId, "postId" to postId, "lang" to "en", "versionId" to versionId)
                    )
                )
            )
            .exchange()
            .expectStatus().isOk

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 6, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "get_post",
                        "arguments" to mapOf("siteId" to siteId, "lang" to "en", "slug" to "unpublish-post")
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("Unpublish Me")) { "expected the post live again in $text" }
            }
    }

    @Test
    fun `tools call schedule sets a future publish time`() {
        val editorToken = createServiceAccountGrantedOnSite("mcp-editor-${System.currentTimeMillis()}", siteId, "editor")
        val postId = createDraftViaMcp(editorToken, "Scheduled Post", "scheduled-post")

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 2, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "schedule",
                        "arguments" to mapOf("siteId" to siteId, "postId" to postId, "scheduledAt" to "2099-01-01T09:00:00Z")
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("\"status\":\"SCHEDULED\"")) { "expected scheduled status in $text" }
                assert(text.contains("2099-01-01")) { "expected the scheduled time in $text" }
            }
    }

    @Test
    fun `tools call update_site_config is forbidden for a non-admin service account`() {
        val editorToken = createServiceAccountGrantedOnSite("mcp-editor-${System.currentTimeMillis()}", siteId, "editor")

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 1, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "update_site_config",
                        "arguments" to mapOf("siteId" to siteId, "customCss" to "body { color: red; }")
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.error.code").isEqualTo(-32002)
    }

    @Test
    fun `tools call update_site_config merges its patch without touching headHtml or the other language`() {
        // Seed headHtml and an "es" config by hand, as the human owner — update_site_config must
        // never see or overwrite these.
        webTestClient.patch().uri("/sites/$siteId")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "config" to mapOf(
                        "headHtml" to "<script>trackingPixel()</script>",
                        "faviconUrl" to "https://example.com/old-favicon.png",
                        "en" to mapOf("title" to "Old EN Title", "footer" to "Old footer"),
                        "es" to mapOf("title" to "Titulo ES", "footer" to "Pie de pagina")
                    )
                )
            )
            .exchange()
            .expectStatus().isOk

        val adminToken = createServiceAccountGrantedOnSite("mcp-admin-${System.currentTimeMillis()}", siteId, "admin")

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $adminToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 1, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "update_site_config",
                        "arguments" to mapOf(
                            "siteId" to siteId,
                            "customCss" to "body { color: blue; }",
                            "faviconUrl" to "https://example.com/new-favicon.png",
                            "en" to mapOf("title" to "New EN Title")
                        )
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("New EN Title")) { "expected the new en title in $text" }
                assert(!text.contains("headHtml") || text.contains("trackingPixel")) {
                    "expected headHtml to survive untouched in $text"
                }
            }

        val site = webTestClient.get().uri("/sites/$siteId")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!

        @Suppress("UNCHECKED_CAST")
        val config = site["config"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val en = config["en"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val es = config["es"] as Map<String, Any?>

        assert(config["headHtml"] == "<script>trackingPixel()</script>") { "headHtml was touched: ${config["headHtml"]}" }
        assert(config["faviconUrl"] == "https://example.com/new-favicon.png") { "favicon wasn't updated: ${config["faviconUrl"]}" }
        assert(en["title"] == "New EN Title") { "en.title wasn't updated: ${en["title"]}" }
        assert(en["footer"] == "Old footer") { "en.footer was wiped even though it wasn't in the patch: ${en["footer"]}" }
        assert(es["title"] == "Titulo ES") { "es config was wiped by an en-only patch: ${es["title"]}" }
        assert(site["customCss"] == "body { color: blue; }") { "customCss wasn't updated: ${site["customCss"]}" }
    }

    @Test
    fun `tools call list_docs returns the theming guide slug`() {
        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 1, "method" to "tools/call",
                    "params" to mapOf("name" to "list_docs", "arguments" to emptyMap<String, Any>())
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("\"slug\":\"guides/theming\"")) { "expected the theming guide slug in $text" }
            }
    }

    @Test
    fun `tools call get_doc returns the theming guide's markdown`() {
        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 1, "method" to "tools/call",
                    "params" to mapOf("name" to "get_doc", "arguments" to mapOf("slug" to "guides/theming"))
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("--copy-btn-bg")) { "expected a CSS variable name in $text" }
                assert(text.contains("post-card__title")) { "expected a documented selector in $text" }
            }
    }

    @Test
    fun `tools call get_doc with an unknown slug returns a not-found JSON-RPC error`() {
        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 1, "method" to "tools/call",
                    "params" to mapOf("name" to "get_doc", "arguments" to mapOf("slug" to "guides/does-not-exist"))
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.error.code").isEqualTo(-32001)
    }

    @Test
    fun `tools call create_draft with a postId is rejected as invalid params`() {
        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 1, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "create_draft",
                        "arguments" to mapOf(
                            "siteId" to siteId,
                            "postId" to 999,
                            "translations" to mapOf("en" to mapOf("title" to "x", "body" to "y"))
                        )
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.error.code").isEqualTo(-32602)
    }

    @Test
    fun `tools call for a site the account has no access to returns a not-found JSON-RPC error`() {
        val otherSiteId = createSite("Other Blog", "othermcp-${System.currentTimeMillis()}.example.com")

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 1, "method" to "tools/call",
                    "params" to mapOf("name" to "list_tags", "arguments" to mapOf("siteId" to otherSiteId))
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.error.code").isEqualTo(-32001)
    }

    @Test
    fun `tools call with an unknown tool name returns a method-not-found JSON-RPC error`() {
        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 1, "method" to "tools/call",
                    "params" to mapOf("name" to "delete_everything", "arguments" to emptyMap<String, Any>())
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.error.code").isEqualTo(-32601)
    }

    @Test
    fun `a request on a non-home domain is rejected before auth is even checked`() {
        // No Authorization header either — if the host check didn't run first, this would come
        // back 401 (unauthorized) instead of 404 (host rejected).
        webTestClient.post().uri("/mcp")
            .header("X-Site-Host", "test.writeinone.com")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("jsonrpc" to "2.0", "id" to 1, "method" to "initialize"))
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `a request without a bearer token is unauthorized`() {
        webTestClient.post().uri("/mcp")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("jsonrpc" to "2.0", "id" to 1, "method" to "initialize"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractPostId(toolResult: Map<String, Any>): Long {
        val content = (toolResult["content"] as List<Map<String, Any>>)[0]
        val text = content["text"] as String
        val postIdMatch = Regex("\"post\":\\{\"id\":(\\d+)").find(text)
            ?: error("could not find post id in $text")
        return postIdMatch.groupValues[1].toLong()
    }

    @Suppress("UNCHECKED_CAST")
    private fun createSite(name: String, domain: String): Long {
        val body = webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name, "domain" to domain))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!
        return (body["id"] as Number).toLong()
    }

    @Suppress("UNCHECKED_CAST")
    private fun createServiceAccountGrantedOnSite(name: String, siteId: Long, role: String = "writer"): String {
        val created = webTestClient.post().uri("/service-accounts/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!

        val serviceAccountId = (created["id"] as Number).toLong()
        val token = created["token"] as String

        webTestClient.post().uri("/sites/$siteId/invitations/service-account")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("serviceAccountId" to serviceAccountId, "role" to role))
            .exchange()
            .expectStatus().isOk

        return token
    }

    private fun createDraftViaMcp(token: String, title: String, slug: String): Long {
        val result = webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 1, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "create_draft",
                        "arguments" to mapOf(
                            "siteId" to siteId,
                            "translations" to mapOf("en" to mapOf("title" to title, "body" to "Body", "slug" to slug))
                        )
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!

        @Suppress("UNCHECKED_CAST")
        return extractPostId(result["result"] as Map<String, Any>)
    }

    @Suppress("UNCHECKED_CAST")
    private fun getLatestVersionId(token: String, postId: Long, lang: String): Long {
        val result = webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 1, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "list_versions",
                        "arguments" to mapOf("siteId" to siteId, "postId" to postId, "lang" to lang)
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!

        val text = ((result["result"] as Map<String, Any>)["content"] as List<Map<String, Any>>)[0]["text"] as String
        return Regex("\"id\":(\\d+)").find(text)?.groupValues?.get(1)?.toLong()
            ?: error("could not find a version id in $text")
    }
}
