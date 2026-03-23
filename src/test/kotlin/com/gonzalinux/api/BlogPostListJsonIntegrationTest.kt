package com.gonzalinux.api

import com.gonzalinux.api.AuthHandler.Companion.ACCESS_TOKEN_COOKIE
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
class BlogPostListJsonIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var db: DatabaseClient

    private var accessTokenCookie: String = ""
    private var siteId: Long = 0L
    private lateinit var siteDomain: String
    private lateinit var testEmail: String

    @BeforeEach
    fun setup() {
        val ts = System.currentTimeMillis()
        testEmail = "blogtest-$ts@integrationtest.com"
        siteDomain = "blogtest-$ts.example.com"
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

        val result = webTestClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf(
                "email" to testEmail,
                "displayName" to "Blog Tester",
                "password" to "password123"
            ))
            .exchange()
            .expectBody(Map::class.java)
            .returnResult()
        accessTokenCookie = result.responseCookies.getFirst(ACCESS_TOKEN_COOKIE)?.value ?: ""

        siteId = createSite("Blog Test Site", siteDomain)
    }

    @AfterEach
    fun cleanup() {
        db.sql("DELETE FROM users WHERE email LIKE '%@integrationtest.com'").then().block()
    }

    @Test
    fun `returns 200 with empty content when no published posts`() {
        webTestClient.get().uri("/en/posts")
            .header("X-Site-Host", siteDomain)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content").isArray
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(0)
            .jsonPath("$.page").isEqualTo(0)
    }

    @Test
    fun `returns published posts for the correct site`() {
        val postId = createPost("Hello World", "Post body content")
        publishPost(postId)

        webTestClient.get().uri("/en/posts")
            .header("X-Site-Host", siteDomain)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].translation.title").isEqualTo("Hello World")
            .jsonPath("$.totalElements").isEqualTo(1)
    }

    @Test
    fun `does not return draft posts`() {
        createPost("Draft Post", "This is a draft")

        webTestClient.get().uri("/en/posts")
            .header("X-Site-Host", siteDomain)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(0)
    }

    @Test
    fun `respects size and page query params`() {
        repeat(3) { i -> publishPost(createPost("Post $i", "Body $i")) }

        webTestClient.get().uri("/en/posts?page=0&size=2")
            .header("X-Site-Host", siteDomain)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.size").isEqualTo(2)
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(3)
            .jsonPath("$.totalPages").isEqualTo(2)
    }

    @Test
    fun `filters by tag`() {
        val taggedPostId = createPostWithTags("Tagged Post", "Content", listOf("kotlin"))
        publishPost(taggedPostId)
        val untaggedPostId = createPost("Untagged Post", "Content")
        publishPost(untaggedPostId)

        webTestClient.get().uri("/en/posts?tag=kotlin")
            .header("X-Site-Host", siteDomain)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].translation.title").isEqualTo("Tagged Post")
    }

    @Test
    fun `filters by search term`() {
        val matchingPostId = createPost("Kotlin Tips", "Learn Kotlin today")
        publishPost(matchingPostId)
        val otherPostId = createPost("Java Guide", "Java content")
        publishPost(otherPostId)

        webTestClient.get().uri("/en/posts?search=Kotlin")
            .header("X-Site-Host", siteDomain)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].translation.title").isEqualTo("Kotlin Tips")
    }

    @Test
    fun `returns posts for spanish lang`() {
        val postId = createPostWithLang("Hola Mundo", "Cuerpo del post", "es")
        publishPost(postId)

        webTestClient.get().uri("/es/posts")
            .header("X-Site-Host", siteDomain)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].translation.title").isEqualTo("Hola Mundo")
    }

    @Test
    fun `does not return posts from a different site`() {
        val ts = System.currentTimeMillis()
        val otherDomain = "other-$ts.example.com"
        createSite("Other Site", otherDomain)

        val postId = createPost("My Post", "Content")
        publishPost(postId)

        // Querying with the other site's domain should return no posts
        webTestClient.get().uri("/en/posts")
            .header("X-Site-Host", otherDomain)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(0)
    }

    @Test
    fun `returns landing page when no site matches the host`() {
        webTestClient.get().uri("/en/posts")
            .header("X-Site-Host", "nonexistent.example.com")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
    }

    // --- helpers ---

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
    private fun createPost(title: String, body: String): Long =
        createPostWithLang(title, body, "en")

    @Suppress("UNCHECKED_CAST")
    private fun createPostWithLang(title: String, body: String, lang: String): Long {
        val response = webTestClient.post().uri("/sites/$siteId/posts/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("translations" to mapOf(lang to mapOf("title" to title, "body" to body))))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!
        return ((response["post"] as Map<*, *>)["id"] as Number).toLong()
    }

    @Suppress("UNCHECKED_CAST")
    private fun createPostWithTags(title: String, body: String, tags: List<String>): Long {
        val response = webTestClient.post().uri("/sites/$siteId/posts/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf(
                "translations" to mapOf("en" to mapOf("title" to title, "body" to body)),
                "tags" to tags
            ))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!
        return ((response["post"] as Map<*, *>)["id"] as Number).toLong()
    }

    private fun publishPost(postId: Long) {
        webTestClient.post().uri("/sites/$siteId/posts/$postId/publish")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
    }
}
