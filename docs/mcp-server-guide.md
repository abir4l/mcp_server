# mcp_server: Client Integration Guide

Context for building an MCP client (e.g. one that bridges an Ollama model to this server).
Everything below reflects the server as implemented; the example responses were captured
from the running server.

## What this server is

A small, hand-written **Model Context Protocol (MCP) server** built with Spring Boot, for
learning how MCP works at the wire level (no MCP SDK; validation is done by hand).

It exposes a single plain-text "notes" file to an LLM through five tools:

| Tool     | What it does                                                  | Arguments                                         |
|----------|---------------------------------------------------------------|---------------------------------------------------|
| `read`   | Returns the entire contents of the notes file                 | none                                              |
| `write`  | **Replaces** the entire file with the given content           | `content` (string, required)                      |
| `append` | Adds the given content to the end of the file                 | `content` (string, required)                      |
| `update` | Replaces every exact match of `search` and reports the count  | `search` (string, required), `replace` (string, required) |
| `clear`  | Empties the file                                              | none                                              |

The file lives at `data/db` by default (`mcp.database-path` in `application.properties`), resolved
relative to the working directory, so run the server from the repo root.
If it doesn't exist, every tool returns an `isError: true` result naming the absolute path it looked for;
no tool recreates it. `update` also returns `isError: true` when `search` is empty or not found.

## Protocol summary

| Item              | Value                                                            |
|-------------------|------------------------------------------------------------------|
| MCP revision      | **`2026-07-28`** (the only supported version)                    |
| Transport         | Streamable HTTP, JSON responses only (no SSE)                    |
| Endpoint          | `POST http://127.0.0.1:8080/mcp`                                 |
| Message format    | JSON-RPC 2.0, one request per HTTP POST (no batching)            |
| State             | **Stateless**: no `initialize` handshake, no sessions            |
| Capabilities      | `tools` only (no resources, prompts, subscriptions, pagination)  |

> **Important if you know older MCP:** revision 2026-07-28 removed the
> `initialize` / `notifications/initialized` handshake. Calling `initialize` returns
> `-32601 Method not found`. Instead, **every request** carries its protocol version
> and client capabilities in `params._meta`, plus mirrored HTTP headers.

The server binds to `127.0.0.1` only, so use `127.0.0.1`, not `localhost` (which may
resolve to IPv6 `::1` and fail to connect).

## Building a valid request

Every request needs **three things**: the JSON-RPC envelope, `params._meta`, and HTTP headers.

### 1. JSON-RPC envelope

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": { ... }
}
```

- `jsonrpc` must be exactly `"2.0"`.
- `id` must be a **string or integer**, never `null` or a float. It is echoed back in the response.
- `params` must be an object.

### 2. `params._meta` (required on every request)

```json
"_meta": {
  "io.modelcontextprotocol/protocolVersion": "2026-07-28",
  "io.modelcontextprotocol/clientCapabilities": {},
  "io.modelcontextprotocol/clientInfo": { "name": "my-ollama-client", "version": "0.1.0" }
}
```

- `protocolVersion` and `clientCapabilities` are **required**. `clientCapabilities` can be `{}`.
- `clientInfo` is optional but recommended.

### 3. HTTP headers

| Header                 | Value                                              | Required for     |
|------------------------|----------------------------------------------------|------------------|
| `Content-Type`         | `application/json`                                 | all              |
| `Accept`               | `application/json, text/event-stream`              | all (per spec)   |
| `MCP-Protocol-Version` | `2026-07-28`, must equal `_meta` protocolVersion   | all              |
| `Mcp-Method`           | must equal the body's `method`                     | all              |
| `Mcp-Name`             | must equal `params.name`                           | `tools/call`     |

Header values must **match the body exactly**; any mismatch or missing header is rejected
with `400` / `-32020`. If a tool name isn't plain ASCII, send
`Mcp-Name: =?base64?<base64 of UTF-8 name>?=`.

Don't send an `Origin` header unless it is `http(s)://localhost`, `127.0.0.1` or `[::1]`
(any port; configurable via `mcp.allowed-origin-hosts`); other origins get `403`. Non-browser clients normally send no `Origin`.

## Methods

### `server/discover`: what does the server support?

Optional for clients, but a good first call / health check.

```http
POST /mcp
Content-Type: application/json
MCP-Protocol-Version: 2026-07-28
Mcp-Method: server/discover

{"jsonrpc":"2.0","id":1,"method":"server/discover","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}
```

```json
{"jsonrpc":"2.0","id":1,"result":{
  "resultType":"complete",
  "supportedVersions":["2026-07-28"],
  "capabilities":{"tools":{}},
  "_meta":{"io.modelcontextprotocol/serverInfo":{"name":"mcp_server","version":"0.0.1-SNAPSHOT"}}
}}
```

### `tools/list`: get the tool definitions

Same request shape with `"method":"tools/list"` and `Mcp-Method: tools/list`.
Do **not** send a `cursor`: there is a single page and any cursor is rejected (`-32602 Invalid cursor`).

```json
{"jsonrpc":"2.0","id":2,"result":{
  "resultType":"complete",
  "tools":[
    {"name":"read","description":"Read all file contents",
     "inputSchema":{"type":"object","properties":{},"required":[],"additionalProperties":false}},
    {"name":"write","description":"Write all contents to file",
     "inputSchema":{"type":"object",
                    "properties":{"content":{"type":"string","description":"Content that gets written"}},
                    "required":["content"],"additionalProperties":false}},
    {"name":"append","description":"Append content to the end of the file", ...},
    {"name":"update","description":"Replace every occurrence of a piece of text in the file", ...},
    {"name":"clear","description":"Delete all contents of the file", ...}
  ],
  "_meta":{"io.modelcontextprotocol/serverInfo":{"name":"mcp_server","version":"0.0.1-SNAPSHOT"}}
}}
```

`inputSchema` is standard JSON Schema and can be passed to an LLM as the tool's parameter schema.

### `tools/call`: run a tool

```http
POST /mcp
Content-Type: application/json
MCP-Protocol-Version: 2026-07-28
Mcp-Method: tools/call
Mcp-Name: write

{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
  "name":"write",
  "arguments":{"content":"buy bread"},
  "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}
}}
```

Success (`read` shown):

```json
{"jsonrpc":"2.0","id":3,"result":{
  "resultType":"complete",
  "content":[{"type":"text","text":"world\nhello\n"}],
  "isError":false,
  "_meta":{"io.modelcontextprotocol/serverInfo":{"name":"mcp_server","version":"0.0.1-SNAPSHOT"}}
}}
```

- `arguments` may be omitted for `read` and `clear`; if present it must be an object (`{}` is fine).
- `write` and `append` return `"Success"`, `update` returns `"Replaced N occurrence(s)"`, `clear` returns `"File cleared"`.
- Tool names are **case-sensitive** (`READ` is an unknown tool).
- `arguments` may only contain declared parameters; extra keys are rejected.

## Tool behaviour details

- **`write` overwrites the whole file.** Use `append` to add a note.
- `write` and `append` store the content followed by a line separator, so writing `"hi"` then
  reading returns `"hi\n"`.
- `update` matches `search` as exact text (not a regex) and replaces every occurrence.
- All arguments must be JSON strings. Numbers, booleans, arrays, objects and `null` are rejected.

## Errors: two different kinds

Your client must handle both. This distinction matters for an LLM loop.

### A. Tool execution errors → `result.isError: true` (HTTP 200)

The call reached the tool but the **arguments were wrong** or the tool failed. This is
a normal `result`, and the text is meant to be **shown to the model** so it can fix
its call and retry.

```json
{"jsonrpc":"2.0","id":4,"result":{
  "resultType":"complete",
  "content":[{"type":"text","text":"Param (content) type doesn't match"}],
  "isError":true,
  "_meta":{...}
}}
```

Messages you can get:

| Text                                          | Cause                                   |
|-----------------------------------------------|-----------------------------------------|
| `Param (content) is required`                 | missing / `null` required argument      |
| `Param (content) type doesn't match`          | wrong JSON type                         |
| `Param (<name>) is not accepted by this tool` | undeclared argument                     |
| `Database file not found at <path>. ...`      | the notes file doesn't exist            |
| `Tool execution failed: Text '<x>' was not found in the file` | `update` search text isn't in the file |
| `Tool execution failed: search must not be empty` | `update` called with `search: ""`   |
| `Tool execution failed: ...`                  | I/O or other failure inside the tool    |

### B. Protocol errors → JSON-RPC `error` object

The **request itself** is wrong; usually a client bug, not something the model can fix.

```json
{"jsonrpc":"2.0","id":5,"error":{"code":-32602,"message":"Unknown tool: nope"}}
```

| HTTP | Code     | Meaning                    | Typical cause                                                        |
|------|----------|----------------------------|---------------------------------------------------------------------|
| 400  | `-32700` | Parse error                | Body isn't valid JSON (no `id` in response)                          |
| 400  | `-32600` | Invalid request            | Missing `jsonrpc`/`method`, `jsonrpc` ≠ `"2.0"`, bad `id`, non-object `params` |
| 400  | `-32602` | Invalid params             | Missing `_meta`, `_meta.protocolVersion` or `_meta.clientCapabilities` |
| 400  | `-32020` | Header mismatch            | Missing `MCP-Protocol-Version`/`Mcp-Method`/`Mcp-Name`, or not matching body |
| 400  | `-32022` | Unsupported protocol version | `error.data` = `{"supported":["2026-07-28"],"requested":"..."}`   |
| 404  | `-32601` | Method not found           | Anything other than `server/discover`, `tools/list`, `tools/call`   |
| 200  | `-32602` | Invalid params (tools/call) | Unknown tool, missing `params.name`, non-object `arguments`, or a `cursor` on `tools/list` |
| 403  | `-32600` | Forbidden origin           | Non-local `Origin` header                                            |
| 405  | n/a      | Method not allowed         | `GET` / `DELETE` on `/mcp` (only `POST` exists)                      |

A response has **either** `result` **or** `error`, never both. `id` is omitted when the
server couldn't read a valid id from the request.

Notifications (a request **without** `id`) get `202 Accepted` with an empty body.
This server doesn't need any notifications from the client.

## Suggested client flow (Ollama bridge)

```
1. tools/list            → get the tool definitions            (once at startup)
2. Convert to Ollama tool format and start the chat
3. Loop:
     ollama /api/chat (messages + tools)
     if the reply has tool_calls:
         for each call → tools/call on this server
         append a role "tool" message with the result text (including isError text)
         call ollama again
     else:
         show the assistant's answer, done
```

### Mapping MCP tools to Ollama tools

Ollama's `/api/chat` takes OpenAI-style function tools. `inputSchema` maps straight to `parameters`:

```json
{
  "type": "function",
  "function": {
    "name": "write",
    "description": "Write all contents to file",
    "parameters": {
      "type": "object",
      "properties": {"content": {"type": "string", "description": "Content that gets written"}},
      "required": ["content"]
    }
  }
}
```

An Ollama reply that wants a tool looks roughly like:

```json
{"message":{"role":"assistant","content":"",
  "tool_calls":[{"function":{"name":"write","arguments":{"content":"buy bread"}}}]}}
```

- `function.name` → `params.name` **and** the `Mcp-Name` header.
- `function.arguments` (already a JSON object in Ollama) → `params.arguments`.
- Send the tool result back as `{"role":"tool","tool_name":"write","content":"<content[0].text>"}`.
  Append the assistant message containing `tool_calls` to the history first.

(Check the Ollama field names against the Ollama version you run; use a tool-capable
model such as `llama3.1`, `qwen2.5` or `qwen3`.)

### Client tips

- **Feed `isError: true` text back to the model** as the tool result rather than throwing.
  Small models often send `content` as a non-string or invent extra keys, and the error text
  lets them correct it.
- **Treat JSON-RPC `error` responses as client bugs**: log them, don't send them to the model
  (except `Unknown tool`, which is worth telling the model so it stops calling a non-existent tool).
- Models sometimes produce tool names with different casing; names must match exactly.
- If the model hallucinates a tool not in `tools/list`, you'll get `-32602 Unknown tool: ...`.
- Remind the model in the system prompt that `write` **replaces** the whole file, e.g.
  *"To add a note, call `append`. Only use `write` to rewrite everything."*

## Quick test with curl

```bash
META='"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}'

# list tools
curl -s http://127.0.0.1:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: tools/list' \
  -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{$META}}"

# read the notes
curl -s http://127.0.0.1:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: tools/call' \
  -H 'Mcp-Name: read' \
  -d "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"read\",\"arguments\":{},$META}}"
```

Start the server with `./gradlew bootRun` (port 8080).

## Not supported (yet)

- `initialize` handshake / legacy (≤ 2025-11-25) clients
- SSE streaming responses, `subscriptions/listen`, list-changed notifications
- Pagination (`cursor`), `structuredContent`, `outputSchema`
- Multi-round-trip `input_required` results (elicitation / sampling)
- Authentication
