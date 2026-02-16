# Security Policy

## Our Commitment

Security is critical for Studio Camera. The app handles device pairing, network communication, and media transfer.
We prioritize secure defaults, encrypted storage, and safe session handling.

## Supported Versions

Studio Camera is currently in pre-release development.

Security fixes are provided for:

- `main` branch (latest commit)

After 1.0.0 GA, this policy will be expanded with an explicit support matrix.

## Reporting a Vulnerability

Prefer **GitHub Private Vulnerability Reporting** (Security Advisories) if enabled on this repository. Otherwise, report privately via email.

- Email: studiocamera+security@lennyobez.com
- Subject: `[SECURITY] <short summary>`
- Include:
  - affected version/commit
  - impact and attack scenario
  - reproduction steps or PoC (safe and minimal)
  - any mitigations you're aware of

**Do not include real device credentials, API keys, or pairing tokens in reports.** Use synthetic values only.

### Response targets

- Acknowledgment: within 48 hours
- Initial triage: within 7 days
- Fix timeline: depends on severity and complexity

### Severity classification

| Severity     | Description                                                              | Examples                                                                     |
| ------------ | ------------------------------------------------------------------------ | ---------------------------------------------------------------------------- |
| **Critical** | Remote code execution, authentication bypass, credential exfiltration    | Pairing token leak, session hijack, arbitrary command execution on device     |
| **High**     | Privilege escalation, significant data exposure, session fixation         | Unauthorized camera access, media exfiltration, MITM on pairing flow         |
| **Medium**   | Limited impact requiring specific conditions or user interaction          | Stored XSS in device names, SSRF via discovery, timing side-channels         |
| **Low**      | Minor issues, information disclosure with minimal impact                 | Verbose error messages exposing internal state, missing certificate pinning   |

Critical and High issues are prioritized for immediate patching. Medium and Low issues are addressed in the next scheduled release unless the risk profile changes.

## Coordinated disclosure

We follow coordinated disclosure.
Please do not publish details until a fix is available, unless we explicitly agree otherwise.

## Security guidelines for contributors

- Never commit secrets, private keys, pairing tokens, or device credentials.
- Use `androidx.security:security-crypto` for sensitive local storage on Android.
- All network communication must use TLS.
- Security-relevant changes require:
  - tests
  - documentation updates
  - clear threat model notes when applicable
