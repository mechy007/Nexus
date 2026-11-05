# Automatic Learning

## Learning targets
The core permits automatic candidate updates only for routing weights, approved prompt templates, context-retrieval weights and reasoning strategy settings. Security rules, permissions, agent identity, privacy settings and retention policies are not learning targets.

## Lifecycle
1. Record bounded outcome observations.
2. Ask an injected candidate proposer to suggest a versioned configuration.
3. Evaluate baseline and candidate using the same evaluator/dataset.
4. Reject safety regressions, insufficient quality gains, and excessive latency or cost regressions.
5. Promote with an expected-version check; concurrent configuration changes cause rejection.
6. Keep the previous version available to the host for rollback.

## Host responsibilities
The core supplies observation, proposal, evaluation and versioned-store contracts plus in-memory development implementations. Production hosts must provide durable storage, a trustworthy evaluation dataset, feedback/outcome instrumentation, candidate generation, audit records and rollback. Automatic learning is not model fine-tuning and must not train on raw conversations without a separate consent and data-governance process.
