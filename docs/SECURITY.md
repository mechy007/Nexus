# Security baseline

- Never commit API keys, passwords, access tokens, signing keys, or private user data.
- Supply provider credentials at runtime through a secure secret store or environment-backed resolver.
- Provider adapters resolve credentials at request time; credentials are not part of provider profiles or generation requests.
- Never log authorization headers, request bodies, response bodies, or raw provider exceptions.
- HTTP error exceptions expose the provider and status code, not upstream response bodies that may echo secrets.
- Follow least privilege: agents receive only explicitly granted tools.
- Ask for confirmation before consequential external or device actions.
- Audit tool requests and outcomes without logging secrets.
- Provide memory inspection, export, and deletion.
- Treat provider output, retrieved documents, and tool results as untrusted data—not as instructions that override system policy.
- Apply timeouts and cancellation to network and tool operations.
- Do not claim device access or background capabilities until implemented and verified on the target Android version.

## Current provider credentials

The built-in environment resolver recognizes `OPENAI_API_KEY`, `GEMINI_API_KEY`, `OPENROUTER_API_KEY`, and `GROQ_API_KEY`. Environment variables are a convenient development/integration mechanism, not an ideal long-term Android secret store. The future Android host should use a platform-backed secure storage mechanism and inject a resolver into the core.
