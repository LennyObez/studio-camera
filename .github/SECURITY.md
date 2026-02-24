# Security policy

## Our commitment

Security matters for Studio Camera. The app handles device pairing, network communication, and media transfer, so we prioritize secure defaults, encrypted storage, and safe session handling.

## Supported versions

Studio Camera is currently in pre-release development. Security fixes are provided for the `main` branch (latest commit). Once the app reaches general availability, we'll publish a more detailed support matrix.

## Reporting a vulnerability

Please use GitHub Private Vulnerability Reporting (Security Advisories) on this repository. If that's unavailable, email us directly.

- **Email:** studiocamera+security@lennyobez.com
- **Subject:** `[SECURITY] <short summary>`
- **Include:**
  - Affected version or commit
  - Impact and attack scenario
  - Reproduction steps or proof of concept (keep it safe and minimal)
  - Any mitigations you're aware of

**Don't include real device credentials, API keys, or pairing tokens in reports.** Use synthetic values only.

### What to expect

- **Acknowledgment:** within 48 hours
- **Initial triage:** within 7 days
- **Fix timeline:** depends on severity and complexity

### Severity levels

| Severity | What it means | Examples |
|----------|--------------|----------|
| Critical | Remote code execution, auth bypass, credential exfiltration | Pairing token leak, session hijack, arbitrary command execution |
| High | Privilege escalation, significant data exposure, session fixation | Unauthorized camera access, media exfiltration, MITM on pairing |
| Medium | Limited impact, requires specific conditions or user interaction | Stored XSS in device names, SSRF via discovery, timing side-channels |
| Low | Minor issues, information disclosure with minimal impact | Verbose error messages, missing certificate pinning |

Critical and high issues get patched immediately. Medium and low issues are addressed in the next release unless the risk changes.

## Coordinated disclosure

We follow coordinated disclosure. Please don't publish details until a fix is available, unless we agree otherwise.

## For contributors

If you're contributing code, keep these in mind:

- Never commit secrets, private keys, pairing tokens, or device credentials.
- Use `androidx.security:security-crypto` for sensitive local storage on Android.
- All network communication must use TLS.
- Security-relevant changes need tests, documentation updates, and threat model notes where applicable.
