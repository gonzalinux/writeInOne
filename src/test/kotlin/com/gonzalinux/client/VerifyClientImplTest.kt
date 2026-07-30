package com.gonzalinux.client

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class VerifyClientImplTest {

    private val exchangeFunction = mockk<ExchangeFunction>()
    private val requests = mutableListOf<ClientRequest>()

    private lateinit var impl: VerifyClientImpl

    @BeforeEach
    fun setup() {
        requests.clear()
        impl = VerifyClientImpl(WebClient.builder().exchangeFunction(exchangeFunction).build())
    }

    private fun respondWith(body: String?) {
        every { exchangeFunction.exchange(capture(requests)) } answers { Mono.just(response(body)) }
    }

    /** Echoes back whatever token the client just stored — a correctly configured site. */
    private fun respondWithStoredToken(domain: String) {
        every { exchangeFunction.exchange(capture(requests)) } answers { Mono.just(response(impl.getToken(domain))) }
    }

    private fun response(body: String?): ClientResponse =
        ClientResponse.create(HttpStatus.OK)
            .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
            .let { if (body == null) it else it.body(body) }
            .build()

    @Test
    fun `verify returns true when response matches generated token`() {
        respondWithStoredToken("blog.example.com")

        StepVerifier.create(impl.verify("blog.example.com", ""))
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    fun `verify returns false when response does not match token`() {
        respondWith("wrong-token")

        StepVerifier.create(impl.verify("blog.example.com", ""))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun `verify returns false when response is empty`() {
        respondWith(null)

        StepVerifier.create(impl.verify("blog.example.com", ""))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun `verify removes token after successful verification`() {
        respondWithStoredToken("blog.example.com")

        StepVerifier.create(impl.verify("blog.example.com", ""))
            .expectNext(true)
            .verifyComplete()

        assertNull(impl.getToken("blog.example.com"))
    }

    @Test
    fun `verify keeps token after failed verification`() {
        respondWith("wrong-token")

        StepVerifier.create(impl.verify("blog.example.com", ""))
            .expectNext(false)
            .verifyComplete()

        // Token should still be present for the next scheduler run
        assertNotNull(impl.getToken("blog.example.com"))
    }

    @Test
    fun `verify builds correct url with prefix`() {
        respondWith("wrong-token")

        StepVerifier.create(impl.verify("blog.example.com", "myblog"))
            .expectNext(false)
            .verifyComplete()

        assertEquals("http://blog.example.com/myblog/_verify", requests.first().url().toString())
    }

    @Test
    fun `verify builds correct url without prefix`() {
        respondWith("wrong-token")

        StepVerifier.create(impl.verify("blog.example.com", ""))
            .expectNext(false)
            .verifyComplete()

        assertEquals("http://blog.example.com/_verify", requests.first().url().toString())
    }

    @Test
    fun `getToken returns null when no verification is pending`() {
        assertNull(impl.getToken("unknown.example.com"))
    }

    @Test
    fun `getToken returns token for pending domain`() {
        respondWith("wrong-token")

        // Start verification but don't complete it — token should be in the map
        impl.verify("blog.example.com", "").subscribe()

        assertNotNull(impl.getToken("blog.example.com"))
    }
}
