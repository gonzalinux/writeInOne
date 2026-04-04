package com.gonzalinux.client

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class VerifyClientImplTest {

    private val webClient = mockk<WebClient>()
    private val reqBodyUriSpec = mockk<WebClient.RequestBodyUriSpec>()
    private val reqBodySpec = mockk<WebClient.RequestBodySpec>()
    private val responseSpec = mockk<WebClient.ResponseSpec>()

    private lateinit var impl: VerifyClientImpl

    @BeforeEach
    fun setup() {
        impl = VerifyClientImpl(webClient)
        every { webClient.post() } returns reqBodyUriSpec
        every { reqBodyUriSpec.uri(any<String>()) } returns reqBodySpec
        every { reqBodySpec.retrieve() } returns responseSpec
    }

    @Test
    fun `verify returns true when response matches generated token`() {
        // Return whatever token was stored for this domain — simulates our server responding correctly
        every { responseSpec.bodyToMono(String::class.java) } answers {
            Mono.fromCallable { impl.getToken("blog.example.com") }
        }

        StepVerifier.create(impl.verify("blog.example.com", ""))
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    fun `verify returns false when response does not match token`() {
        every { responseSpec.bodyToMono(String::class.java) } returns Mono.just("wrong-token")

        StepVerifier.create(impl.verify("blog.example.com", ""))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun `verify returns false when response is empty`() {
        every { responseSpec.bodyToMono(String::class.java) } returns Mono.empty()

        StepVerifier.create(impl.verify("blog.example.com", ""))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun `verify removes token after successful verification`() {
        every { responseSpec.bodyToMono(String::class.java) } answers {
            Mono.fromCallable { impl.getToken("blog.example.com") }
        }

        StepVerifier.create(impl.verify("blog.example.com", ""))
            .expectNext(true)
            .verifyComplete()

        assertNull(impl.getToken("blog.example.com"))
    }

    @Test
    fun `verify keeps token after failed verification`() {
        every { responseSpec.bodyToMono(String::class.java) } returns Mono.just("wrong-token")

        StepVerifier.create(impl.verify("blog.example.com", ""))
            .expectNext(false)
            .verifyComplete()

        // Token should still be present for the next scheduler run
        assert(impl.getToken("blog.example.com") != null)
    }

    @Test
    fun `verify builds correct url with prefix`() {
        val capturedUri = mutableListOf<String>()
        every { reqBodyUriSpec.uri(capture(capturedUri)) } returns reqBodySpec
        every { responseSpec.bodyToMono(String::class.java) } returns Mono.just("wrong-token")

        StepVerifier.create(impl.verify("blog.example.com", "myblog"))
            .expectNext(false)
            .verifyComplete()

        assert(capturedUri.first() == "https://blog.example.com/myblog/_verify")
    }

    @Test
    fun `verify builds correct url without prefix`() {
        val capturedUri = mutableListOf<String>()
        every { reqBodyUriSpec.uri(capture(capturedUri)) } returns reqBodySpec
        every { responseSpec.bodyToMono(String::class.java) } returns Mono.just("wrong-token")

        StepVerifier.create(impl.verify("blog.example.com", ""))
            .expectNext(false)
            .verifyComplete()

        assert(capturedUri.first() == "https://blog.example.com/_verify")
    }

    @Test
    fun `getToken returns null when no verification is pending`() {
        assertNull(impl.getToken("unknown.example.com"))
    }

    @Test
    fun `getToken returns token for pending domain`() {
        every { responseSpec.bodyToMono(String::class.java) } returns Mono.just("wrong-token")

        // Start verification but don't complete it — token should be in the map
        impl.verify("blog.example.com", "").subscribe()

        assert(impl.getToken("blog.example.com") != null)
    }
}
