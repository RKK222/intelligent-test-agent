---
name: code-update-handoff
description: Use when finishing or pausing any code-change task in this repo so the handoff stays candid, a `Not done yet` section is always included, and the per-author `.agents/session-log.{id}.md` is updated once per meaningful session boundary for future agents.
---

# Code Update Handoff

Use this skill at the end of any repository edit batch, before the final reply.

## Required Workflow

1. Review the latest diff, test output, and any unresolved risks.
2. If the session produced new durable information worth preserving, write or update your per-author file `.agents/session-log.{id}.md` once for the session and merge related edits into a single top entry. `{id}` is your sanitized `git config user.name` (lowercase, runs of non-`[a-z0-9]` collapsed to a single `-`, trimmed; fall back to `hostname -s` if empty). Do not append to the frozen shared `.agents/session-log.md`.
3. Reply to the user with a concise handoff that always includes:
   - what changed
   - what was verified
   - `Not done yet`
4. If the requested scope is fully implemented and verified, set `Not done yet` to `None` instead of omitting the section.
5. Do not claim `done`, `complete`, or `ready` if any requested item is still missing or unverified.

## Session Log

Each committer keeps their own compact, chronological record in `.agents/session-log.{id}.md` (`{id}` = sanitized `git config user.name`). The shared `.agents/session-log.md` is a frozen legacy archive-never append to it. Before committing, review the recent entries of every `.agents/session-log*.md` file so you don't clobber or re-derive others' work.

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
- Do not add a new entry for every small edit when the same session can be summarized once.
- If a pitfall is unresolved, say so plainly and name the next useful action.
- Do not turn the log into a transcript or changelog.
- Avoid secrets, tokens, and long command output.
