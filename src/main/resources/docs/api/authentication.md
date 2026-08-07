# Authentication

The API is cookie-based: a successful login or verification sets two `HttpOnly` cookies,
and every protected request (everything under `/sites`) is authenticated by sending them
back — there's no bearer token to attach manually.

| Cookie | Path | Lifetime |
|---|---|---|
| `access_token` | `/` | 15 minutes |
| `refresh_token` | `/auth` | 30 days |

## Register

```
POST /auth/register
{ "email": "you@example.com", "displayName": "Your Name", "password": "at-least-4-chars" }
```

Sends a verification code by email. The account can't log in until it's verified.

## Verify email

```
POST /auth/verify-email
{ "email": "you@example.com", "code": "123456" }
```

On success this logs you in directly — the response sets both cookies, same as `/auth/login`.

## Log in

```
POST /auth/login
{ "email": "you@example.com", "password": "your-password" }
```

## Refresh

Access tokens are short-lived by design. When one expires, call:

```
POST /auth/refresh
```

with no body — the `refresh_token` cookie is read from the request. This returns a new
pair of cookies. If the refresh token is missing, expired, or was already rotated, this
returns `401`.

## Log out

```
POST /auth/logout
```

Revokes the current refresh token and clears both cookies.

## Password reset

```
POST /auth/forgot-password   { "email": "you@example.com" }
POST /auth/reset-password    { "email": "you@example.com", "code": "123456", "password": "new-password" }
```

`forgot-password` always returns success regardless of whether the email exists, to avoid
leaking account existence.
