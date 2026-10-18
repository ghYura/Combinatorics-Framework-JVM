# Ordered actions and optional interruptions

Counts use the executed samples selected in the current Framework checkpoints. An ordered triple is three dispatched control calls, including repeated calls and calls after cancellation. It is not three successful state transitions: refusal and idempotence are part of the contract.

| Optional branch | Cases | Positional triples / 27 | Contiguous pairs / 9 | Contiguous triples / 27 | Optional action fired |
|---|---:|---:|---:|---:|---:|
| absent | 28 | 27 | 9 | 27 | 0 |
| cancel | 28 | 27 | 9 | 0 | 28 |
| refresh | 28 | 27 | 9 | 0 | 28 |

The mandatory action triples remain 27/27 in every branch. A sudden action between the first and second calls breaks their adjacency; the interrupted branches therefore have no contiguous three-action mandatory triple. The uninterrupted branch supplies all 9 pairs and 27 triples. Setup hooks and cleanup calls are excluded from the three user-action positions.

| Family | Selected optional branches | Observed action dispatches |
|---|---|---|
| `t4_data` | {'absent': 18, 'undo': 18, 'validate': 18} | {'undo': 18, 'validate': 18} |
| `t5_candidates` | {'absent': 7, 'cancel': 7} | {'cancel': 7} |
| `t6_run` | {'absent': 30, 'cancel': 30} | {'cancel': 30} |
| `t6_sequences` | {'absent': 28, 'cancel': 28, 'refresh': 28} | {'cancel': 28, 'refresh': 28} |
| `t7_campaign` | {'absent': 6, 'cancel': 6} | {'cancel': 6} |

Sieve draft ordering: 27 cases dispatch draft → attempted switch → resolution → switch; 27 dispatch draft → resolution → switch. Each branch includes absent/undo/validate variants. Invalid data Apply exercises the exact draft validator and discards the rejected draft because its modal error display is excluded headless. These two order obligations are covered; no claim is made about all possible GUI histories.

The supplementary guard sequence activates Duplicate → rule Up → rule Down → condition construction, restores the draft, then condition Down → Up → Delete → Apply. Cancel-work is dispatched in the same EDT callback that starts impact validation, while the action is enabled. The initial graph scenarios also cover pair → save → undo → redo.

Actual FW_Optional sheets, expanded by the Framework, generate the omission and insertion branches. Traces verify dispatch; a selected optional value alone does not count as execution.
