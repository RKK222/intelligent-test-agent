---
name: code-update-handoff
description: Use when finishing or pausing any code-change batch in this repo so the handoff stays candid, a `Not done yet` section is always included, and `.agents/session-log.md` is updated for future agents.
---

# Code Update Handoff

Use this skill at the end of any repository edit batch, before the final reply.

## Required Workflow

1. Review the latest diff, test output, and any unresolved risks.
2. Write or update `.agents/session-log.md` with one new top entry.
3. Reply to the user with a concise handoff that always includes:
   - what changed
   - what was verified
   - `Not done yet`
4. If the requested scope is fully implemented and verified, set `Not done yet` to `None` instead of omitting the section.
5. Do not claim `done`, `complete`, or `ready` if any requested item is still missing or unverified.

## Session Log

Keep `.agents/session-log.md` as a compact, chronological record for other developers and agents.

### Entry Format

```md
### YYYY-MM-DD - Short Title

- Why: the reason for the change or the problem being addressed.
- What: the files, behavior, or docs changed.
- How: the approach, reuse decisions, or key implementation detail.
- Result: the expected effect or value for future work.
- Pitfalls: any issue discovered; use `None` if there were none.
- Verification: the commands actually run; use `None` if nothing was run.
- Next: the next useful step; use `None` if nothing remains.
```

### Rules

- Put the newest entry at the top of `## Entries`.
- Keep each entry factual, short, and repo-relative.
- If a pitfall is unresolved, say so plainly and name the next useful action.
- Do not turn the log into a transcript or changelog.
- Avoid secrets, tokens, and long command output.
