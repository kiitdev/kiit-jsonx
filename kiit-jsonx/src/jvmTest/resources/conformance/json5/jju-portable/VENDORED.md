# Vendored: rlidwka/jju (portable JSON5 test suite)

Source: https://github.com/rlidwka/jju, `test/portable-json5-tests.yaml`
Commit: `b8c49213c46941598422cad426d32dd3b530dbc5` (2018-07-30)
License: MIT (see `LICENSE` in this directory)

`portable-json5-tests.yaml` is vendored unmodified from upstream. It's a YAML document of named
test cases, each with an `input` (JSON5 text) and either an `output` (the expected decoded
value) or the literal `!error` tag if parsing should fail. Each case also has a `type`: `basic`,
`advanced`, or `extra` — per upstream's own comment header, `basic` is "tests that every JSON5
parser should pass," which is the tier this milestone vendors and runs. `advanced`/`extra` cases
exist in the file but are intentionally skipped by the harness.

Do not hand-edit this file or add jsonx-specific test files to this directory — it exists to
keep upstream's conformance data isolated and auditable against the real jju suite.

See `kiit.jsonx.conformance.Json5ConformanceTest` for the harness that runs these.
