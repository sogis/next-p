## What does not belong here
- Application business logic
- One-off local debugging scripts unless committed under `scripts/`

## Architecture
- `haproxy/`: main runtime configuration
- `templates/`: generated config inputs
- `scripts/`: validation or deployment helpers
- `tests/`: config and behavior checks

## Critical invariants
- Production backends must never be removed without replacing routing.
- Config must stay valid for HAProxy version X.
- Generated files must only be edited through their source templates.

## How to work in this repo
- Validate with: `...`
- Run tests with: `...`
- Format with: `...`

## Conventions
- Prefer small config diffs.
- Do not rename public frontend/backend identifiers without updating references.
- Keep environment-specific values isolated in `...`