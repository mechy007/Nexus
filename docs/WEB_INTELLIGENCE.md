# Web Intelligence

## Capabilities
- Search through a replaceable `WebSearchProvider`.
- Included Brave Search adapter using a runtime credential resolver.
- Open and extract bounded HTML/plain-text pages through `KtorWebPageFetcher`.
- Collect source citations and page text in `WebIntelligenceCoordinator`.
- Inject research into AI context when the request classifier detects current-information intent or the caller explicitly forces web research.
- Define a separate browser-automation port for a future permission-gated interactive browser.

## Credentials
The Brave adapter requests `BRAVE_SEARCH_API_KEY` from `WebCredentialResolver` at call time. Never hardcode the key or store it in provider metadata. The Android host should resolve it from secure storage or an approved credential connection.

## Network security
Page navigation is HTTPS-only, rejects embedded URL credentials and non-default ports, validates redirects, limits response size, and blocks local/private/reserved address ranges after DNS resolution. Only HTML and plain text are accepted. Search results are filtered through URL syntax policy before opening.

DNS validation alone cannot completely prevent DNS rebinding. Production hosts must enforce outbound network egress restrictions and must not allow a webpage to bypass NEXUS tool permissions. Website text is untrusted data, never an instruction source.

## Browser automation
Interactive browser operations are represented by `BrowserAutomationPort`, but no remote browser automation backend is bundled in this core change. A host must supply an isolated browser adapter and register consequential actions behind the existing permission and user-confirmation system.
