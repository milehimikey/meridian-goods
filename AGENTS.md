<!-- GENERATED:agent-contract:start -->
## Working with an AI agent

This repo uses [em](https://github.com/milehimikey/em) for event modeling. Any implementing
agent — Claude Code or otherwise — should follow this contract:

- **Contract**: `em contract` prints the full implementation contract to stdout — what
  "ready" means, treating the slice doc as the read-only spec, when to stop and hand a gap to
  a human instead of deciding it silently.
- **Gate**: before implementing a slice, verify it's ready —
  `em validate <model>.em --slice-ready <slice-key> --json` and read the JSON document's
  `ready` field (don't infer readiness from the exit code or printed text). Never make the
  gate pass yourself — that's a ratification decision.
- **Read path**: `em export <model>.em --slice <slice-key>` exports just that slice's
  normalized JSON (pattern, fields, doc) to implement against; `em export <model>.em` exports
  the whole model.
- **MCP alternative**: `em-mcp` starts an MCP server exposing the contract, gate, and read
  path above (plus full validate/export) as tools instead of shell commands — see
  [docs/mcp.md](https://github.com/milehimikey/em/blob/main/docs/mcp.md).
<!-- GENERATED:agent-contract:end -->
