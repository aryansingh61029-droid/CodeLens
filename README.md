# Code Plagiarism Checker

A Java 21 / Spring Boot 3 web application for comparing Java, Python, and C/C++
source code. Upload two files, paste two snippets, or analyze a ZIP of submissions.
The app includes a similarity gauge, highlighted source regions, a pairwise matrix,
ranked pairs, MySQL comparison history, REST endpoints, automated tests, Docker packaging, and CI.

## Run locally

Install JDK 21, Maven 3.6.3 or newer, and MySQL 8.4. Create a database and an
application user using a MySQL administrator account (replace the password):

```sql
CREATE DATABASE codelens CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'codelens'@'localhost' IDENTIFIED BY 'YOUR_UNIQUE_PASSWORD';
GRANT ALL PRIVILEGES ON codelens.* TO 'codelens'@'localhost';
```

Set `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` as environment variables before
starting Java. For PowerShell you can copy `.env.example` to `.env`, replace
the placeholders, build the project, then run `./scripts/Start-Local.ps1`.
The script reads `.env` as data and starts Java on port 8082 by default. In this
workspace it can also start the private portable MySQL server when needed.
On another machine install MySQL normally or use Docker Compose.

POSIX example (use your own password, do not commit it):

```sh
export DB_URL='jdbc:mysql://localhost:3306/codelens?connectionTimeZone=UTC'
export DB_USERNAME='codelens'
export DB_PASSWORD='YOUR_UNIQUE_PASSWORD'
```

From this directory:

```sh
mvn clean verify
mvn spring-boot:run
```

Open http://localhost:8080 for Single Comparison. The other pages are
http://localhost:8080/batch.html, http://localhost:8080/samples.html, and
http://localhost:8080/history.html.
The sample library has Java, Python, and C/C++ programs, copy buttons, and links
that load identical, renamed, or unrelated pairs into the comparison page.
The animated gauge respects reduced-motion preferences. Verdict badges use
green (low), amber (medium), and rose (high). Matching spans are highlighted in green.
Alternatively run the packaged JAR:

```sh
java -jar target/app.jar
```

On Windows, if an existing server locks `target/app.jar`, build separately with
`mvn -Dcodelens.build-dir=target-ui verify`, then run
`java -jar target-ui/app.jar --server.port=8081`.

Use Java 21 for building and running. MySQL is required for runtime persistence; no external API key is needed.
The `codelens` database stores completed comparisons in `comparison_runs` and
ranked pairs in `comparison_pairs`. Flyway creates these tables automatically
on startup and tracks migrations in `flyway_schema_history`. Filenames, scores,
verdicts and timestamps persist across restarts; source code is never stored.
History is shared across users of this instance and has no automatic expiry.
This app does not add authentication; protect access to private history before
exposing it to other users. Back up the database according to your retention needs.
Servlet multipart uploads may use temporary files, cleaned up by the container.

## Frontend and Tailwind

The four static HTML pages use **Tailwind CSS 3.4.17 via CDN**, as requested,
plus shared custom components in `styles.css`. Navigation works without a
client-side router. `app.js` uses same-origin relative API URLs, so a deployed
site does not accidentally send comparisons to localhost. It renders code and
filenames with text nodes, never unsanitized HTML. No user submissions are
stored in browser storage. The CDN receives a script request, not submitted code.

The requested CDN is included, and a **precompiled Tailwind stylesheet** is
also checked in at `src/main/resources/static/tailwind.css`. The app therefore
stays styled if the CDN is blocked or offline. Docker rebuilds this stylesheet
in a separate Node stage; the final image contains only Java and the JAR.
Node is not required for a normal Maven build or JAR run.

When changing Tailwind classes outside Docker, install Node 22 or later and run:

```sh
npm ci --ignore-scripts
npm run build:css
mvn clean verify
```

Tailwind recommends compiled CSS for production. For a public launch, you can
remove the CDN script from all four pages and its allowance in `RequestGuard`;
the bundled stylesheet already contains all required classes. It is retained
in this version to meet the requested CDN requirement. The CSP permits that
CDN and its generated inline styles, while blocking embedded inline scripts,
framing and foreign API connections.

## Docker

Install Docker with Compose. Copy `.env.example` to `.env` and replace both
password placeholders with different strong passwords, then:

```sh
docker compose up --build -d
curl http://localhost:8080/actuator/health
docker compose logs -f
docker compose down
```

Compose starts MySQL first, waits for an authenticated database health check,
then starts the application. MySQL is reachable only on the Compose network and
stores data in the persistent `mysql_data` volume. `docker compose down` keeps
this volume. Do not use `down -v` unless you intentionally want to erase history.
Changing `.env` passwords does not change users in an existing MySQL volume;
rotate them with `ALTER USER` as well.

The multi-stage Dockerfile runs tests during its build and packages only the JAR
and Java 21 runtime. The process runs as UID 10001. Compose sets a 512 MiB memory
limit, uses a read-only root filesystem, and provides a temporary filesystem.

To run without Compose, export connection settings for a reachable MySQL server first:

```sh
docker build -t code-plagiarism-checker .
docker run --rm -p 8080:8080 --memory=512m \
  -e DB_URL -e DB_USERNAME -e DB_PASSWORD code-plagiarism-checker
```

## API

Examples use a POSIX shell. On Windows use `curl.exe` and adapt line continuations,
or put JSON in a file and send it with `--data-binary @request.json`.

### Compare two files

```sh
curl --fail-with-body -X POST http://localhost:8080/api/compare \
  -F 'file1=@First.java' -F 'file2=@Second.java'
```

Both programs must be in the same language family. C and C++ share a family.
Extensions: `.java`, `.py`, `.c`, `.h`, `.cc`, `.cpp`, `.cxx`, `.hpp`, `.hh`, `.hxx`.
Language detection uses the filename extension; the server does not compile code.

### Compare pasted snippets

```sh
curl --fail-with-body http://localhost:8080/api/compare \
  -H 'Content-Type: application/json' \
  -d '{"language":"JAVA","code1":"int sum(int a,int b){return a+b;}","code2":"int add(int x,int y){return x+y;}"}'
```

`language` accepts `JAVA`, `PYTHON`, or `CPP` (case insensitive); `py`, `c`, and
`c++` are aliases. File and snippet responses use the same schema:

```json
{
  "similarity": 100.0,
  "verdict": "high",
  "sharedFingerprints": 1,
  "totalFingerprints": 1,
  "first": {
    "name": "Snippet A", "language": "JAVA", "code": "return a;",
    "tokenCount": 3, "fingerprintCount": 1,
    "matches": [{"start": 0, "end": 9}]
  },
  "second": {
    "name": "Snippet B", "language": "JAVA", "code": "return b;",
    "tokenCount": 3, "fingerprintCount": 1,
    "matches": [{"start": 0, "end": 9}]
  }
}
```

This response illustrates comparing `return a;` with `return b;`. Counts depend
on the submitted program. `totalFingerprints` is the union's distinct count,
not the sum of all fingerprint occurrences. Range offsets are UTF-16 indexes,
with inclusive start and exclusive end, compatible with JavaScript `slice`.

### Batch comparison

```sh
curl --fail-with-body http://localhost:8080/api/compare/batch \
  -F 'file=@submissions.zip'
```

Returns `files` in archive order, a symmetric `matrix` of percentages with 100 on
the diagonal, and `pairs` sorted from highest to lowest similarity. Each pair
contains `first`, `second`, `similarity`, and `verdict`. Different-language cells
are `null` and these pairs are omitted from the ranking. Directory entries are
allowed; all regular files must have a supported source extension. Duplicate
entry names, unsafe paths, encrypted ZIPs, and nested archives are rejected.

### Saved history

Every successful comparison returns an `X-Comparison-Id` response header. The
existing comparison JSON response shape remains unchanged. Failed validation
does not create history. A run and all its pairs commit in one transaction;
database failures return a readable 503 rather than silently skipping persistence.

```sh
curl --fail-with-body 'http://localhost:8080/api/history?page=0&size=20'
curl --fail-with-body 'http://localhost:8080/api/history/COMPARISON_UUID'
```

The list returns `items`, `page`, `size`, and `hasMore`, newest first. Each item
contains `id`, `createdAt` (UTC), `type`, `submissionCount`, `pairCount` and
`topSimilarity`. Detail returns a `summary` and ranked `pairs`. A mixed-language
batch with no comparable pairs has a null top score. Page is bounded to 0–1000
and size to 1–50. Unknown UUIDs return 404; malformed UUIDs return 400.
The History page provides pagination and a pair detail view.

### Validation and errors

Errors use HTTP status codes and `application/problem+json`, with `title`,
`status`, and a readable `detail`. For example:

```json
{"type":"about:blank","title":"Bad Request","status":400,"detail":"Both code snippets are required."}
```

* 400: missing/empty/comment-only programs, malformed data, invalid UTF-8,
  unsupported language/type, incompatible languages, or invalid ZIP.
* 413: source over 256 KiB, ZIP over 2 MiB, combined expanded ZIP content over
  2 MiB, more than 20 source files or 100 archive entries, or over 160,000 batch tokens.
* A source over 40,000 lexical tokens is rejected with 400.
* 415: unsupported request media type. 429: all comparison slots are occupied;
  retry after the `Retry-After` delay. 503: database unavailable. 500: generic unexpected failure.

The HTTP multipart envelope limit is 3 MiB; JSON bodies are capped at 4 MiB before
deserialization (individual decoded sources still have the 256 KiB limit).
ZIP limits use actual decompressed bytes, so declared archive sizes cannot bypass
them. ZIP contents are never extracted to filesystem paths. Uploaded code is
never executed, and the frontend renders it as text rather than HTML.

## Architecture and algorithm

`controller` accepts multipart or JSON and returns ProblemDetail errors.
`service` validates input, orchestrates comparisons, and builds result ranges.
`tokenizer` produces normalized tokens with original source offsets.
`fingerprinting` creates and selects hashes. `model` contains immutable records.
`persistence` uses Spring JDBC with parameterized queries; `HistoryService`
wraps each run and its pairs in a database transaction. HikariCP bounds the
connection pool. Flyway SQL migrations evolve the schema without runtime
Hibernate auto-update. Database credentials stay in environment variables.
Static HTML, CSS, and JavaScript are served from Spring Boot's `static` directory.

1. **Normalize:** discard comments and whitespace; map non-keyword identifiers to
   `ID`, number literals to `NUM`, and string/character literals to `STR`. Preserve
   language keywords, punctuation, and operators. String scanning happens before
   comment interpretation inside literals, preserving things such as URLs.
2. **Hash k-grams:** hash each run of seven tokens using a rolling 64-bit polynomial
   hash. Token values use deterministic FNV-style hashing. Integer overflow gives
   arithmetic modulo 2^64. Sliding a k-gram takes constant time after initialization.
3. **Winnow:** slide a window across five consecutive hashes and select its minimum;
   ties select the rightmost occurrence. A monotonic deque makes selection linear.
   Avoid emitting the same position twice. Standard Winnowing guarantees a shared
   fingerprint for an identical normalized region of at least `7 + 5 - 1 = 11`
   tokens. Short programs use a smaller k-gram/window and have weaker evidence.
4. **Score:** compare sets of selected fingerprints, verifying each hash match
   against its normalized token sequence. This avoids counting hash collisions.
   `similarity = 100 * |A intersection B| / |A union B|`, rounded to two decimals.
   Empty/comment-only programs are rejected, so the union is never empty.
5. **Explain:** collect source spans for shared selected k-grams and merge
   overlapping or touching spans. These are matching fingerprint regions, not a
   complete character diff. The score is fingerprint-set Jaccard, not percent of
   lines copied. Verdict: low below 35%, medium from 35% to below 70%, high from 70%.

For fixed k/window, preparation is O(n) time and memory. Pair scoring is O(f),
where f is the number of distinct fingerprints. Batch prepares each file once,
then performs O(m² f) pair work for at most 20 submissions. Input sizes, token
counts, and concurrent requests are bounded.

Renamed code scores identically when its other normalized tokens match. Function
reordering preserves most internal fingerprints, although boundaries can change.
This is a MOSS-inspired implementation of published Winnowing, not the MOSS service.

### Limits of interpretation

This is a lexical checker, not an AST or semantic equivalence engine. Identifier
roles and literal values are intentionally discarded, which can inflate similarity
in short programs or boilerplate. Python indentation is ignored; docstrings and
f-strings are normalized as entire literals. C macros are not expanded and Java
Unicode escapes are not preprocessed. Unusual language syntax may need a full
lexer. No cross-language plagiarism detection or base-code exclusion is provided.
Use scores to prioritize human review, not as proof that plagiarism occurred.

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/codelens?connectionTimeZone=UTC` | MySQL JDBC URL |
| `DB_USERNAME` | `codelens` | Application database user |
| `DB_PASSWORD` | Required, no default | Database password |
| `DB_POOL_SIZE` | `5` | Maximum pooled database connections |
| `MYSQL_ROOT_PASSWORD` | Required by Compose | MySQL container administrator password; Java does not use it |
| `PORT` | `8080` | Listen port, bound to `0.0.0.0` |
| `SPRING_PROFILES_ACTIVE` | `dev` (Docker: `prod`) | Select production settings |
| `LOG_LEVEL` | `INFO` | Root log level in production |
| `APP_MAX_CONCURRENT_REQUESTS` | `2` | Simultaneous API requests; range 1–16 |
| `SERVER_THREADS` | `16` | Tomcat worker threads |
| `SERVER_MAX_CONNECTIONS` | `100` | Connection bound |
| `JAVA_TOOL_OPTIONS` | Docker sets heap to 65% of RAM | JVM configuration |
| `HOST_PORT` | `8080` | Compose host-side port only |

Example POSIX production run:

```sh
PORT=10000 SPRING_PROFILES_ACTIVE=prod java -jar target/app.jar
```

PowerShell:

```powershell
$env:PORT = '10000'
$env:SPRING_PROFILES_ACTIVE = 'prod'
java -jar target/app.jar
```

No secrets are hardcoded. `.env` is ignored by Git and Docker and excluded
from the distributed source ZIP. Spring Boot does not automatically load `.env`:
use the supplied PowerShell launcher, Docker Compose, or export environment
variables explicitly. H2 is a test-only dependency, not a runtime fallback.

The health endpoint includes database connectivity. Readiness at
`/actuator/health/readiness` includes the DB; liveness remains independent of DB
availability so a database outage does not trigger unnecessary restart loops.

## Deploy on Render with MySQL

1. Provision a persistent MySQL 8.4 database with a provider reachable from Render.
   Create the `codelens` database and a user scoped to that database. Use the
   provider's TLS settings; for a trusted CA use `sslMode=VERIFY_IDENTITY` in the
   JDBC URL. A private hostname on another provider may not be reachable from Render.
2. Commit this project to GitHub, with `pom.xml` and `Dockerfile` at the repository
   root. Run `mvn clean verify` first. Never commit `.env` or database data files.
3. In Render choose **New > Web Service**, connect the repository and choose
   **Docker**. Set the root directory if this project is in a larger repository.
4. Use `./Dockerfile` and leave the Docker command empty to use the image entrypoint.
5. Set `SPRING_PROFILES_ACTIVE=prod`, `PORT=10000`, `DB_URL`, `DB_USERNAME`, and
   `DB_PASSWORD` in the service environment. Example URL shape:
   `jdbc:mysql://YOUR_DB_HOST:3306/codelens?connectionTimeZone=UTC&sslMode=VERIFY_IDENTITY`.
   Use a real reachable host, database port and TLS settings from your provider.
6. Give the Java service at least 512 MiB RAM. The database needs its own persistent
   storage and resources. Set the health path to `/actuator/health/readiness`.
7. Deploy. Flyway applies the schema migration before the app accepts requests.
8. Compare a sample, open History, restart the web service and confirm the entry
   remains. Configure backups at the database provider.

The Docker Compose file is for local two-service runs. Render builds the app's
Dockerfile; it does not automatically create MySQL from Compose. The CI workflow
tests the Java code using isolated H2, then starts app plus real MySQL in Compose
and checks health and history without publishing an image.

## Testing

```sh
mvn test
mvn clean verify
```

Tests cover normalization and offsets, quoted comments, Python/C++ strings,
Winnowing tie selection, short input, deterministic hashes, identical programs,
renamed variables, reordered functions, unrelated code, Jaccard symmetry,
API validation, batch matrices, expanded ZIP limits, Actuator health, history
persistence, SQL parameterization, pagination, and transaction rollback. Tests
use the `test` profile with an in-memory H2 database in MySQL compatibility mode.
Runtime and live smoke verification use real MySQL.

## Interview explanation

“I first turn source code into a normalized token stream so changes to names,
comments, and formatting do not dominate the result. I hash overlapping groups
of seven tokens, then Winnowing retains representative local minima. I compare
the resulting sets with Jaccard similarity and verify matching hashes against
the actual normalized tokens. Token offsets let me highlight the corresponding
original source. It detects local structural overlap even if functions move,
but it does not establish semantic equivalence or prove plagiarism.”

## References

* [Winnowing: Local Algorithms for Document Fingerprinting](https://theory.stanford.edu/~aiken/publications/papers/sigmod03.pdf)
* [Spring Boot 3.5 requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
* [Docker on Render](https://render.com/docs/docker)
* [Render health checks](https://render.com/docs/health-checks)

* [Tailwind Play CDN installation and production guidance](https://v3.tailwindcss.com/docs/installation/play-cdn)
