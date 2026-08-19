# Vendored: json5/json5-tests

Source: https://github.com/json5/json5-tests
Commit: `ceb24d4080137d70833f86c25659c1331b80a387` (2026-02-19)
License: MIT (see `LICENSE.md` in this directory)

Every fixture file here (other than this one and `LICENSE.md`/`README.md`) is vendored
unmodified from upstream. Naming convention, per upstream's own `README.md`: the *file
extension* signals expected behavior, not a filename prefix.

- `.json`: valid JSON, must remain valid JSON5 (parse successfully).
- `.json5`: uses JSON5-specific syntax, must parse successfully.
- `.js`: valid JavaScript but explicitly disallowed by JSON5, must fail to parse.
- `.txt`: invalid JavaScript (and so invalid JSON5), must fail to parse.
- `.errorSpec`: companion metadata for a `.txt` case (expected line/column/message) — not itself
  a fixture to parse; the harness only checks accept/reject, not exact error details.

Do not hand-edit these fixtures or add jsonx-specific test files to this directory — it exists
to keep upstream's conformance data isolated and auditable against the real json5-tests suite.

See `kiit.jsonx.conformance.Json5ConformanceTest` for the harness that runs these.
