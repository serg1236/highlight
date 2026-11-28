# `highlight-backend`

Spring Boot **4.2.0** / **Java 21** REST API that ingests novels (EPUB, FB2, TXT) and uses a cheap Claude model (Claude
Haiku 5.5 by default) to build the material an illustration pipeline needs: a cited character catalogue, a scene index,
a location catalogue and the book's world setting. For each named character who speaks, acts or is described, it stores
name aliases, sex (`MALE`, `FEMALE` or `UNDEFINED` when the text doesn't make it clear), plus appearance, clothing and
role (occupation, social class) observations. Appearance and clothing are the priority, with each garment and accessory
as its own observation; role is limited to one occupation and one social class per character per chunk. Appearance and
clothing must be stated by the text; role and age may be implied by it and are then flagged `inferred`. Each observation
carries the verbatim source quote and its character offset in the book text, so later steps can ask "what does this
character look like at offset N". Offsets always come from searching the book text for the quote, never from the model.
An observation whose quote can't be found is kept without offsets and is ignored by state-at-offset queries. Scenes,
locations and the setting (realism, period, region, technology, clothing norms, architecture, mood and a short
`promptLine` for image prompts) follow the same rule: offsets come from located quotes, and the period is marked `CITED`
or `INFERRED`.

For AI/agent conventions (rules, skills), see [`CLAUDE.md`](../CLAUDE.md).

## Behavior

- **Entry:** [`BookController`](src/main/java/com/highlight/controller/BookController.java) and [
  `CharacterController`](src/main/java/com/highlight/controller/CharacterController.java):
    - `POST /api/books` (multipart field `file`) parses and stores the book, then returns **202** with status
      `UPLOADED`.
    - `GET /api/books`, `GET /api/books/{id}` show status, progress (`chunksDone/chunksTotal`), tokens and `costUsd`.
    - `DELETE /api/books/{id}` deletes the book and everything extracted from it.
    - `GET /api/books/{id}/characters` lists characters with aliases and observations.
    - `GET /api/characters/{id}/state?offset=N` returns the latest appearance, clothing and role observation per
      category at or before offset `N`.
    - `GET /api/books/{id}/scenes?dialogOnly=false` lists scenes in reading order with offsets, summary, location, time
      of day, timeline shift and the characters present (and whether they speak).
    - `GET /api/books/{id}/locations` lists locations with one merged visual description each.
    - `GET /api/books/{id}/setting` returns the world setting with the cited clues it was built from; **404** until
      extraction has produced it.
- **Startup:**
    - [`AnthropicProperties`](src/main/java/com/highlight/config/AnthropicProperties.java) and [
      `ExtractionProperties`](src/main/java/com/highlight/config/ExtractionProperties.java) are validated at bind time;
      the app does not start without `ANTHROPIC_API_KEY`.
    - Flyway applies [`db/migration`](src/main/resources/db/migration).
    - [`InterruptedExtractionRecovery`](src/main/java/com/highlight/service/support/InterruptedExtractionRecovery.java)
      marks books left in `UPLOADED`, `EXTRACTING_CHARACTERS` or `EXTRACTING_SCENES` as `FAILED`, because jobs run in
      memory and a restart interrupts them.
- **Per upload:** the parser is chosen by file extension:
    - [`EpubBookParser`](src/main/java/com/highlight/parser/EpubBookParser.java): spine documents become chapters.
    - [`Fb2BookParser`](src/main/java/com/highlight/parser/Fb2BookParser.java): the innermost sections of the main body
      become chapters.
    - [`TxtBookParser`](src/main/java/com/highlight/parser/TxtBookParser.java): UTF-8 only; "Chapter N" / "Розділ N" /
      roman-numeral headings start chapters; Project Gutenberg framing is stripped.

  Text is normalized to paragraphs separated by a blank line. After the upload transaction commits, [
  `ExtractionJobLauncher`](src/main/java/com/highlight/service/support/ExtractionJobLauncher.java) starts the job on a
  dedicated executor.
- **Extraction job** ([
  `BookExtractionServiceImpl`](src/main/java/com/highlight/service/impl/BookExtractionServiceImpl.java)):
    1. [`BookChunker`](src/main/java/com/highlight/service/support/BookChunker.java) packs whole chapters into chunks
       twice: large chunks of at most `chunk-max-tokens` for the character pass, and small chunks of at most
       `scene-chunk-max-tokens` for the scene pass. Tokens are estimated as 4 chars per token, or 2.5 for Cyrillic text
       such as Ukrainian. Oversized chapters are split at paragraph breaks.
    2. Calls use structured output, in two passes. The book's `status` names the pass in progress:
        - **Character pass** (`EXTRACTING_CHARACTERS`), one call at a time: one **character call** ([
          `ClaudeCharacterExtractionClient`](src/main/java/com/highlight/ai/ClaudeCharacterExtractionClient.java)) per
          large chunk. It receives the roster of characters found so far and also reports the excerpt's ISO 639-1
          language.
        - **Scene pass** (`EXTRACTING_SCENES`), in parallel: one **scene call** ([
          `ClaudeSceneExtractionClient`](src/main/java/com/highlight/ai/ClaudeSceneExtractionClient.java)) per small
          chunk. Calls run in waves of up to `scene-parallelism` chunks on a per-book thread pool (`scene-<bookId>-N`).
          Each call receives the complete character roster from the first pass and the location names used by earlier
          waves. Results are applied in chunk order, so `chunksDone` advances in steps of up to one wave. If a call
          fails, the rest of its wave is cancelled and the book is marked `FAILED`. The character merge and world
          synthesis below also run while the status is `EXTRACTING_SCENES`. It returns scenes (start/end quotes,
          summary, characters present, location, time, timeline shift) and up to 8 setting clues. Chunks are kept small
          because the model reports far fewer scenes than there are in a long excerpt. Each scene later drives a
          background, so the prompt asks for a new scene at every change of place or time and at every chapter marker,
          and it skips non-narrative text such as epigraphs.

       Descriptions and location names are always in English, while quotes stay in the book's language.
    3. [`QuoteLocator`](src/main/java/com/highlight/service/support/QuoteLocator.java) resolves each quote to offsets.
    4. When there is more than one chunk, one more Haiku call groups the per-chunk characters into people.
    5. [`CharacterMerger`](src/main/java/com/highlight/service/support/CharacterMerger.java) assembles the final
       characters. A character's sex is the one known value its per-chunk candidates agree on, or `UNDEFINED` if none is
       known or they conflict.
    6. One **world synthesis** call turns all setting clues and location names into the book setting and merges location
       names that refer to the same place. [
       `LocationMerger`](src/main/java/com/highlight/service/support/LocationMerger.java) builds the locations and
       treats the answer defensively.
    7. [`SceneAssembler`](src/main/java/com/highlight/service/support/SceneAssembler.java) builds the scenes: offsets
       from the start and end quotes (falling back to the next scene's start or the chunk end), a first scene marked as
       continuing joins the previous chunk's last scene, and character names are matched to canonical names or aliases.
       Names matching no character or several are skipped with a warning.
    8. The language reported by most chunks replaces the book's metadata `language` when it differs. A metadata value
       that already names that language, such as `en-US` or `English` for `en`, is kept.
    9. Re-running replaces the book's characters, scenes, locations, setting and clues.
- **Cost:** after every call, [`CostCalculator`](src/main/java/com/highlight/service/support/CostCalculator.java) prices
  the API-reported `input_tokens` / `output_tokens` (thinking included) with the configured rates. A call whose prompt
  exceeds `long-context-threshold` is priced entirely at the long-context rates. Totals accumulate on the book as the
  job runs. This is an estimate; the Anthropic Console is the source of truth.
- **Error handling:**
    - Unsupported extension or unparseable file → **400**; unknown id → **404**; file over the size limit → **413**.
    - The SDK retries transient API errors (`max-retries`).
    - Any other job failure, including a refusal or truncated model output from any of the calls, sets the book to
      `FAILED` with `errorMessage`. Nothing is retried automatically.

## Configuration (`application.yml`)

| Key                                                                      | Purpose                                                                                                                            |
|--------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `spring.application.name`                                                | Application name (no env-var override, `highlight-backend`)                                                                        |
| `spring.datasource.url`                                                  | PostgreSQL JDBC URL (`DB_URL`, default `jdbc:postgresql://localhost:5432/highlight`)                                               |
| `spring.datasource.username`                                             | DB user (`DB_USERNAME`, default `highlight`)                                                                                       |
| `spring.datasource.password`                                             | DB password (`DB_PASSWORD`, default empty)                                                                                         |
| `spring.jpa.hibernate.ddl-auto`                                          | `none`; the schema is owned by Flyway (no env-var override)                                                                        |
| `spring.servlet.multipart.max-file-size` / `max-request-size`            | Upload size limit (`MAX_UPLOAD_SIZE`, default `50MB`)                                                                              |
| `highlight.anthropic.api-key`                                            | **Required**: Claude API key (`ANTHROPIC_API_KEY`)                                                                                 |
| `highlight.anthropic.base-url`                                           | Claude API base URL (`ANTHROPIC_BASE_URL`, default `https://api.anthropic.com`)                                                    |
| `highlight.anthropic.model`                                              | Model for extraction and merge (`ANTHROPIC_MODEL`, default `claude-haiku-5-5`)                                                     |
| `highlight.anthropic.max-tokens`                                         | Output cap per call, thinking included (no env-var override, `32000`)                                                              |
| `highlight.anthropic.effort.character`                                   | Effort for character extraction and the character merge, `low`/`medium`/`high`/`xhigh`/`max` (no env-var override, `medium`)       |
| `highlight.anthropic.effort.scene`                                       | Effort for scene extraction and the world synthesis, same values (no env-var override, `low`)                                      |
| `highlight.anthropic.timeout`                                            | Per-request timeout (no env-var override, `5m`)                                                                                    |
| `highlight.anthropic.max-retries`                                        | SDK retries for transient errors (no env-var override, `2`)                                                                        |
| `highlight.anthropic.pricing.input` / `output`                           | USD per 1M tokens for prompts up to the threshold (no env-var override, `0.10` / `0.50`)                                           |
| `highlight.anthropic.pricing.long-context-threshold`                     | Input tokens above which long-context rates apply (no env-var override, `100000`)                                                  |
| `highlight.anthropic.pricing.long-context-input` / `long-context-output` | USD per 1M tokens above the threshold (no env-var override, `0.50` / `2.50`)                                                       |
| `highlight.extraction.chunk-max-tokens`                                  | Estimated book tokens per character call, 1,000–900,000 (no env-var override, `70000`)                                             |
| `highlight.extraction.scene-chunk-max-tokens`                            | Estimated book tokens per scene call, 1,000–900,000; smaller finds more scenes (no env-var override, `20000`)                      |
| `highlight.extraction.concurrent-books`                                  | Books extracted in parallel (no env-var override, `2`)                                                                             |
| `highlight.extraction.scene-parallelism`                                 | Concurrent scene calls per book; at most `concurrent-books × scene-parallelism` model calls run at once (no env-var override, `4`) |

Keep the `pricing` values in sync with the configured model.

## Build and verify

From this directory:

```bash
mvn -B clean verify
```

Tests need no database or API key; the Claude client is tested against WireMock.

## Runtime

Normal operation needs a reachable PostgreSQL database and `ANTHROPIC_API_KEY`:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/highlight
export DB_USERNAME=highlight
export DB_PASSWORD=...
export ANTHROPIC_API_KEY=...
java -jar target/highlight-backend-0.0.1-SNAPSHOT.jar
```

Example flow:

```bash
curl -F file=@pride-and-prejudice.epub http://localhost:8080/api/books
```

```bash
curl http://localhost:8080/api/books/1
```

```bash
curl http://localhost:8080/api/books/1/characters
```

```bash
curl "http://localhost:8080/api/books/1/scenes?dialogOnly=true"
```

```bash
curl http://localhost:8080/api/books/1/setting
```

`mvn -B spring-boot:run` works as a local-dev shortcut. A typical 100–120k-word novel takes 2–3 character chunks plus
5–10 scene chunks (`chunksTotal` counts both) and costs about $0.08–0.16 at the default Haiku rates (the text is read
twice, once by each pass; thinking tokens are billed as output, so `medium` effort on the character pass adds about a
cent). Text in Cyrillic script (such as Ukrainian) needs roughly twice as many tokens.

## Project layout

| Area                     | Location                                                                                                                                                                                                                                                                                           |
|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Entry                    | [`Application.java`](src/main/java/com/highlight/Application.java)                                                                                                                                                                                                                                 |
| Config                   | [`AppConfig`](src/main/java/com/highlight/config/AppConfig.java), [`AnthropicProperties`](src/main/java/com/highlight/config/AnthropicProperties.java), [`ExtractionProperties`](src/main/java/com/highlight/config/ExtractionProperties.java)                                                     |
| HTTP layer               | [`controller/`](src/main/java/com/highlight/controller)                                                                                                                                                                                                                                            |
| Parsing                  | [`parser/`](src/main/java/com/highlight/parser)                                                                                                                                                                                                                                                    |
| Claude calls and prompts | [`ClaudeStructuredCaller`](src/main/java/com/highlight/ai/ClaudeStructuredCaller.java), [`ClaudeCharacterExtractionClient`](src/main/java/com/highlight/ai/ClaudeCharacterExtractionClient.java), [`ClaudeSceneExtractionClient`](src/main/java/com/highlight/ai/ClaudeSceneExtractionClient.java) |
| Services                 | [`service/`](src/main/java/com/highlight/service), [`service/impl/`](src/main/java/com/highlight/service/impl)                                                                                                                                                                                     |
| Extraction helpers       | [`service/support/`](src/main/java/com/highlight/service/support)                                                                                                                                                                                                                                  |
| Entities                 | [`entity/`](src/main/java/com/highlight/entity)                                                                                                                                                                                                                                                    |
| Repositories             | [`repository/`](src/main/java/com/highlight/repository)                                                                                                                                                                                                                                            |
| Schema                   | [`db/migration/`](src/main/resources/db/migration) (`V1__init.sql`, `V2__character_sex.sql`, `V3__scenes_setting_roles.sql`, `V4__extraction_phase_statuses.sql`)                                                                                                                                  |
