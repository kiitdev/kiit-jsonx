# Contributing

Thanks for your interest in kiit-jsonx.

## Where things stand right now

This project is early. Phase 1 (the foundation: tree type, lexer state, error model) just
landed — see [`_prd/jsonx`](../_prd/jsonx) in the workspace root for the full PRD and phased
plan. **I'm not accepting general pull requests at this stage.** The priority right now is
working through the phased plan and letting the design settle before opening up broader
contribution.

This isn't a permanent stance, just a "not yet."

## What's welcome right now

- **Bug reports.** If something is broken, incorrect, or the docs don't match the actual
  behavior, please open an [issue](../../issues).
- **Design feedback.** Disagree with a naming choice, a grammar decision, or a default? Open a
  [Discussion](../../discussions). This is genuinely useful, and it's the best way to influence
  where the project goes next.
- **Questions.** Also welcome in Discussions, not Issues.

## Before opening a PR

If you've discussed something in an Issue or Discussion and I've said a PR would be welcome,
please make sure:

- The PR references the Discussion or Issue it came out of.
- Tests are included for any behavioral change.
- The change is scoped narrowly, one concern per PR.

PRs opened without prior discussion may be closed and asked to start with a Discussion instead.
Not out of unfriendliness, just to keep effort aligned before code gets written.

## Build, test, and publish

See [BUILD.md](./BUILD.md) for local build and test instructions. Publishing isn't wired up yet
(planned for Phase 4 of the jsonx plan).

## Code of conduct

Be respectful. Disagreement about design is welcome and expected, personal attacks are not.
