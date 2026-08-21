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
    fun `tools list returns the ten v1 tools`() {
        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("jsonrpc" to "2.0", "id" to 1, "method" to "tools/list"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.tools.length()").isEqualTo(10)
            .jsonPath("$.result.tools[?(@.name == 'create_draft')]").exists()
            .jsonPath("$.result.tools[?(@.name == 'edit')]").exists()
            .jsonPath("$.result.tools[?(@.name == 'list_versions')]").exists()
            .jsonPath("$.result.tools[?(@.name == 'publish')]").exists()
            .jsonPath("$.result.tools[?(@.name == 'publish_version')]").exists()
            .jsonPath("$.result.tools[?(@.name == 'schedule')]").exists()
            .jsonPath("$.result.tools[?(@.name == 'propose_edit')]").doesNotExist()
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

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $serviceAccountToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 2, "method" to "tools/call",
                    "params" to mapOf("name" to "publish", "arguments" to mapOf("siteId" to siteId, "postId" to postId))
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.error.code").isEqualTo(-32002)
    }

    @Test
    fun `tools call publish takes an editor-role account's draft live`() {
        val editorToken = createServiceAccountGrantedOnSite("mcp-editor-${System.currentTimeMillis()}", siteId, "editor")
        val postId = createDraftViaMcp(editorToken, "Editor Publishes", "editor-publishes")

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 2, "method" to "tools/call",
                    "params" to mapOf("name" to "publish", "arguments" to mapOf("siteId" to siteId, "postId" to postId))
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("\"status\":\"PUBLISHED\"")) { "expected published status in $text" }
            }

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 3, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "get_post",
                        "arguments" to mapOf("siteId" to siteId, "lang" to "en", "slug" to "editor-publishes")
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("Editor Publishes")) { "expected the post live in $text" }
            }
    }

    @Test
    fun `tools call publish_version pushes a pending edit live`() {
        val editorToken = createServiceAccountGrantedOnSite("mcp-editor-${System.currentTimeMillis()}", siteId, "editor")
        val postId = createDraftViaMcp(editorToken, "Before Edit", "publish-version-post")

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 2, "method" to "tools/call",
                    "params" to mapOf("name" to "publish", "arguments" to mapOf("siteId" to siteId, "postId" to postId))
                )
            )
            .exchange()
            .expectStatus().isOk

        val editResult = webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 3, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "edit",
                        "arguments" to mapOf(
                            "siteId" to siteId,
                            "postId" to postId,
                            "translations" to mapOf(
                                "en" to mapOf(
                                    "title" to "After Edit",
                                    "body" to "New body",
                                    "slug" to "publish-version-post"
                                )
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
        val editText = ((editResult["result"] as Map<String, Any>)["content"] as List<Map<String, Any>>)[0]["text"] as String
        val versionId = Regex("\"latestVersions\":\\{\"en\":\\{\"id\":(\\d+)").find(editText)
            ?.groupValues?.get(1)?.toLong()
            ?: error("could not find latest en version id in $editText")

        webTestClient.post().uri("/mcp")
            .header("Authorization", "Bearer $editorToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 4, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "publish_version",
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
                    "jsonrpc" to "2.0", "id" to 5, "method" to "tools/call",
                    "params" to mapOf(
                        "name" to "get_post",
                        "arguments" to mapOf("siteId" to siteId, "lang" to "en", "slug" to "publish-version-post")
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.content[0].text").value<String> { text ->
                assert(text.contains("After Edit")) { "expected the published edit live in $text" }
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
}
