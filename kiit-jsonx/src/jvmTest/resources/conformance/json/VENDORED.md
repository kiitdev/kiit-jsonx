# Vendored: nst/JSONTestSuite

Source: https://github.com/nst/JSONTestSuite
Subdirectory: `test_parsing/`
Commit: `1ef36fa01286573e846ac449e8683f8833c5b26a` (2024-11-22)
License: MIT (see `LICENSE` in this directory)

Every `*.json` file here (other than this one) is vendored unmodified from upstream. Naming
convention, per upstream: `y_*.json` must parse successfully, `n_*.json` must fail to parse,
`i_*.json` is implementation-defined — either outcome is acceptable.

Do not hand-edit these fixtures or add jsonx-specific test files to this directory — it exists
to keep upstream's conformance data isolated and auditable against the real JSONTestSuite. Add
jsonx-specific parser tests elsewhere (e.g. `JsonParserTest`).

See `kiit.jsonx.conformance.JsonConformanceTest` for the harness that runs these.
