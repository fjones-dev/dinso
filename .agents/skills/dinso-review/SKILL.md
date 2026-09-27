---
name: dinso-review
description: Reviews a Dinso diff, PR, or commit range for correctness, security, and consistency issues in this demo pension/insurance codebase. Explicit-invocation only — never run this proactively; use it when the user directly asks for a Dinso code review. Separates real findings from already-documented/accepted trade-offs, calibrates severity to the stated demo scale, and writes findings in plain, jargon-free terms.
disable-model-invocation: true
---

# Dinso code review

## Purpose

Review a set of changes to Dinso — a demo pension/insurance platform with three customer
overlays — for correctness, security, and internal consistency. This is not a style pass;
don't nitpick formatting or naming that isn't actually wrong.

Dinso is a stated demo/take-home project, not a production system. Calibrating findings for
that fact is part of this review (see Step 5), not a license to skip real bugs.

For visual/UX conventions, see the `dinso-design` skill — out of scope here.

## Activation

Use this skill only when explicitly asked to review Dinso code — e.g. "review this diff",
"code review the current PR", "review my changes with dinso-review". Never run this analysis
on your own initiative as part of implementing a feature or fixing a bug.

This rule stands on its own even if a repo-level agent-instructions file already says the
same thing — don't rely on that file being loaded.

## What to review

The default target is the current uncommitted/staged diff. If the user names a PR, branch,
or commit range instead, use that. If neither the target nor a description of what changed
and why is obvious, ask once rather than guessing scope.

Covers both `client/` (Vue) and `server/` (Gradle/Spring Boot) changes. Dinso is one shared
codebase compiled into three customer variants (svenskebanken, pensionsbolaget, finbanken)
via overlays in `client/customers/*` and `server/customers/*`. A change to shared code must
be checked against behavior in **all three** variants, not just the one the author tested —
a feature one customer doesn't offer (e.g. finbanken has no company portal) needs to actually
be inaccessible there, not just untested.

## Step 1 — Gather context before judging

Before reading code line by line:

- Look for a change/PR description and any linked decisions or limitations document. If one
  exists, read it fully and note what it already calls known, accepted, or deliberately
  deferred — this list drives the filter in Step 4.
- If no such document exists, say so explicitly in the output rather than silently treating
  everything as novel.
- Skim `docs/systemdokumentation.md` if the change touches authorization, roles, or
  customer-specific behavior — it documents Dinso's own architecture and invariants, and a
  finding that contradicts it carries more weight than a stylistic guess.
- Identify what the change is trying to accomplish before judging whether it does.

## Step 2 — Read the diff

Read the actual diff, not just filenames. For each changed file, note what changed, why
(from Step 1), and what it touches (endpoint, service method, entity, Vue component/store/
view). Trace one level beyond the diff when correctness depends on a caller or callee not
shown in it — e.g. a new check needs to be read against the controller that's supposed to
call it.

## Step 3 — What to check, in priority order

1. **Server-side authorization is real.** `docs/systemdokumentation.md` states the
   invariant directly: hidden client buttons are not a security boundary, all authorization
   must be enforced server-side. Check every new or changed endpoint and service method
   actually enforces what it implies, independent of what the client renders. Authorization
   here can live at **two layers** — inline service checks (e.g. a
   `requireCompanyPermission`-style method) and URL-pattern rules in a Spring Security
   config's `authorizeHttpRequests` (e.g. `/api/admin/** → hasRole("SYSTEM_ADMIN")`). Check
   both before reporting a controller has "no auth check" — one with no inline check can
   still be fully protected by a security-config matcher, and concluding otherwise from the
   controller body alone is a false positive.

2. **Cross-customer consistency.** A shared-code change must behave correctly for all three
   customer overlays, including ones where a feature is disabled. Check that routes or
   endpoints that shouldn't exist for a given customer are actually absent (missing route or
   bean), not just hidden in the client.

3. **Data model durability.** The database is in-memory, `create-drop`, and re-seeded on
   every start — there are no migrations to worry about — but check for anything that would
   misbehave or corrupt data even within a single running session (e.g. a stored enum name
   that a rename or removal would silently break on next load).

4. **Stated-scale correctness.** If the change names an explicit scale target, check whether
   the design would visibly fail at *that* target — not whether it's hardened beyond it. See
   Step 5 for how to draw that line.

5. **Business-rule correctness.** Compare new logic against what the change description says
   the rules should be; flag anywhere code and stated intent diverge.

6. **Test coverage matches the risk.** New authorization or business-rule logic should have
   a test exercising both the allowed and the denied path. Flag gaps; don't demand exhaustive
   coverage of everything touched.

## Step 4 — Filter out already-known trade-offs

For every candidate finding, before reporting it as new: check it against the known/accepted
list gathered in Step 1.

- A finding is "already known" if the document states the same underlying issue as a
  deliberate, accepted choice — even if worded differently or scoped slightly differently.
- Only re-raise an already-known item if something **material** changed since it was
  accepted: the described mitigation no longer matches the code, the blast radius grew (a
  trade-off scoped to one internal endpoint now reaches a public one), or a stated
  precondition for accepting the risk (e.g. "fine because the DB is re-seeded every start")
  no longer holds.
- Never invent or assume a specific known trade-off that isn't actually stated in a document
  you read. If you found no such document, there is nothing to filter against — everything
  you find is a new finding.
- Report filtered-out items separately (see Output format) instead of dropping them silently
  — this is the reviewer's own audit trail, so a human can see what it chose not to repeat
  and why.

## Step 5 — Calibrate to demo scope

Dinso is explicitly a demo/take-home project: no real authentication, in-memory database,
re-seeded on every start, no external integrations. De-prioritize suggestions that only
matter for production infrastructure the project never claims to have: rate limiting,
distributed locks or optimistic locking beyond what correctness requires, migration tooling,
caching layers, full security-audit-grade hardening.

This is **not** a blanket excuse. Where a task or change explicitly states a scale or
robustness target, a design that would visibly break, degrade badly, or need re-architecture
*at that stated target* is a real finding, not a demo-scope exemption — say explicitly which
case applies for anything borderline ("this would already misbehave at the stated target,
not just at production scale").

## Step 6 — Write findings in plain, concrete terms

No unexplained framework jargon. Name the exact file, function, and line a finding is about
rather than describing it abstractly. A finding should be understandable on its own, without
needing outside context.

This is a project-scoped skill for reviewing Dinso changes, not a personalized tutorial for
any one reader — keep explanations general-purpose rather than tailored to one developer's
background.

## Output format

Output is plain markdown, not tied to any specific tool's report schema — this skill should
work for any AI agent reading it, not just one with a particular findings tool.

Structure, in this order:

1. `## Findings` — the real, new issues. For each: a severity/confidence marker, a one-line
   summary, why it matters, and a suggested next step.
2. `## Already known — not re-raised` — items that matched something in the decisions/
   limitations document, each with a one-line pointer to what already covers it.
3. `## Out of scope for this demo` — anything explicitly de-prioritized per Step 5, one line
   each, so it's visible the reviewer considered and consciously skipped it rather than
   missed it.

Per-finding template:

```
- **[Severity/Confidence] One-line summary**
  Why it matters: ...
  Suggested next step: ...
```

## Do not

- Do not run this review on your own initiative as part of unrelated work — only when
  explicitly asked. Do not use browser-based verification tools as part of a review unless
  explicitly asked.
- Do not start the application (backend Spring Boot process or frontend dev server) to
  empirically test a finding — review the diff statically. Running the existing test suite or
  a typecheck (`./gradlew test`, `npm run typecheck`) is fine — a safe, self-contained signal,
  not a live process.
- Do not modify git state. Reading via `git diff`/`git log`/`git show`/`git status` is
  expected; never commit, push, checkout, reset, stash, or otherwise mutate the repository or
  its history. Do not edit the reviewed code either — report findings, don't fix them, unless
  the user separately and explicitly asks for fixes.
- Do not create or update documentation, decision logs, or summary files as a side effect of
  reviewing — findings go in your response, not new files.
- Do not hardcode specific past findings from any one PR as permanent facts about Dinso's
  codebase — always re-derive findings from the current diff and whatever known-trade-offs
  document it actually points to.
- Do not demand production-grade infrastructure for a stated demo unless the change itself
  claims to need it or the stated scale target would actually fail.
- Do not silently drop a candidate finding because it resembles a known trade-off — always
  show it under "Already known," never just omit it.
