When I started building WriteInOne, a personal blogging backend in Kotlin with Spring Boot WebFlux,
I obviously went for Spring Security to handle JWTs and refresh tokens. I even had some experience with it from
previous projects that didn't use WebFlux, so it seemed like the best option.

But as I started wiring things up, something felt a bit off.

## Spring Security Does Not Fit Functional Routing

Spring Security was designed for the classic MVC model — `@RestController`, `@RequestMapping`, annotations everywhere.

In that model, routes are scattered across dozens of controller classes, so centralizing security rules in one place makes
sense. You just declare your rules in a `SecurityWebFilterChain` bean:

```kotlin
.authorizeExchange { auth ->
    auth.pathMatchers("/auth/**").permitAll()
    auth.pathMatchers("/sites/**").authenticated()
    auth.anyExchange().authenticated()
}
```

What is the problem? I am using WebFlux functional routing. All my routes already live in one place: the router. So now
I have two places declaring the same information: in the router you can see `POST /sites`, and in the security config
you have to say again that `/sites/**` exists and requires auth. Change one and you have to change the other, and if
you forget, you have a bug.

It is not that Spring Security is wrong — it is just that its goal was to bring all those `@Controller` classes under
some generic rules in a single place.

## What I Did Instead

I had used other frameworks before that handle authorization differently, basically the concept of **Pipelines**. For
example, how it is done in [Elysia.js](https://elysiajs.com/) and [Phoenix](https://www.phoenixframework.org/) (Elixir).
Both let you group routes and apply functions or middleware to those groups, keeping access rules in the same place as
the route declarations they protect. So I tried to do the same in the Kotlin backend.

Basically, public routes or login endpoints stay in an unprotected group, but routes that need a JWT simply have to pass
through a filter first that validates the authorization. If valid, it adds it to the context; if not, it returns a 401
without even reaching the handler.

In the router this looks like:

```kotlin
fun routes() = publicRoutes().and(protectedRoutes())

fun publicRoutes() = route()
    .POST("/auth/register", authHandler::register)
    .POST("/auth/login", authHandler::login)
    .build()

fun protectedRoutes() = route()
    .POST("/sites", siteHandler::create)
    .GET("/sites", siteHandler::list)
    .build()
    .filter(jwtAuthFilter)
```

The filter itself is a plain class implementing `HandlerFilterFunction`:

```kotlin
@Component
class JwtAuthFilter(private val tokenService: TokenService) : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): Mono<ServerResponse> {
        val token = request.cookies()[ACCESS_TOKEN_COOKIE]?.firstOrNull()?.value
            ?: throw UnauthorizedException()

        return Mono.fromCallable { tokenService.getUserIdFromToken(token) }
            .map { userId -> RequestContext(userId, extractRequestId(request)) }
            .onErrorMap { throw UnauthorizedException() }
            .flatMap { reqContext ->
                next.handle(request).contextWrite { it.withRequestContext(reqContext) }
            }
    }
}
```

Here we can see what I mentioned earlier: the `userId` extracted from the token gets written into Reactor's `ContextView`
via a Kotlin extension function (in Java you would just create a function that takes the context and modifies it).
Any handler downstream can then pull it out with `Mono.deferContextual { ctx -> ctx.getRequestContext() }`. No thread
locals, no static holders, just the reactive context flowing through the pipeline.

That same `RequestContext` also gets bridged into MDC. A `ThreadLocalAccessor` registered via Micrometer's
`ContextRegistry` picks it up and calls `MDC.put("userId", ...)` automatically whenever the context propagates to a
thread. The result is that `userId` and `requestId` show up in every log line for that request — just configure your
log pattern to include `%X{userId}` and it is there.

By the way, `HandlerFilterFunction` is a WebFlux interface that has absolutely no dependency on Spring Security or any
other framework. Which means it can be tested in isolation, injected into any route group that needs it, and composed
with other filters.

To summarize: protected routes go into `protectedRoutes()`. Public routes go into `publicRoutes()`. The rule is defined
in the same place as the routes. Easy to understand, hard to break.

Want role based access? Add a new filter that validates the role and create `adminRoutes()`. The pattern is that
flexible.

## The Downsides

Yes, stepping outside Spring Security conventions has a cost:

- No `@PreAuthorize` or method level security annotations
- No automatic integration with `ReactiveSecurityContextHolder`, which is why we build our own
- Less familiar to developers who expect the standard setup

For teams that already rely heavily on `@PreAuthorize`, the migration cost would be real. But if you are starting
something from scratch, there is almost nothing to give up.

## Conclusion

Spring Security is a powerful framework solving real problems, but the biggest problems it solves are those tied to the
annotation system of the MVC model. If you switch to functional routing, the router should be your single source of
truth.

Keep your security where your routes live. It is simpler, it is explicit, and when something breaks at 2am you will
know exactly where to look.
