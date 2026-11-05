# Provider resilience

`ResilientProviderRouter` is an opt-in implementation of the existing `ProviderRouter` port. It preserves capability filtering and preferred-provider ordering while adding bounded retries, exponential backoff, fallback, and per-provider cooldowns.

## Policy

- Retry attempts, initial/max backoff, failure threshold, and cooldown are configurable.
- IO failures and HTTP 408, 429, and 5xx responses are transient by default.
- Other failures move to the next compatible provider without retry.
- A provider that crosses the failure threshold is skipped until its cooldown expires.
- Success resets that provider's in-memory failure state.
- Coroutine cancellation is propagated immediately and is never retried.

## State and privacy

Health state is in-memory and contains only failure counts, a cooldown timestamp, and an exception class label. It does not retain prompts, model responses, request bodies, or credentials. Hosts can inject a clock and a health tracker for deterministic tests.

## Verification boundary

Unit tests cover transient retry, fallback, cooldown skipping/expiry, and cancellation. The router does not perform live health probes; it learns health from actual generation attempts. A host may separately schedule a lightweight provider check if its provider supports one.
