package com.gonzalinux.api

import com.gonzalinux.api.AuthHandler.Companion.ACCESS_TOKEN_COOKIE
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.OffsetDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PostIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var db: DatabaseClient

    private var accessTokenCookie: String = ""
    private var siteId: Long = 0L

    @BeforeEach
    fun setup() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

        val cookies = webTestClient.post().uri("/auth/register")
            .bodyValue(mapOf(
                "email" to "posttest@integrationtest.com",
                "displayName" to "Test User",
                "password" to "password123"
            ))
            .exchange()
            .returnResult(String::class.java)
            .responseCookies
        accessTokenCookie = cookies.getFirst(ACCESS_TOKEN_COOKIE)?.value ?: ""

        siteId = createSite("Post Test Blog", "posttest.example.com")
    }

    @AfterEach
    fun cleanup() {
        db.sql("DELETE FROM users WHERE email LIKE '%@integrationtest.com'").then().block()
    }

    @Test
    fun `create post returns 200 with translations`() {
        webTestClient.post().uri("/sites/$siteId/posts/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .bodyValue(mapOf(
                "translations" to mapOf(
                    "en" to mapOf("title" to "Hello World", "body" to "First post body")
                )
            ))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.post.id").isNumber
            .jsonPath("$.post.status").isEqualTo("DRAFT")
            .jsonPath("$.translations[0].title").isEqualTo("Hello World")
            .jsonPath("$.translations[0].lang").isEqualTo("en")
    }

    @Test
    fun `create post auto-generates slug from title`() {
        webTestClient.post().uri("/sites/$siteId/posts/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .bodyValue(mapOf(
                "translations" to mapOf(
                    "en" to mapOf("title" to "My Awesome Post!", "body" to "Content")
                )
            ))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.translations[0].slug").isEqualTo("my-awesome-post")
    }

    @Test
    fun `create post with tags returns 200 with tags in response`() {
        webTestClient.post().uri("/sites/$siteId/posts/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .bodyValue(mapOf(
                "translations" to mapOf("en" to mapOf("title" to "Tagged Post", "body" to "Content")),
                "tags" to listOf("kotlin", "spring")
            ))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.tags.length()").isEqualTo(2)
    }

    @Test
    fun `create post returns 404 when site not found`() {
        webTestClient.post().uri("/sites/999999/posts/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .bodyValue(mapOf(
                "translations" to mapOf("en" to mapOf("title" to "Post", "body" to "Content"))
            ))
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `get post returns 200 when found`() {
        val postId = createPost("Get Post", "Get content")

        webTestClient.get().uri("/sites/$siteId/posts/$postId")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.post.id").isEqualTo(postId)
            .jsonPath("$.translations[0].title").isEqualTo("Get Post")
    }

    @Test
    fun `get post returns 404 when not found`() {
        webTestClient.get().uri("/sites/$siteId/posts/999999")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `list posts returns 200 with page wrapper`() {
        createPost("Post A", "Content A")
        createPost("Post B", "Content B")

        webTestClient.get().uri("/sites/$siteId/posts/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content").isArray
            .jsonPath("$.total").isNumber
    }

    @Test
    fun `list posts filters by status`() {
        createPost("Draft Post", "Content")

        webTestClient.get().uri("/sites/$siteId/posts/?status=draft")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content[0].post.status").isEqualTo("DRAFT")
    }

    @Test
    fun `update post returns 200 with updated translation`() {
        val postId = createPost("Original Title", "Original body")

        webTestClient.put().uri("/sites/$siteId/posts/$postId")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .bodyValue(mapOf(
                "translations" to mapOf(
                    "en" to mapOf("title" to "Updated Title", "body" to "Updated body")
                )
            ))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.translations[0].title").isEqualTo("Updated Title")
    }

    @Test
    fun `publish post returns 200 with PUBLISHED status`() {
        val postId = createPost("To Publish", "Content")

        webTestClient.post().uri("/sites/$siteId/posts/$postId/publish")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("PUBLISHED")
            .jsonPath("$.publishedAt").isNotEmpty
    }

    @Test
    fun `unpublish post returns 200 with DRAFT status`() {
        val postId = createPost("To Unpublish", "Content")

        webTestClient.post().uri("/sites/$siteId/posts/$postId/publish")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk

        webTestClient.post().uri("/sites/$siteId/posts/$postId/unpublish")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("DRAFT")
    }

    @Test
    fun `schedule post returns 200 with SCHEDULED status`() {
        val postId = createPost("To Schedule", "Content")
        val scheduledAt = OffsetDateTime.now().plusDays(7).toString()

        webTestClient.post().uri("/sites/$siteId/posts/$postId/schedule")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .bodyValue(mapOf("scheduledAt" to scheduledAt))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("SCHEDULED")
            .jsonPath("$.scheduledAt").isNotEmpty
    }

    @Test
    fun `delete post returns 200 and post is no longer accessible`() {
        val postId = createPost("Delete Me", "Content")

        webTestClient.delete().uri("/sites/$siteId/posts/$postId")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk

        webTestClient.get().uri("/sites/$siteId/posts/$postId")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `delete post returns 404 when post not found`() {
        webTestClient.delete().uri("/sites/$siteId/posts/999999")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `list posts returns 401 when not authenticated`() {
        webTestClient.get().uri("/sites/$siteId/posts/")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Suppress("UNCHECKED_CAST")
    private fun createSite(name: String, domain: String): Long {
        val body = webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .bodyValue(mapOf("name" to name, "domain" to domain))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!
        return (body["id"] as Number).toLong()
    }

    @Suppress("UNCHECKED_CAST")
    private fun createPost(title: String, body: String): Long {
        val response = webTestClient.post().uri("/sites/$siteId/posts/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .bodyValue(mapOf(
                "translations" to mapOf("en" to mapOf("title" to title, "body" to body))
            ))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!
        val post = response["post"] as Map<*, *>
        return (post["id"] as Number).toLong()
    }
}
