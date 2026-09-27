# Design decisions regarding SKILL.md

**Follows convention from other agent skills in the repo.**
Frontmatter, numbered steps, a closing "Do not" list, strict `disable-model-invocation: true` —
this helps readability and continuity within the repo. The disable flag specifically mirrors
`AGENTS.md`'s existing rule against running review or analysis on your own initiative, and the
same setting the `system-documentation` skill already uses for "only when asked" behaviour — a
review skill that could trigger itself would add uninvited critique to unrelated work.

**Checks the PR text before flagging something, to avoid re-flagging known issues.**
Relevant specifically to this repo since it's a demo built under time constraints, where
deferrals and known weaknesses are an accepted part of the workflow. If an issue has already
been noted, don't bring it up again in the review.

**Scoped to what a take-home needs, not production.**
Follows the task's stated scale (~100 actions, 100 users) rather than production-grade
requirements it doesn't need.

**What I considered, but later dropped.**
Throughout this project I've mentioned in most of my prompts that I'm new to Vue but have
React experience, and asked agents to explain Vue concepts in React terms, e.g. "this
`computed` is like `useMemo`". I decided not to include this in the skill, since it's too
developer-specific and not general enough for the repo.

**No git, doesn't touch running state, but can run tests.** It never commits, pushes, or
checks out anything, and never starts the app. A review should come from reading the diff,
not from running it live. Tests and typecheck are the one exception, since they're a safe,
self-contained signal rather than a live process.

**Co-written with Claude, referencing other skills, and best practices.**
