# jsonx — Technical Specification

jsonx is a text-based configuration format. It is a strict syntactic superset of JSON5 — anything valid in JSON5 is valid jsonx — extended with a bounded, deterministic tag mechanism (`@name(args)`) for typed literals, environment-variable resolution, and config-specific safety guarantees such as preventing secrets from being committed as literals. It is designed for application and service configuration, not for general-purpose data interchange or wire-format payloads.

```jsonx
// A representative jsonx document
{
  service: {
    name: "orderService",
    environment: @env('APP_ENV', 'development'),
  },

  database: {
    // required — errors if unset
    host: @env('DB_HOST'),
    port: @env.number('DB_PORT', 5432),
    // must be sourced from @env, never a literal
    password: @secret(@env('DB_PASSWORD')),
  },

  timeout: @env.number('TIMEOUT_MS', 3000),
  retryTimeout: @ref('timeout'),

  roles: @table({
    names: [ "role", "canDeploy" ],
    rows: [
      [ "admin", true ],
      [ "viewer", false ],
    ]
  }),

  notes: """
    Multiline, TOML-aligned string.
    Comments, trailing commas, and unquoted keys are all valid jsonx.
    """,
}
```

| Field | Value |
|---|---|
| **Version** | v0.1 |
| **Status** | Complete draft — ready for review |
| **Versioning** | Any decision changing the format itself (grammar, tags, resolution, errors, security) bumps this version and is logged in Appendix F. Editorial-only changes don't. |
| **Companion documents** | `jsonx-prd.md`, `jsonx-plan.md`, `declarative-tags.md`, `updates-1.md` |

| Scope | Statement |
|---|---|
| **Is** | A precise, implementation-agnostic spec of jsonx as a format — buildable from this document alone, in any language. |
| **Is not** | A Kotlin API reference or build guide (see the PRD/plan). No Kotlin syntax appears here — concepts are illustrated with `.jsonx` examples instead of code. |

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Design Principles](#2-design-principles)
3. [Format Layers](#3-format-layers)
4. [Lexical Structure — Dialect Additions](#4-lexical-structure--dialect-additions)
5. [Data Model](#5-data-model)
6. [Tags](#6-tags)
7. [Resolution Pipeline](#7-resolution-pipeline)
8. [Error Model](#8-error-model)
9. [Configuration Surface](#9-configuration-surface)
10. [Extraction Semantics](#10-extraction-semantics)
11. [Serialization](#11-serialization)
12. [Security Model](#12-security-model)
13. [Compatibility](#13-compatibility)
14. [Conformance](#14-conformance)
15. [Appendices](#15-appendices)

---

## 1. Introduction

### 1.1 Purpose & Scope
This document specifies jsonx: a text-based configuration format that is a strict syntactic superset of JSON5, extended with a bounded, deterministic tag mechanism for typed literals, environment resolution, and config-specific safety guarantees. It covers grammar, data model, tag semantics, resolution ordering, error taxonomy, and security posture. It does not cover any single implementation's API.

### 1.2 Conformance Levels
| Format | Conforms To | Tag-Capable |
|---|---|---|
| `Format.Json` | RFC 8259, unmodified | No |
| `Format.Json5` | Official JSON5 spec, unmodified | No |
| `Format.Jsonx` | §3.3 of this document (JSON5 + dialect additions) | Yes |
| `Format.JsonL` | One `Format.Json` value per line | No |

### 1.3 Terminology
| Term | Meaning |
|---|---|
| **Node** | One value in a parsed tree (object, array, string, number, boolean, null, or an unresolved tag reference). |
| **Tree** | The complete parsed structure rooted at one document. |
| **Tag** | An `@name(args)` construct appearing at a value position. |
| **Tagged reference** | A node representing a tag that has not yet been resolved to a final value. |
| **Resolution** | The act of converting a tagged reference into a final node. |
| **Phase** | A named stage in the resolution pipeline (§7.1). |
| **Eager resolution** | Resolution occurring during parsing, before the tree is returned. |
| **Deferred resolution** | Resolution occurring after parsing, via an explicit pipeline phase. |
| **Namespace** | The prefix in a dotted external tag name (`prefix.tagName`), identifying who registered it. |
| **Dialect** | `Format.Jsonx` specifically — JSON5 plus jsonx's own syntax additions. |
| **Provenance** | The origin of a value — e.g. whether it was sourced from an environment variable versus written as a literal. |

### 1.4 Notation Conventions
1. **Numbered, not dashed**: enumerations use numbered lists, not dashes.
2. **Tables over prose**: anything with more than one dimension (name + behavior + default, etc.) is a table.
3. **Bold-prefixed bullets**: every numbered point leads with a short bold term, then a colon, then the detail.
4. **Grammar notation**: `::=` defines a production; `|` separates alternatives; `[x]` marks `x` optional; `{x}` marks zero-or-more repetitions of `x`; literal text is quoted.
5. **One example per concept**: illustrated in `.jsonx`, never host-language code.
6. **Minimum viable prose**: short sentences, no restatement, no filler.

### 1.5 Relationship to Other Documents
This document defines what jsonx is. `jsonx-prd.md` explains why it exists and for whom. `jsonx-plan.md` sequences how it gets built. `declarative-tags.md` holds unscoped brainstorm material — nothing there is normative until it appears in this document. `updates-1.md` is a session changelog, superseded by Appendix F going forward.

---

## 2. Design Principles

*Consolidates the PRD's principles, tag admission rules, and value tiers into one "why" layer, referenced elsewhere rather than repeated. Each principle below states the rule; Appendix D holds the alternatives weighed and rejected in reaching it.*

### 2.1 Core Principles
| # | Principle | Statement | Consequence |
|---|---|---|---|
| 1 | **Brace-based** | Grammar is brace-delimited, never whitespace-significant. | Avoids YAML-class indentation ambiguity. |
| 2 | **Bounded extensibility** | New capability only through the tag mechanism (§6), never new core grammar. | The parser's grammar is closed and fully enumerable. |
| 3 | **Syntactically familiar** | JSON/JSON5-compatible grammar. | Familiarity is about syntax; tags, merge, and resolution semantics are jsonx-specific and not implied by JSON5 familiarity. |
| 4 | **Low hand-editing friction** | Comments, trailing commas, unquoted keys, multiline strings. | Goes further than JSON5 (triple-quoted strings) where it improves authoring — see Appendix D for what was deliberately not added (optional commas, quoteless strings). |
| 5 | **Structured, diagnosable errors** | Every failure is categorized, carries a path, and carries position where available (§8). | Serves both human and automated (AI-assisted) consumers; taxonomy is host-language-agnostic. |
| 6 | **Format, not language** | No variables, expressions, conditionals, loops, or imports. | Reading a jsonx document is sufficient to know what it produces — no execution model exists. |

### 2.2 Tag Admission Rules
Every tag — built-in or external — must satisfy all four.

| # | Rule | Statement | Rationale |
|---|---|---|---|
| 1 | **Declarative** | A tag converts arguments to a value. It never remembers, branches on a runtime value, or repeats. | Distinguishes a typed literal from a program. Directly produced by the rejected scripting alternative (`@var`/`@if`/`@loop`) — see Appendix D. |
| 2 | **No I/O** | A tag never reads a file or makes a network call. | jsonx's core input contract is text-in, text-derived-tree-out — nothing external. `@env`'s environment-variable access is the sole, explicit, policy-gated exception (§9.3). |
| 3 | **Static-shape checks only** | A tag may inspect the shape of its own arguments (is this still an unresolved reference? is this an object with the right fields?) but may never branch on a computed runtime value. | Shape checks are resolved once from what's on the page; value-based branching is program logic. |
| 4 | **No ambient state** | Nothing a tag does may depend on or modify state persisting across separate resolutions, beyond what the tree itself expresses. | Named reuse (a value bound to a name once, referenced elsewhere) is fine; mutable variables are not. |

### 2.3 Extensibility Model
Tags are the sole extension point. The core grammar (§3–4) is closed and will not grow to accommodate any single tag's needs. Related rejected alternatives: a shared `@enum.{type}` namespace, a global mutable tag registry — see Appendix D.

```jsonx
// Bounded: a tag call, ordinary grammar surrounding it
timeout: @env.number('TIMEOUT_MS', 3000),

// Out of bounds (hypothetical, not valid jsonx): new grammar, not a tag
// timeout: for env in ['A','B'] { ... }
```

### 2.4 Value Tiers *(informative, non-normative)*
Guidance for which tags are worth specifying as built-ins — not a conformance requirement.

| Tier | Criterion | Example Tags |
|---|---|---|
| 1 | Prevents a real, costly mistake. Without it, the fallback is worse (hardcoded secrets, copy-paste drift, silent misconfiguration). | `@env`, `@ref`, `@secret` |
| 2 | Validates or normalizes a value's shape at authoring time. Catches a bad value early, doesn't prevent an incident. | (reserved for future tags — none committed in v0.1) |
| 3 | Pure convenience. Nothing breaks without it. | `@table` |

---

## 3. Format Layers

### 3.1 `Format.Json`
Conforms to RFC 8259 without modification. Not restated here.

### 3.2 `Format.Json5`
Conforms to the official JSON5 specification without modification. Not restated here. `Format.Json5` never produces tag syntax — `@` has no special meaning under this format.

### 3.3 `Format.Jsonx` (the dialect)
A strict superset of §3.2. Specified below as a diff — the only grammar this document defines.

#### 3.3.1 Triple-Quoted Strings
| # | Rule | Detail |
|---|---|---|
| 1 | **Delimiter** | `"""` opens and closes a multiline string. |
| 2 | **Leading newline** | A newline immediately following the opening `"""` is trimmed; otherwise content starts as-is. |
| 3 | **No auto-dedent** | Indentation is preserved literally as written. |
| 4 | **Escapes** | Standard escapes (`\n`, `\t`, `\uXXXX`, `\"`, etc.) are processed. |
| 5 | **Line continuation** | `\` at end of line consumes the newline and following leading whitespace on the next line. |
| 6 | **Embedded quotes** | Unescaped `"` and `""` are permitted inside; only `"""` closes the string. |

```jsonx
message: """
  line one \
  continues here
  line two, "quoted" inline, fine
  """,
```

#### 3.3.2 Tag Syntax
```
tag        ::= "@" [namespace "."] identifier "(" [argList] ")"
namespace  ::= identifier
argList    ::= value {"," value}
```

```jsonx
host: @env('DB_HOST'),
role: @acmecorp.enum.userType('ADMIN'),
```

#### 3.3.3 Grammar Diff (vs. §3.2)
| Production | Change |
|---|---|
| `value` | Add alternative: `tag` |
| `string` | Add alternative: `tripleQuotedString` |

### 3.4 `Format.JsonL`
One `Format.Json` value per line — strict JSON, not JSON5 or the dialect, per line. Each line parses and fails independently.

```jsonl
{"id": 1, "name": "Superman"}
{"id": 2, "name": "Batman"}
```

### 3.5 Format Comparison
| Format | Comments | Trailing Commas | Tags | Multiline Strings | Reference |
|---|---|---|---|---|---|
| `Json` | No | No | No | No | RFC 8259 |
| `Json5` | Yes | Yes | No | No (line continuation only) | spec.json5.org |
| `Jsonx` | Yes | Yes | Yes | Yes (`"""`) | §3.3 |
| `JsonL` | No | No | No | No | §3.4 (delegates to `Json`) |

---

## 4. Lexical Structure — Dialect Additions

Only what's new beyond JSON5 tokenization.

### 4.1 Additional Token Types
| Token | Trigger | Appears In |
|---|---|---|
| At | `@` | `Jsonx` only |
| TripleQuoteString | `"""` | `Jsonx` only |
| LParen / RParen | `(` / `)` | `Jsonx` only (tag argument list) |

### 4.2 Comment Styles
| Style | Syntax | Permitted In |
|---|---|---|
| Line | `//` | `Json5`, `Jsonx` |
| Block | `/* */` | `Json5`, `Jsonx` |
| Hash | `#` | Not committed in v0.1 — deferred (see `declarative-tags.md`) |

`Format.Json5` remains spec-pure: no dialect-only token or comment style is ever produced under it.

### 4.3 Line Terminator Handling
`\n`, `\r`, `\r\n`, U+2028 (Line Separator), and U+2029 (Paragraph Separator) are all recognized as line terminators, including as the target of a line-continuation backslash inside strings.

---

## 5. Data Model

The tree every format layer parses into — a closed set of node types.

### 5.1 Node Types
| Node Type | Description | Example Literal |
|---|---|---|
| Object | Ordered key → node map | `{ a: 1 }` |
| Array | Ordered list of nodes | `[1, 2, 3]` |
| String | Text value | `"abc"` |
| Number | Numeric value (§5.3) | `42`, `3.14` |
| Boolean | `true` / `false` | `true` |
| Null | Explicit absence of value | `null` |
| Tagged reference | An unresolved `@name(args)` — see §6 | `@env('X')` |

### 5.2 Object Key Ordering
Normative: object keys preserve insertion (source) order. No implementation may reorder keys during parsing, merging, or serialization.

```jsonx
{ c: 1, a: 2, b: 3 }
// iteration order is c, a, b — never re-sorted
```

### 5.3 Number Representation
| Form | Representation | Precision Boundary |
|---|---|---|
| Integer literal | Exact integer | Up to 64-bit signed range |
| Decimal/exponent literal | Floating-point | IEEE 754 double precision |

A number outside the 64-bit integer range that lacks a decimal point or exponent is a conformance edge case implementations must document explicitly.

### 5.4 Equality Rules
| Node Type | Equality |
|---|---|
| Object | Same keys, same values, order-independent for equality purposes (order affects iteration, not equality) |
| Array | Same length, same values, in order |
| String / Boolean / Null | Value equality |
| Number | Numeric equality across integer/decimal representations of the same value |
| Tagged reference | Same tag name and same argument equality — two unresolved references are equal only if neither has been resolved and both match structurally |

---

## 6. Tags

### 6.1 Argument Convention
No dedicated named-argument grammar exists. Arguments are positional by default; an object argument carries named fields via ordinary object syntax.

```jsonx
// pure positional
@env('DB_HOST'),
// pure named (single object argument)
@table({ names: [...], rows: [...] }),
// mixed — positional, then trailing named options
@acmecorp.openUrl('https://...', { newTab: true }),
```

### 6.2 Tag Taxonomy
| Tier | Namespace Requirement | Resolution Timing | Examples |
|---|---|---|---|
| Always-on built-in | None (unprefixed) | Eager or deferred, per tag (§6.4) | `@env`, `@ref`, `@secret`, `@table` |
| Optional built-in (stdlib) | None (unprefixed) | Eager | (none committed in v0.1) |
| External | Required (`namespace.tag`) | Eager | Consumer-defined |

### 6.3 Namespace Rules
| Rule | Detail |
|---|---|
| 1. Reserved namespaces | `kiit` and `jsonx` may not be used as an external tag's namespace. |
| 2. Required prefix | Every external tag name must be `namespace.identifier`; a bare, unprefixed external registration is invalid. |
| 3. Built-ins are unprefixed | Always-on and optional-built-in tags never carry a namespace. |

```jsonx
// valid — external, properly namespaced
role: @acmecorp.enum.userType('ADMIN'),
// invalid — external tag, missing namespace prefix
role: @userType('ADMIN'),
// invalid — 'kiit' is reserved
role: @kiit.userType('ADMIN'),
// valid — built-in, correctly unprefixed
host: @env('DB_HOST'),
```

### 6.4 Resolution Timing
| Tag | Timing | Reason |
|---|---|---|
| `@table` | Eager (parse-time) | Self-contained — its argument is fully resolvable from its own content. |
| External tags | Eager (parse-time) | Same as above, by construction of the tag contract (§6.5). |
| `@env` (and typed/fallback variants) | Deferred (Env phase, §7.3) | Parsing alone must never read the environment (§12.1). Resolution is gated by an explicit, policy-controlled phase. |
| `@ref` | Deferred (Reference phase, §7.4) | Requires whole-tree context unavailable during single-pass parsing. |

General rule: tag syntax recognition (producing a tagged reference) is always eager, for every tag, without exception. Resolution is eager only when a tag is self-contained; `@env` and `@ref` are the deliberate, sole exceptions.

### 6.5 Tag Contract
A tag is defined by:
1. **Name**: its identifier, optionally namespaced.
2. **Kind**: `Simple` (ordinary argument list, per §6.1) or `Structural` (custom argument grammar — reserved, unspecified in v0.1; see Appendix D, item 2).
3. **Conversion**: a function from arguments to either a resolved node or a structured error (§8).

No host-language interface is specified here; each implementation exposes this contract in whatever form is idiomatic to it.

### 6.6 Built-in Tag Reference
| Tag | Phase | Arguments | Output | Error Conditions |
|---|---|---|---|---|
| `@env(name)` | Env | 1 positional: name (string) | String | Missing (unset, no default) |
| `@env(name, default)` | Env | 2 positional: name, default (string) | String | — (falls back to default) |
| `@env.number(name, default?)` | Env | name (string), default (number, optional) | Number | Missing; coercion failure |
| `@env.bool(name, default?)` | Env | name (string), default (boolean, optional) | Boolean | Missing; coercion failure |
| `@env.any(names, default?)` | Env | names (array of strings), default (optional) | String | Missing (none of `names` set, no default) |
| `@ref(path)` | Reference | 1 positional: path (string) | Node at `path` | Path not found; cycle detected |
| `@secret(arg)` | Eager | 1 positional: must be a still-unresolved reference to an approved source (`@env`, in v0.1) | The argument, unchanged (still deferred) | Argument is an already-resolved literal |
| `@table(obj)` | Eager | 1 positional: `{ names?: string[], rows: array[] }` | Array of objects | Missing `rows`; row width mismatch |

```jsonx
users: @table({
  names: [ "role", "canDeploy" ],
  rows: [
    [ "admin", true ],
    [ "viewer", false ],
  ]
})
// -> [ { role: "admin", canDeploy: true }, { role: "viewer", canDeploy: false } ]
```

Every environment access above is additionally gated by the policy in §9.3.

### 6.7 Cross-References
See §2.2 for the admission rules every tag (including future ones) must satisfy, and §2.4 for the non-normative value-tier guidance behind which tags are specified as built-ins.

---

## 7. Resolution Pipeline

### 7.1 Phases & Ordering
| Phase | Resolves | Depends On |
|---|---|---|
| Parse (Eager) | `@table`, external tags | — |
| Env | `@env` and all typed/fallback variants | Parse complete |
| Reference | `@ref` | Env phase complete (so references see resolved values, not pending env tags) |
| Merge | (not a resolver — combines two trees) | Reference phase complete on both input trees |

Each phase runs to completion, on the whole tree, before the next begins. Merge is not itself a resolution phase — it takes two already-resolved trees as input and produces one; every stage before it operates on one tree.

### 7.2 Parse-Time (Eager) Resolution
```jsonx
role: @table({ names: ["r"], rows: [["admin"]] })
// resolved immediately during parsing — no @table tagged reference
// ever exists in the tree returned by the parser
```

### 7.3 Env Phase
```jsonx
{
  // string, falls back
  host: @env('DB_HOST', 'localhost'),
  // coerced to number
  port: @env.number('DB_PORT', 5432),
  // coerced to boolean
  debug: @env.bool('DEBUG', false),
  // fallback chain
  region: @env.any(['REGION_V2', 'REGION'], 'us-east'),
}
```
A missing, required (no-default) environment variable is a resolution-time error in this phase. A value that fails type coercion (e.g. `DEBUG=maybe`) is also a resolution-time error — never a silent fallback to the default.

### 7.4 Reference Phase
```jsonx
{
  timeout: @env.number('TIMEOUT_MS', 3000),
  // resolves to 3000 (or the env-sourced value)
  retryTimeout: @ref('timeout'),
}
```
Cyclic references (`a` refers to `@ref('b')`, `b` refers to `@ref('a')`) are detected and rejected as an error, not resolved by any fallback.

### 7.5 Merge Phase
```jsonx
// base.jsonx: { port: 8080, ssl: false }
// prod.jsonx (override): { ssl: true }
// merge(base, override) -> { port: 8080, ssl: true }
```
Merge combines two already-fully-resolved trees (each individually run through Env and Reference phases first). It never operates on a tree still containing unresolved tagged references.

### 7.6 Reserved: Post-Merge Phase
A phase running after Merge, for resolving tags whose purpose is to interact with merge itself (e.g. protecting a value from being overridden), is reserved for future specification. No such tag is committed in v0.1 — see `declarative-tags.md` for the exploratory design.

### 7.7 Full Pipeline Example
```jsonx
// base.jsonx
{
  db: {
    host: @env('DB_HOST', 'localhost'),
    port: @env.number('DB_PORT', 5432),
  },
  displayHost: @ref('db.host'),
}
```
With `DB_HOST` unset in the environment: Env phase resolves `host` to `"localhost"` and `port` to `5432`; Reference phase resolves `displayHost` to `"localhost"`. Final tree:
```jsonx
{ db: { host: "localhost", port: 5432 }, displayHost: "localhost" }
```

---

## 8. Error Model

Host-language-agnostic — jsonx's own closed error taxonomy, not any library's error system.

### 8.1 Design Goals
1. **Structured**: every error is categorized, not a free-text message alone.
2. **Diagnosable**: sufficient detail (category, path, position where available) for both a human and an AI tool to act on without guessing.
3. **Portable**: defined independent of how any implementation surfaces errors (exceptions, result types, error codes).

### 8.2 Error Categories
| Category | Description | Example Trigger |
|---|---|---|
| SyntaxError | Malformed input at the lexical/grammar level | Unterminated string |
| TypeMismatch | Value present but wrong type for the operation | Extracting a string from a number node |
| MissingValue | Required value absent | `@env('X')` with `X` unset, no default |
| UnresolvedTag | A tagged reference reached extraction without being resolved by any phase | No handler registered for an external tag |
| DuplicateKey | Same key appears twice in one object, under Error policy (§9.1) | `{ a: 1, a: 2 }` |
| CoercionFailure | A value could not be converted to the requested type | `@env.number('X')` where `X="abc"` |
| CycleDetected | A reference chain refers back to itself | `@ref('a')` inside a value at path `a` |
| EnvironmentAccessDenied | `@env` resolution blocked by policy (§9.3) | `EnvAccessPolicy.Deny` in effect |
| MergeConflict | An override attempted where merge rules forbid it | (reserved — see §7.6) |

### 8.3 Required Error Fields
| Field | Description | Always Present |
|---|---|---|
| Category | One value from §8.2 | Yes |
| Message | Human-readable detail, specific to the instance | Yes |
| Path | Dotted/indexed location in the tree | Yes |
| Position | Line/column/offset in source | Only for Parse and Eager-resolution-stage errors (§8.4) |
| Cause | Wrapped underlying failure, if any | Optional |

Security constraint: if a value's provenance traces to `@env`, Message must never include the resolved value — only the variable name (§12.3).

### 8.4 Position & Path Availability
| Pipeline Stage | Position Available | Path Available |
|---|---|---|
| Parse | Yes | Yes |
| Eager tag resolution | Yes | Yes |
| Env phase | No | Yes |
| Reference phase | No | Yes |
| Merge | No | Yes |
| Extraction | No | Yes |

### 8.5 Example Errors
Concrete instances of §8.2–8.3 applied to real inputs.

| Scenario | Category | Path | Position | Message |
|---|---|---|---|---|
| `host: @env('DB_HOST')`, `DB_HOST` unset, no default | MissingValue | `host` | — (Env phase, no position) | Environment variable `DB_HOST` is not set and no default was provided. |
| `{ "a": 1, "b": "x" }`, extracting `a` as a string | TypeMismatch | `a` | line 1, col 8 | Expected string, found number. |
| `{ a: 1, a: 2 }` under `DuplicateKeyPolicy.Error` | DuplicateKey | `a` | line 1, col 9 | Key `a` is duplicated; first defined at line 1, col 3. |
| `port: @env.number('PORT')`, `PORT=abc` | CoercionFailure | `port` | — (Env phase, no position) | Value `abc` from `PORT` could not be coerced to a number. |
| `a: @ref('b')`, `b: @ref('a')` | CycleDetected | `a` | — (Reference phase, no position) | Reference cycle detected: `a` → `b` → `a`. |
| `password: @secret('hunter2')` | SyntaxError | `password` | line 3, col 15 | `@secret` requires an argument sourced from an approved tag (e.g. `@env`); a literal value was given. |

Note the `DB_HOST`/`DB_PASSWORD` rows above never surface a resolved value in `Message` — only the variable name — per the security constraint in §8.3.

### 8.6 Implementation Note *(non-normative)*
An implementation may surface this taxonomy through whatever convention is idiomatic to its host language — result types, exceptions, error codes. This document defines the taxonomy and required fields only, not any representation of them.

---

## 9. Configuration Surface

Behavioral options, described as a contract.

### 9.1 Duplicate Key Policy
| Policy | Behavior | Default |
|---|---|---|
| Error | Duplicate key is a parse-time error | Yes |
| LastWins | Later occurrence overwrites earlier | No |
| FirstWins | First occurrence is kept, later ignored | No |
| CollectIntoArray | All values collected into an array under one key | No |

### 9.2 Comment Retention
When enabled, comments are attached to the tree for round-trip serialization (§11.2). When disabled (default), comments are discarded during parsing.

### 9.3 Environment Access Policy
| Policy | Behavior | Default |
|---|---|---|
| Deny | `@env` resolution fails closed — every access is EnvironmentAccessDenied unless explicitly permitted | Yes |
| Allowlist | Only named variables may be read | No |
| AllowAll | Any variable may be read | No |

Rationale: parsing alone must never touch the environment (§2.2 rule 2; §12.1). Only the explicit Env phase, under this policy, may.

### 9.4 Tag Registry Scope
The set of registered external tags is scoped to a single parse operation. No global, shared, or ambient registry exists — each parse call supplies its own registrations.

---

## 10. Extraction Semantics

### 10.1 Path Syntax
Dotted for object keys, indexed for array elements: `database.host`, `servers.0.port`.

### 10.2 Lookup Rules
A path "exists" if it resolves to any node, including an explicit `null`. A path resolving to nothing (no such key/index) does not exist. These are distinct outcomes and must not be conflated.

### 10.3 Access Contract
| Access Mode | On Missing | On Wrong Type | On Unresolved Tag |
|---|---|---|---|
| Required | Error (MissingValue) | Error (TypeMismatch) | Error (UnresolvedTag) |
| OrDefault | Returns the supplied default | Returns the supplied default | Returns the supplied default |
| OrNull | Returns null/absent | Returns null/absent | Returns null/absent |

OrDefault and OrNull never raise an error under any of the three conditions; Required always does.

---

## 11. Serialization

### 11.1 Round-Trip Guarantees
| Survives Parse → Write | Does Not Survive |
|---|---|
| Structure, key order, values, tag syntax (unresolved references) | Resolved tag values (writer operates pre-transform — §11.4) |
| Comments (if retention enabled, §9.2) | Comments (if retention disabled) |

### 11.2 Comment Preservation Rules
Comments are written back only if retained at parse time (§9.2). A tree parsed without retention cannot regenerate comments on write.

### 11.3 Determinism Rules
Given the same input tree and the same options, output is byte-identical across runs: stable key order (§5.2), stable formatting.

### 11.4 Pre-Transform Writing Requirement
Normative: the writer operates on the tree as it exists immediately after parsing — before the Env, Reference, or Merge phases run. An `@env` reference is written back as `@env('VAR')` tag syntax, never as its resolved value.

```jsonx
// input:  host: @env('DB_HOST')
// correct output:   host: @env('DB_HOST')
// incorrect output: host: "prod-db-01"   <- would leak a resolved value
```

---

## 12. Security Model

### 12.1 Trust Boundary Definition
| Format | Tag-Capable | Safe for Untrusted Input |
|---|---|---|
| `Format.Json` | No | Yes, by construction |
| `Format.Json5` | No | Yes, by construction |
| `Format.Jsonx` | Yes | Only under `EnvAccessPolicy.Deny` and absent untrusted external tag registrations |

Tag syntax does not parse at all under `Json`/`Json5` — there is no grammar production for it. Risk is fully scoped to the deliberate choice of `Format.Jsonx`.

### 12.2 Threats Considered
| # | Threat | Scenario |
|---|---|---|
| 1 | Environment over-exposure | Untrusted jsonx text reads an arbitrary environment variable via `@env`. |
| 2 | Secret leakage via errors | A resolved secret value appears in an error message, log, or diagnostic output. |
| 3 | Secret leakage via round-trip | A resolved secret is written back to text by the serializer. |
| 4 | Literal secret commitment | A secret is typed directly into a config file and committed to source control. |

### 12.3 Mitigations by Mechanism
| Threat | Mitigation | Specified In |
|---|---|---|
| 1 | `EnvAccessPolicy` defaults to Deny | §9.3 |
| 2 | Error messages never include a value whose provenance traces to `@env` | §8.3 |
| 3 | Writer operates on pre-transform trees | §11.4 |
| 4 | `@secret` rejects a literal argument, requiring an approved source | §6.6 |

### 12.4 Worked Example
```jsonx
// valid — sourced from @env
password: @secret(@env('DB_PASSWORD')),
// rejected
password: @secret('hunter2'),
```
The second form produces:
```
Category: SyntaxError (tag argument rejected)
Path: password
Message: @secret requires an argument sourced from an approved tag (e.g. @env); a literal value was given.
```

Scope note: `@secret` protects the authoring/repository layer — it prevents a literal from being committed. It does not protect a compiled/shipped binary; any value baked into a distributed artifact (mobile app, etc.) is extractable regardless of how it was sourced in the config file. See `mobile-security-scope.md`.

---

## 13. Compatibility

### 13.1 JSON5 Superset Claim
Any `.jsonx` document that uses no `@tag(...)` syntax and no `"""..."""` strings is valid JSON5. Any valid JSON5 document is valid `Format.Jsonx` input. This is a grammar-design claim; empirical verification is via §14.

### 13.2 JSON Conformance Claim
`Format.Json` conforms to RFC 8259 in full; verification is via §14.

### 13.3 Spec Versioning Policy
Stated at the top of this document. Summarized: behavioral changes bump the version and are logged in Appendix F; editorial changes do not.

---

## 14. Conformance

### 14.1 Test Suite Structure
| Suite | Source | Scope | Isolation |
|---|---|---|---|
| `nst/JSONTestSuite` | External, vendored | `Format.Json` | Isolated resource tree, unmodified from upstream |
| Official `json5-tests` | External, vendored | `Format.Json5` | Isolated resource tree, unmodified from upstream |
| `jju` portable suite (basic tier) | External, vendored | `Format.Json5` (supplementary) | Isolated resource tree, unmodified from upstream |
| jsonx dialect fixtures | Authored by this project | `Format.Jsonx` additions only (§3.3) | Separate from vendored suites |

### 14.2 Pass/Fail Criteria
| Format | Criterion |
|---|---|
| `Json` | 100% of `nst/JSONTestSuite`'s accept/reject expectations met |
| `Json5` | 100% of the official suite's expectations met; jju basic tier green |
| `Jsonx` | All dialect-specific fixtures pass; inherits `Json5`'s criterion in full (superset requirement, §13.1) |
| `JsonL` | Each line independently meets the `Json` criterion |

---

## 15. Appendices

### A. Full Grammar Diff
Consolidated from §3.3.3 and §4.1 — the complete set of jsonx-dialect additions over JSON5, in one place. JSON and JSON5's own grammars are not reproduced — see §3.1–3.2.

| Production | Definition |
|---|---|
| `value` | ...JSON5's value... `\|` `tag` |
| `string` | ...JSON5's string... `\|` `tripleQuotedString` |
| `tag` | `"@" [namespace "."] identifier "(" [argList] ")"` |
| `tripleQuotedString` | `'"""'` { any char except unescaped `'"""'` } `'"""'` |

### B. Reserved Names
| Name | Kind | Reserved As |
|---|---|---|
| `kiit` | Namespace | Unavailable to external tag registration |
| `jsonx` | Namespace | Unavailable to external tag registration |

### C. External Extension Points
A conformant implementation intended to support sibling tools (e.g. a related library reusing jsonx's value/tag parsing for its own grammar) should expose:
1. **Value-level parsing**: a callable entry point for parsing a single value (not just a whole document), so a sibling grammar can delegate leaf-level parsing to it.
2. **Tag resolution access**: the tag registry and resolution contract (§6.5), so a sibling tool can reuse tag resolution without reimplementing it.

This is a capability requirement, not an API specification — the exact shape is implementation-defined.

### D. Rejected Alternatives *(non-normative)*

Before specifying jsonx's grammar and extension model, a range of existing configuration formats and implementations were surveyed for lessons on what to adopt and what to avoid; that general survey isn't named here (see the PRD's competitive analysis for it). This appendix records only the specific alternatives directly tied to a documented decision below. Status distinguishes permanently rejected ideas from merely deferred ones.

| # | Decision Area | Alternative Considered | Reason Rejected | Status | Related (§) | Reference |
|---|---|---|---|---|---|---|
| 1 | String/comma syntax | Quoteless strings & optional commas | Grammar ambiguity; hurts AI generation/repair reliability | Rejected | §2.1, §2.2 | Hjson (hjson.github.io), MAML (maml.dev) |
| 2 | Tag argument grammar | `TagKind.Structural` / bounded token slice | No current tag needs custom sub-grammar — `@table` proved expressible as an ordinary object argument | Deferred | §6.4, §6.6 | — |
| 3 | Tag namespacing | `@enum.{type}(...)` shared namespace | Conflates tag origin (who registered it) with category (what kind); reopens collision risk the namespace rule exists to prevent | Rejected | §6.3 | — |
| 4 | Resolution ordering | Resolver `runsAfter` dependency graph + topological sort | Over-engineered for the known, small resolver set; order not visible in one place | Superseded | §7.1 | — |
| 5 | Tag registry scope | Global mutable tag registry | Thread-safety risk; poor testability; cross-library namespace collision | Rejected | §9.4 | — |
| 6 | Error taxonomy | Host-language error/exception system embedded directly in the spec | Spec must stay implementation-agnostic; embedding one library's taxonomy ties portability to one language | Excluded | §8.5 | — |
| 7 | Extensibility scope | Scripting constructs (`@var`, `@if`, `@loop`) | Breaks declarative/no-branching/no-state admission rules; unbounded security surface; breaks read-and-know-the-output auditability | Rejected | §2.2, §2.3 | — |
| 8 | I/O scope | `@file(path)` content loading | Violates the no-I/O admission rule directly | Rejected | §2.2 (rule 2) | — |
| 9 | Naming/positioning | Renaming the library to signal untrusted-input safety | Communication device, not a technical control; discards existing positioning | Rejected | §12.1 | — |
| 10 | Built-in tag set | Removing `@env` as a built-in | Pushes consumers toward unreviewed, unhardened reimplementations of the same capability | Rejected | §6.2 | — |
| 11 | `@table` argument grammar | Newline-delimited row syntax inside `{ }` | Implicit, whitespace-significant grammar — same defect as item 1 | Superseded | §6.6 | — |

### E. Glossary
See §1.3 (Terminology) — the single source of truth for defined terms in this document; not duplicated here.

### F. Change Log / Version History
| Version | Summary |
|---|---|
| v0.1 | Initial complete specification, consolidating the full design conversation (grammar, tags, resolution pipeline, error taxonomy, security model, compatibility claims) into one implementation-agnostic document. |
