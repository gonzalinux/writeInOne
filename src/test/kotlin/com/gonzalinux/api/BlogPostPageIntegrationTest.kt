package com.gonzalinux.api

import com.gonzalinux.api.AuthHandler.Companion.ACCESS_TOKEN_COOKIE
import com.gonzalinux.client.TestEmailClient
import org.assertj.core.api.Assertions.assertThat
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
class BlogPostPageIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var db: DatabaseClient

    @Autowired
    lateinit var emailClient: TestEmailClient

    private var accessTokenCookie: String = ""
    private var siteId: Long = 0L
    private lateinit var siteDomain: String

    @BeforeEach
    fun setup() {
        val ts = System.currentTimeMillis()
        siteDomain = "postpage-$ts.example.com"
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

        accessTokenCookie = webTestClient.registerVerifiedUser(
            emailClient, "postpage-$ts@integrationtest.com", "Post Page Tester"
        )
        siteId = createSite(siteDomain)
    }

    @AfterEach
    fun cleanup() {
        db.sql("DELETE FROM users WHERE email LIKE '%@integrationtest.com'").then().block()
        emailClient.clear()
    }

    @Test
    fun `renders share links pointing at the canonical post url`() {
        publishPost(createPost("Tips & Tricks", "share-me"))

        val html = getPostPage("share-me")
        // escapeQueryParam keeps ':' and '/' (legal in a query); it's '&', '=' and '#' that must not leak
        val postUrl = "https://$siteDomain/en/articles/share-me"

        assertThat(html).contains("class=\"share\"")
        assertThat(html).contains("https://x.com/intent/post?url=$postUrl&amp;text=Tips%20%26%20Tricks")
        assertThat(html).contains("https://www.linkedin.com/sharing/share-offsite/?url=$postUrl")
        assertThat(html).contains("data-url=\"$postUrl\"")
    }

    @Test
    fun `omits share links when the site turns them off`() {
        publishPost(createPost("Quiet Post", "quiet-post"))
        db.sql("UPDATE sites SET config = config || '{\"showShareButtons\": false}'::jsonb WHERE id = :id")
            .bind("id", siteId).then().block()

        assertThat(getPostPage("quiet-post")).doesNotContain("class=\"share\"")
    }

    // --- helpers ---

    private fun getPostPage(slug: String): String =
        webTestClient.get().uri("/en/articles/$slug")
            .header("X-Site-Host", siteDomain)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody!!

    private fun createSite(domain: String): Long {
        val body = webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Post Page Site", "domain" to domain))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!
        val id = (body["id"] as Number).toLong()
        // HostFilter only resolves VERIFIED sites
        db.sql("UPDATE sites SET status = 'VERIFIED' WHERE id = :id").bind("id", id).then().block()
        return id
    }

    private fun createPost(title: String, slug: String): Long {
        val response = webTestClient.post().uri("/sites/$siteId/posts/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("translations" to mapOf("en" to mapOf("title" to title, "body" to "Body", "slug" to slug))))
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
