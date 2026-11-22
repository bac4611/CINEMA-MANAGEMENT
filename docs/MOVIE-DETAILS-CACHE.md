# Movie details metadata cache

Both `/movies/details?id=...` and `/movies/details/{id}` use the same local Caffeine
cache, keyed by movie ID. Only immutable public metadata is cached: title, poster,
synopsis, duration, release date, age rating, trailer/formats, language, country,
genres, director names and cast names. No JPA entity or mutable page view model is
stored. Every request creates its own view model.

Default policy: at most 1,000 entries, expiring 15 minutes after the metadata was
loaded (reads do not extend the lifetime). Override through Spring configuration:

```properties
cinema.cache.movie-information.spec=maximumSize=1000,expireAfterWrite=15m
```

The first request loads three metadata queries, plus the schedule and reviews:
five statements. A cache hit loads only the schedule and reviews: two statements.
Logged-in viewers still have a separate fresh account-status query, giving six
statements on a miss and three on a hit. Neither HTML, CSRF, authentication,
reviews/ratings, the seven-day schedule nor booking/payment state is cached.

`DefaultStaffService.createMovie`, `updateMovie` and `deleteMovie` invalidate the
affected movie after successful transaction commit. Updating genre/director/cast
links is part of the same movie-edit operation. Rejected operations and rolled-back
transactions retain the old entry. Reads inside a write transaction bypass the
shared cache so uncommitted metadata cannot leak to other requests. Missing movies
and failed loads are not cached. Atomic per-key loading coalesces concurrent misses.

This is a per-JVM cache; restarting clears it. It is intended for one application
instance. Direct SQL changes or writes from another instance do not trigger local
invalidation and may remain stale until expiration. A future separate editor for
shared people, genres, countries or languages must invalidate every affected movie
after commit as well. Multiple live instances need coordinated invalidation or a
shared cache before relying on immediate updates across instances.

Tests use H2 only. `MovieInformationCacheIntegrationTests` checks actual SQL counts,
both URL variants, new schedules/reviews, fresh account status and CSRF rendering,
successful edits/deletes, rejected edits and rollback. `MovieInformationCacheTests`
checks expiration using a fake clock, per-movie invalidation after commit, missing
or failed loads, and concurrent misses. Existing uncached-query tests execute inside
a write transaction and continue checking the five-query fallback and page content.
