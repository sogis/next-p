# AGENTS.md

This is a SQLMesh folder.

When modifying a model, always do the following:

1. Understand the model grain and business logic.
2. Add or update SQLMesh tests for every non-trivial logic change.
3. Add or update audits for important invariants.
4. Run validation commands and fix issues before finishing.
5. Summarize what changed, what was tested, and any assumptions.

## Testing rules

Add unit tests for:
- joins
- filters
- mappings
- aggregations
- deduplication
- null handling
- date logic
- incremental behavior
- bug fixes

Use minimal fixtures and explicit expected outputs.
Prefer a few strong edge-case tests over many weak ones.

## Audit rules

Add audits for real model contracts such as:
- not null
- uniqueness at model grain
- accepted values
- valid numeric ranges
- referential integrity
- conditional business rules

Do not add generic low-value audits.

## Required behavior

If you change logic, do not finish without adding validation.
If you fix a bug, add a regression test.
If similar models already have a pattern, follow it.
If unsure, prefer adding a focused test.

## Branching Strategy for Tasks

When working on any task, you must **always create a dedicated branch** before making changes.

### Requirements

* **Never work directly on the main or default branch.**
* Create a **new branch for every task**, change request, bugfix, or improvement.
* Branch names should be **short, descriptive, and task-focused**.
* Do **not** rely on fixed naming prefixes. Use natural names that clearly describe the work.

**Examples:**

* `user-authentication`
* `login-validation-error`
* `update-dependencies`
* `improve-search-performance`

### Workflow

1. Ensure your current work is committed or otherwise safely stored.
2. Check whether the current task branch has already been merged or is ready to be merged.
3. If the previous task branch is complete, **squash-merge it before starting a new task**.
4. Create a fresh branch for the new task.
5. Make all changes for that task only within that branch.
6. Once the task is finished, merge it cleanly, preferably as a squash merge if that matches the repository workflow.

### Task Isolation Rule

A branch should correspond to exactly one task.
Do not continue unrelated work on an old branch.
Before starting a new task, close out the previous branch by merging, squashing, or explicitly abandoning it.

### Intelligent Branch Cleanup

Agents should apply the following logic before starting a new task:

* If the current branch contains completed work for a single task and is ready to integrate, **squash-merge it first**.
* If the current branch contains unfinished or unrelated work, **do not reuse it**.
* If there is any doubt about whether the current branch is safe to merge, **ask the user** whether to squash-merge it before proceeding.
* Never leave a previous branch unaddressed without asking the user what should happen to it.
* After a squash merge, return to the main branch, update it, and create a new task branch from `main`.

### Recommended Principle

**Always begin a new task from a clean state.**
That usually means the previous task branch has been squash-merged and a fresh branch has been created for the next piece of work.

## New Task Signal

The user will prompt `nt: ...` whenever starting a new task.
Treat that as the explicit signal to close out the previous task context and create a dedicated new branch from `main` before making changes.

For your question: yes, that “intelligent logic” makes sense, but it should be framed as a **rule-based decision process**, not as fully automatic behavior unless your tooling explicitly supports safe auto-merge checks. The safe version is: *before starting a new task, check whether the current branch represents a completed single task; if yes, squash-merge it, then create a new branch.*

I can also turn this into a stricter policy version with RFC-style wording such as **MUST / MUST NOT / SHOULD**.
