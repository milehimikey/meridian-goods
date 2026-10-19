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
- **Slice keys**: `em export <model>.em | jq -r '.model.slices[].key'` lists every slice's
  export key (the `<slice-key>` the gate and read path above take) without reading the DSL.
- **MCP alternative**: `em-mcp` starts a stateless stdio MCP server exposing the contract,
  gate, and read path above (plus full validate/export) as tools instead of shell commands —
  every tool mirrors a CLI `--json` surface byte-for-byte. Handshake and full tool list:
  [docs/mcp.md](https://github.com/milehimikey/em/blob/main/docs/mcp.md).
- **Skills (Claude Code)**: the em plugin provides `/em:event-modeling` (router),
  `/em:discover`, `/em:design`, `/em:implement`, `/em:conform`, and `/em:review`. It is pinned
  in `.claude/settings.json`; register it once per machine with
  `claude plugin marketplace add milehimikey/em@v<version> --scope project` then
  `claude plugin install em@em-<version-with-dashes> --scope project` (`<version>` is the em
  release pinned in `.claude/settings.json`, e.g. `1.14.0` and `em-1-14-0`).
<!-- GENERATED:agent-contract:end -->
