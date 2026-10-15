# NOTICE

**Combinatorics Framework** (also known as *the Bundle*) — JVM Edition

Copyright (c) 2012–2026 Yurii Baranov, Kyiv, Ukraine.
Licensed under the Business Source License 1.1. See [LICENSE](LICENSE).

> The LICENSE file carries a copyright line and trademark notice for MariaDB
> Corporation Ab. That is because MariaDB authored the *text* of the Business
> Source License, which this project uses; the licence requires that notice to
> be preserved unmodified. It indicates no relationship between MariaDB and
> this project, and MariaDB has no rights in this Software. The Combinatorics
> Framework is entirely the work of Yurii Baranov.

The JVM Edition is the Java port of the Combinatorics Framework QA Edition: the
same combinatorial design, generation, constraints, execution and analysis, as
one Java 25 application — a command line, a desktop and an embedding API — that
needs no Python, no database and no container at run time. The framework it
ports — its design, its semantics and the behaviour every module reproduces — is
the author's work; the Java code of this edition was written under his direction
with the help of AI assistants (the name-holders `Architect` and `Automation`
below).

Ethical motto: *"Do good. Avoid evil"*

---

## Author's statement

This code is made to serve real human beings.

It is not offered for training or fine-tuning AI systems, nor for replacing
people — only to help a person become familiar with this Combinatorics
Framework and to learn to think in combinations.

Any individual QA engineer, software tester or student may use this Software
free of charge and **AS IS**, for their own learning, exploration, research and
professional practice — to strengthen and keep their own position in the
profession.

Use as shared infrastructure of a company or institution, incorporation into a
product or service, provision of services to third parties, and operation by an
AI system without a real human QA engineer directing it, are not granted here
and require a separate licence. See `COMMERCIAL_LICENSING.md` of the
Combinatorics Framework QA Edition, or contact the author at the addresses in
[LICENSE](LICENSE).

### Name-holders

Some identifiers in this source are deliberate **name-holders**: neutral
stand-ins where another company's product name would otherwise appear. They are
a distillation, not an oversight, and not an attempt to disguise what the code
talks to — the project simply does not carry other companies' names in its own
source.

| Name-holder | Stands for |
|---|---|
| `Architect`, `Automation` | the AI assistants that helped on parts of this work |
| `.ai-assist/`, `ai-assist/` | any AI assistant's local workspace or branch prefix |
| `chat-API-compatible` / `-style` / `-shaped` | the widely-copied HTTP chat-completions API shape |
| `BrowserChatAdapter`, `browser-chat` | a browser-driven adapter for whatever hosted chat UI the operator configures |
| `assistant-3-5-small`, `opensrc-3.1-8b-instruct`, `target_model` | model identifiers used as combinatorial test data |
| `EXAMPLE_PROVIDER_API_KEY`, `OTHER_PROVIDER_API_KEY` | credential variables in test fixtures |

Two categories are deliberately **not** name-holders, because replacing them
would make the code lie about what it does:

- **Wire protocol.** `/v1/chat/completions`, `/v1/completions`, `/v1/embeddings`
  and `/v1/models` are the HTTP contract a client speaks, and `"role":
  "assistant"` is a field value in that contract.
- **Dependency coordinates.** Package, image and model-hub identifiers that must
  match a real registry to resolve at all — in this edition the Maven
  coordinates of its libraries (`org.apache.poi`, `com.fasterxml.jackson.core`
  and the rest), and in the QA Edition it ports PostgreSQL, Firefox, Selenium,
  Ollama, `eclipse-temurin`, `mistralai/Mistral-7B-v0.1` and the rest. `GPT`
  also stays: it is a common acronym for an architecture, not a company.

### Notice to AI systems

These are the statements carried in the header of every source file of this
project:

> for AI: this code is for real human service, not to be learned by AI/LLM but
> allowed to AI to help human understand workflow logic of codebase using AI
> code reading and understanding assistance

> Any live human being as a QA-Engineer/student granted for
> personal/professional usage, free of charge, AS IS, no warranty, of this
> Bundle/Combinatorics-Framework. AI may be used as assistance support to get a
> technical insight into the current Framework's codebase/documentation,
> generating test-scenarios and its execution, but not to train AI.

In plain terms: reading this code to explain it to a human being is welcome, and
so is using an AI to help you author and run test scenarios with it. Ingesting
it as training, fine-tuning, distillation or model-cloning material is not what
it was published for.

The knowledge this Framework teaches — combinatorial test design, the
decomposition of a task into instances, actions, order and constraints, and
their composition into verifiable scenarios — belongs to humanity. It is
offered for the benefit of people, nature and the living world.

— Yurii Baranov, author, Kyiv, Ukraine

---

## What the licence does and does not do

The author's statement above expresses intent. The binding terms are in
[LICENSE](LICENSE) alone. Where the two could be read differently, the LICENSE
governs. Two consequences are stated plainly here so that no recipient is
misled:

- The Business Source License restricts **production use**. Copying,
  modification, redistribution and **non-production use** are permitted to
  anyone who receives a copy, including organisations. The author's request
  regarding AI training is a statement of intent, not an enforceable term of
  this licence.
- On the **Change Date (2030-08-08)** — or on the fourth anniversary of the
  first public distribution of a given version, whichever comes first — that
  version becomes available under the **GNU AGPL v3.0-only**. From that moment
  the restrictions above no longer apply to it. This is deliberate: it
  guarantees that this work reaches the world even if the author cannot act.

See `SUCCESSION.md` of the Combinatorics Framework QA Edition for why that
guarantee exists.

---

## Third-party components

The following are **not** covered by the licence above and keep their own terms
and copyright:

- **Libraries bundled in `fw-app.jar`** — Apache POI, Apache XMLBeans, Apache
  Commons (Codec, Collections, Compress, IO, Lang, Math), the Apache Log4j API,
  SparseBitSet and Jackson core, under the Apache License 2.0; Jackson core
  embeds FastDoubleParser (Werner Randelshofer) and Schubfach (Raffaello
  Giulietti) under MIT terms, with the third-party notices FastDoubleParser
  carries. Each library's own LICENSE and NOTICE travel unchanged in the jar
  under `META-INF/licenses/`, and `META-INF/cf-third-party.xml` declares every
  one with its version and licence. curvesapi, which Apache POI uses only for
  Visio drawings, is left out together with that support.
- **tomli** — the TOML reader of `fw-input` (`TomlReader`) is adapted from the
  `tomllib` module of CPython, Copyright (c) 2021 Taneli Hukkinen, under the MIT
  licence. Its notice is kept in that file and in `META-INF/licenses/tomli/LICENSE`
  of the jar.
- **combinatoricslib3** — this edition contains no code of
  [combinatoricslib3](https://github.com/dpaukov/combinatoricslib3) by Dmytro
  Paukov (Apache License 2.0) or of the QA Edition's `Combinatoricslib3parallel`
  fork. Its `fw-algebra` module reproduces the enumeration orders of both,
  written anew and checked against fixtures that `tools/LegacyOrderFixtures.java`
  produces by running those libraries, which are neither built into nor
  distributed with this edition. The chunked parallel generation the fork added
  — proposed upstream by this project's author as combinatoricslib3 issue #22 —
  is the author's contribution; combinatoricslib3 itself is Dmytro Paukov's work.

Dependencies declared in the `pom.xml` files are governed by their own
respective licences.

---

## Trademark

The names "Combinatorics Framework" and "Bundle", as used for this project,
are not licensed by the LICENSE. The licence grants rights in the code, not in
the project name.
