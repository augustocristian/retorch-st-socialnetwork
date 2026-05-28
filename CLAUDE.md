# CLAUDE.md — retorch-st-socialnetwork

This file provides Claude Code with the context needed to work in this repository without re-deriving it each session.

## Project purpose

End-to-end test suite for the [DeathStarBench Social Network](https://github.com/augustocristian/docker-socialnetwork) microservices application, orchestrated with the [RETORCH](https://github.com/giis-uniovi/retorch) framework. The project contains two complementary test suites:

- **Frontend (E2E/browser)** — Selenium WebDriver tests that drive the HTML/Bootstrap UI through Chrome.
- **API** — HTTP-level tests that call the REST endpoints directly through the Nginx gateway.

Both suites share the same RETORCH resource model and run under the same CI pipeline.

---

## System Under Test (SUT)

The SUT lives in the `docker-socialnetwork/` directory (cloned from `https://github.com/augustocristian/docker-socialnetwork` if not already present). It is started by the deployment scripts at runtime:

```bash
# Linux/macOS
./deploy-local.sh

# Windows PowerShell
./deploy-local.ps1
```

The scripts clone the SUT if needed, create the `jenkins_network` Docker network, start all containers, and wait up to 300 seconds for the Nginx gateway to serve the DeathStar UI.

**Default local URL:** `http://localhost:8080`

### Microservices and Nginx routes

| Route prefix | Target service | Method | Notes |
|---|---|---|---|
| `/api/user/register` | user-service | POST (form) | Registers user, redirects to index.html |
| `/api/user/login` | user-service | POST (form) | Sets `login_token` JWT cookie, redirects to main.html |
| `/api/user/follow` | social-graph-service | POST (form) | Follow by `user_name`/`followee_name` or by ID |
| `/api/user/unfollow` | social-graph-service | POST (form) | Unfollow by username or ID |
| `/api/user/get_follower` | social-graph-service | GET | Requires `login_token` cookie; returns follower IDs |
| `/api/user/get_followee` | social-graph-service | GET | Requires `login_token` cookie; returns followee IDs |
| `/wrk2-api/post/compose` | compose-post-service | POST (form) | No auth; params: username, user_id, text, media_ids, media_types, post_type |
| `/wrk2-api/user-timeline/read` | user-timeline-service | GET | No auth; params: user_id, start, stop |
| `/wrk2-api/home-timeline/read` | home-timeline-service | GET | No auth; params: user_id, start, stop |

The home timeline is populated **asynchronously** via RabbitMQ → `write-home-timeline-service`. After `POST /wrk2-api/post/compose`, the post may not appear in the home timeline immediately.

---

## Repository layout

```
src/test/java/giis/socialnetwork/e2e/functional/
├── common/
│   ├── BaseApiClass.java        # HTTP base class (form POST, cookie store, JWT parsing)
│   ├── BaseLoggedClass.java     # Selenium base class (browser lifecycle, shared helpers)
│   └── ElementNotFoundException.java
├── tests/
│   ├── TestNavigation.java      # Browser: page structure and navbar links
│   ├── TestLogin.java           # Browser: register + login flows via forms
│   ├── TestPosts.java           # Browser: compose post and verify in timeline
│   └── api/
│       ├── TestApiUsers.java    # API: register + login, JWT extraction
│       ├── TestApiPosts.java    # API: compose post, read user timeline
│       ├── TestApiSocialGraph.java  # API: follow user, get followers (JWT auth)
│       └── TestApiTimeline.java # API: home timeline with eventual consistency polling
└── utils/
    ├── Navigation.java          # Direct URL navigation helpers
    ├── Waiter.java              # Explicit wait helpers for Social Network pages
    └── Click.java               # Safe click with JS fallback
```

---

## Key dependencies

| Dependency | Purpose |
|---|---|
| JUnit 5 (Jupiter) | Test runner |
| Selenium WebDriver 4.x | Browser automation (frontend tests) |
| Selema | Browser lifecycle manager (wraps SeleManager) |
| Apache HttpClient 4.5.14 | HTTP client (API tests — form-encoded POST, cookie store) |
| Gson 2.14.0 | JSON parsing (API tests, JWT decoding) |
| RETORCH annotations | `@AccessMode` resource declarations for scheduling |
| Log4j2 + SLF4J | Structured logging with per-TJob log files |

---

## RETORCH resource model

Each test declares the resources it accesses with `@AccessMode`. RETORCH uses these to generate a parallel-safe Jenkinsfile (via `RetorchGenerateJenkinfileTest`).

| Resource ID | Represents | Typical access |
|---|---|---|
| `frontend` | Nginx HTML pages (read-only, high concurrency) | `READONLY, concurrency=10, sharing=true` |
| `web-browser` | Chrome browser instance | `READWRITE, concurrency=1, sharing=false` |
| `user` | User accounts in user-mongodb | `READWRITE` (register/login), concurrency=1 |
| `post` | Posts in post-storage-mongodb | `READWRITE` (compose), `READONLY` (timeline reads) |
| `social-graph` | Follow relationships in social-graph-mongodb | `READWRITE` (follow/unfollow), `READONLY` (get followers) |
| `home-timeline` | Home feed data in home-timeline-redis | `READONLY, concurrency=10, sharing=true` |
| `user-timeline` | User timeline data in user-timeline-mongodb | `READONLY, concurrency=10, sharing=true` |

**API tests do not use `web-browser` or `frontend`** — they interact directly with the HTTP layer and can therefore run with higher concurrency for read-only operations.

---

## Configuration

### `src/test/resources/test.properties`
```properties
BROWSER_USER=CHROME
LOCALHOST_URL=http://localhost:8080
```
Both `BaseApiClass` and `BaseLoggedClass` read `LOCALHOST_URL` as the SUT base URL. In CI, `SUT_URL` is passed as a system property to override it.

### `src/test/resources/log4j2.xml`
Log files are written to `target/testlogs/log${sys:TJOB_NAME:-testinglocal}-test.log`. Each parallel TJob writes to a separate file.

### `pom.xml` — per-TJob build directory
```xml
<directory>${project.basedir}/target/${TJOB_NAME}</directory>
```
Each Maven invocation writes compiled classes and surefire reports under `target/{TJOB_NAME}/`, preventing parallel CI builds from corrupting each other's compiled output. A Maven profile (`local-execution`) defaults `TJOB_NAME=local` when the property is absent.

---

## Running tests

### Local — full suite
```bash
# Start the SUT first
./deploy-local.sh        # Linux
./deploy-local.ps1       # Windows

# Run all tests
mvn test
```

### Local — specific class
```bash
mvn test -Dtest=TestApiUsers
mvn test -Dtest=TestNavigation
```

### CI (Jenkins)
Tests are executed by the RETORCH TJob lifecycle scripts:
```bash
.retorch/scripts/tjoblifecycles/tjob-testexecution.sh <TJOB_NAME> <STAGE> <SUT_URL> "<TestClass#method>"
```
which internally runs:
```bash
mvn test -Dtest="<TestClass#method>" -DTJOB_NAME="<TJOB_NAME>" -DSUT_URL="<SUT_URL>"
```

---

## Known issues and design decisions

### Authentication model
The social network uses JWT-based session cookies (`login_token`). The `POST /api/user/login` endpoint sets this cookie via `Set-Cookie` and redirects the browser to `main.html`. API tests use Apache HttpClient with a `BasicCookieStore` — the cookie is automatically sent on subsequent requests to the same host. The JWT payload (base64url-decoded middle segment) contains `user_id` and `username`. `BaseApiClass.loginUser()` extracts `user_id` from the JWT payload without signature verification (the secret is known to be `"secret"`).

### Form-encoded requests (not JSON)
All Social Network API endpoints accept `application/x-www-form-urlencoded` POST bodies, not JSON. `BaseApiClass` uses `UrlEncodedFormEntity` and `BasicNameValuePair` from Apache HttpClient for all mutations. Read operations use plain `HttpGet`.

### Home timeline eventual consistency
After `POST /wrk2-api/post/compose`, the home-timeline-service is updated **asynchronously** via RabbitMQ and `write-home-timeline-service`. `TestApiTimeline.testHomeTimelineShowsFollowedUserPost` polls `GET /wrk2-api/home-timeline/read` (up to `HOME_TIMELINE_TIMEOUT_MS = 15 000 ms`, every `HOME_TIMELINE_POLL_MS = 500 ms`) using `LockSupport.parkNanos` — never `Thread.sleep`, which triggers Sonar rule `java:S2925`. The browser test `TestPosts.testComposePostAppearsInTimeline` uses a refresh-retry loop via `Waiter.waitForPostText`.

### Follow endpoint — no auth required
The `POST /api/user/follow` Lua script has its JWT auth check commented out. Tests can therefore follow users by `user_name`/`followee_name` without being logged in. `GET /api/user/get_follower` does require a valid JWT cookie (it reads the requesting user's `user_id` from the token, not from a query parameter).

### User uniqueness
Each test creates users with a timestamp-based username suffix (e.g., `reg1748430000000`) to avoid conflicts across repeated or parallel runs. Usernames are lower-cased and stripped of non-alphanumeric characters to satisfy the social network's constraints.

### Missing services added to docker-compose
The upstream `docker-compose.yml` lacked `compose-post-redis`, `write-home-timeline-rabbitmq`, and `write-home-timeline-service`. These have been added so that `POST /wrk2-api/post/compose` correctly propagates posts to the home timeline.

### Parallel build isolation
Multiple TJobs run concurrently in CI, each calling `mvn test` in the same workspace. The `<directory>` override in `pom.xml` plus per-TJob log files prevent classpath and log file collisions.

---

## API test helpers (`BaseApiClass`)

### HTTP verbs
- `get(url)` — GET, returns response body as `String`
- `getStatus(url)` — GET, returns HTTP status code
- `postForm(url, params)` — form-encoded POST, returns response body
- `postFormStatus(url, params)` — form-encoded POST, returns HTTP status code

### URL builders
- `userUrl(path)` → `sutUrl + "/api/user" + path`
- `wrk2PostUrl(path)` → `sutUrl + "/wrk2-api/post" + path`
- `wrk2UserTimelineUrl(path)` → `sutUrl + "/wrk2-api/user-timeline" + path`
- `wrk2HomeTimelineUrl(path)` → `sutUrl + "/wrk2-api/home-timeline" + path`

### Payload builders (return `List<NameValuePair>`)
- `registerPayload(firstName, lastName, username, password)`
- `loginPayload(username, password)`
- `composePostPayload(username, userId, text)` — media_ids/types default to `[]`, post_type to `0`
- `followPayload(userName, followeeName)`

### Fixture helpers
- `registerUser(firstName, lastName, username, password)` — returns HTTP status
- `loginUser(username, password)` — clears cookie store, logs in, returns `user_id` from JWT
- `createUser(label)` — derives all fields from `label + unique()`, registers + logs in, returns `user_id`
- `createUserWithName(label)` — same but returns `String[]{username, userId, password}`
- `composePost(username, userId, text)` — returns HTTP status
- `followUser(userName, followeeName)` — returns HTTP status

### Typical test shape (write + verify)
```java
@AccessMode(resID = "user", concurrency = 1, sharing = false, accessMode = "READWRITE")
@AccessMode(resID = "post", concurrency = 1, sharing = false, accessMode = "READWRITE")
@AccessMode(resID = "user-timeline", concurrency = 10, sharing = true, accessMode = "READONLY")
@Test
@DisplayName("GET /wrk2-api/user-timeline/read returns a JSON array containing the composed post")
void testReadUserTimeline() throws IOException {
    String[] user = createUserWithName("timeline");
    String username = user[0];
    long userId = Long.parseLong(user[1]);
    String postText = "Timeline test post " + unique();
    composePost(username, userId, postText);

    JsonArray timeline = getJsonArray(wrk2UserTimelineUrl("/read")
            + "?user_id=" + userId + "&start=0&stop=10");
    Assertions.assertFalse(timeline.isEmpty(), "Timeline must contain the post");
}
```

---

## Code style conventions (for this project)

- **No comments** unless the WHY is non-obvious (constraint, workaround, invariant).
- **`@DisplayName`** must be a human-readable sentence, not the method name.
- **Test data isolation**: each test creates its own user with a timestamp-based unique suffix.
- **`BaseApiClass` helpers**: use `createUser()` / `createUserWithName()` for setup. Do not duplicate form-param construction inline.
- **Assertions**: use `Assertions.assertAll` when checking multiple fields of the same object.
- **Logger**: always use the inherited `log` field; never instantiate a new logger in a test class.
- **No `Thread.sleep`**: use `LockSupport.parkNanos` for polling waits to avoid Sonar rule `java:S2925`.
