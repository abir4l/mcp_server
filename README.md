# mcp_server

A small, hand-written [Model Context Protocol](https://modelcontextprotocol.io) server built with
Spring Boot, for learning how MCP works at the wire level. No MCP SDK is used: the JSON-RPC
envelope, headers and tool arguments are validated by hand.

It implements protocol revision `2026-07-28` over Streamable HTTP and exposes a plain-text notes
file to an LLM through five tools: `read`, `write`, `append`, `update` and `clear`.

## Running

Requires JDK 18+.

```bash
./gradlew bootRun   # serves POST http://127.0.0.1:8080/mcp
./gradlew test
```

Run it from the repo root, since the notes file path is relative to the working directory.

### With Docker

```bash
docker compose up --build
```

The `Dockerfile` only builds the image. Everything about running it lives in `docker-compose.yml`:
the server listens on `127.0.0.1:8080` on your machine, and `./data` is mounted into the
container, so it reads and writes the same `data/db` as a local run.

### Postgres with pgvector

`docker compose up` also starts `pgvector/pgvector:pg19` on `127.0.0.1:5432` (database, user and
password `mcp`; set `POSTGRES_PASSWORD` to change the password). On the first start of an empty
volume it runs `db/init/`: `01-schema.sql` enables the `vector` extension and creates
`faq_chunks`, and `02-seed.sql` loads 24 support-FAQ chunks for a fictional company with their
embeddings.

Embeddings come from Ollama's `nomic-embed-text` (768 dimensions). Documents were embedded with the
`search_document: ` prefix, so embed queries with `search_query: ` and the same model:

```sql
SELECT title, content
FROM faq_chunks
ORDER BY embedding <=> '[...query embedding...]'
LIMIT 3;
```

To change the seed data, edit `db/seed/faq_chunks.json`, run `python3 db/seed/generate_seed.py`
(needs Ollama running with `nomic-embed-text` pulled), then recreate the volume with
`docker compose down -v && docker compose up`.

## Configuration

Settings live in `src/main/resources/application.properties`:

| Property                  | Default                         | Purpose                                  |
|---------------------------|---------------------------------|------------------------------------------|
| `server.port`             | `8080`                          | HTTP port                                |
| `mcp.database-path`       | `data/db`                       | Notes file the tools read and write      |
| `mcp.server.name`         | `mcp_server`                    | `serverInfo.name` reported to clients    |
| `mcp.server.version`      | `0.0.1-SNAPSHOT`                | `serverInfo.version` reported to clients |
| `mcp.allowed-origin-hosts`| `localhost,127.0.0.1,[::1]`     | Browser origins allowed to call the server |

Any of them can be overridden at launch, e.g. `./gradlew bootRun --args='--mcp.database-path=/tmp/notes'`.

## Docs

[docs/mcp-server-guide.md](docs/mcp-server-guide.md) covers the request format, every method and
tool, error handling, and how to bridge the server to an Ollama model.

## License

[MIT](LICENSE)
