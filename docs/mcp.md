# TripMind MCP demonstration

TripMind exposes its Java planning engine through a stateless Streamable HTTP MCP server using Spring AI 2.0.1. The MCP tools work without an LLM API key; the connected AI client supplies the conversation.

## Start and connect

Run from the project directory:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=mcp
```

Connect an MCP client supporting Streamable HTTP to `http://127.0.0.1:8080/mcp` (use the configured PORT if different). This is an MCP JSON-RPC endpoint, not a browser page. The `mcp` profile binds the whole application to localhost; MCP is disabled during normal startup. The profile uses the normal database, so plans created in the app are available to the tools.

For clients using the common `mcpServers` configuration format:

```json
{
  "mcpServers": {
    "tripmind": {
      "url": "http://127.0.0.1:8080/mcp"
    }
  }
}
```

Configuration formats differ by client; choose HTTP/Streamable HTTP and enter that URL. No client has been automatically configured.

## Tools

| Tool | Inputs | Result |
| --- | --- | --- |
| `getTripPlan` | `requestId` | Saved budget, itinerary, weather and source labels |
| `compareTripOptions` | `requestId` | Recalculated shorter-stay and budget-style proposals, savings and preserved constraints |
| `searchTripGuidance` | `requestId`, `query` | Destination-scoped guidance with evidence IDs and source URLs |

All tools are read-only and require a plan UUID supplied by the user. No plan enumeration, booking, deletion or automatic revision tool is exposed. Apply a proposal through TripMind's existing UI. Estimates are heuristic, and tourism summaries are curated snapshots with limited coverage.

## Mentor demonstration

1. Create a comfortable four-night Goa trip in the app.
2. Copy the UUID from the saved plan link (`?plan=...`).
3. Ask the connected client: "Use TripMind to inspect plan UUID, compare the savings from returning one day earlier, and find guidance about historical places in Goa. Preserve the traveler's dietary needs."
4. Observe tool discovery and the three tool calls. Compare returned totals with the app, then apply the preferred revision in the app.

The integration test negotiates MCP initialization, discovers tools, executes every tool over HTTP, verifies invalid-ID errors and confirms the saved plan remains unchanged. It runs with offline providers.

## Deployment limit

This profile is a local demo, without authentication or plan ownership enforcement. Local clients knowing a plan UUID can read its details. Remote deployment requires authentication, per-user plan authorization and transport/origin protections before exposing this interface. Do not override the localhost binding for a public deployment.

Protocol configuration follows the [Spring AI stateless MCP server documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stateless-server-boot-starter-docs.html).
