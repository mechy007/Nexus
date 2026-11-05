# Security baseline

- Never commit API keys, passwords, access tokens, signing keys, or private user data.
- Supply provider credentials at runtime through a secure secret store.
- Follow least privilege: agents receive only explicitly granted tools.
- Ask for confirmation before consequential external or device actions.
- Audit tool requests and outcomes without logging secrets.
- Provide memory inspection, export, and deletion.
- Treat provider output, retrieved documents, and tool results as untrusted data—not as instructions that override system policy.
- Apply timeouts and cancellation to network and tool operations.
- Do not claim device access or background capabilities until implemented and verified on the target Android version.
