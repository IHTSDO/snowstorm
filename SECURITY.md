# Security Policy

SNOMED International takes the security of our software seriously. We appreciate the efforts of security researchers and the wider community in helping us keep our tools safe for the members and users who rely on them.

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues, discussions, or pull requests.**

Instead, please use GitHub's private vulnerability reporting feature for this repository:

1. Go to the **Security** tab of this repository.
2. Click **Report a vulnerability**.
3. Fill in as much detail as you can — affected version(s), steps to reproduce, potential impact, and any proof-of-concept code.

This creates a private advisory visible only to us and you, so the issue can be discussed and fixed before it's public.

*(Don't see a Security tab or the report option? Email us instead — see [Alternative contact](#alternative-contact) below.)*

## What to Expect

| Stage | Timing |
|---|---|
| Acknowledgement of your report | Within 2 business days |
| Initial severity assessment | Within 5 business days |
| Status updates | At least every 2 weeks until resolved |
| Public disclosure / CVE | After a fix is released, typically 14–30 days later |

Response and fix timelines depend on severity — critical issues are prioritised well ahead of this schedule.

## No Bounty Program

SNOMED International is a non-profit organisation, and we do not operate a paid bug bounty program. We're not able to offer financial rewards for vulnerability reports.

What we **can** offer is credit: with your permission, we will publicly acknowledge your contribution in the security advisory, release notes, and/or a project acknowledgements page. Let us know in your report whether you'd like to be named, remain anonymous, or use a specific handle/affiliation.

## Scope

This policy covers vulnerabilities in code maintained in this repository. Vulnerabilities in third-party dependencies should ideally be reported upstream to the relevant project directly, but we're happy to be a relay if you're unsure where to send it — please still use private reporting rather than a public issue.

## Out of Scope/Prohibited Testing

* Attacks against SNOMED International hosted environments or public infrastructure (including DoS/DDoS). Please test using local, self-hosted builds of the repository.
* Abuse of CI/CD pipelines, build systems, or GitHub Actions.
* Social engineering, phishing, or physical attacks against maintainers, staff, or contributors.
* Publicly submitting exploit code or backdoors via open Pull Requests or public Issues.

## Safe Harbor

If you conduct security research in good faith and strictly in accordance with this policy:

* Authorisation: We consider your research to be authorised within the meaning of Section 1 of the UK Computer Misuse Act 1990 (and equivalent international legislation).
* Legal Protection: SNOMED International will not initiate or support legal action, nor report your activities to law enforcement, for accidental or good-faith research that complies with this policy.
* Data Privacy: You must comply with the UK Data Protection Act 2018 and UK GDPR (and equivalent international Data Protection legislation). Research must not involve accessing, modifying, exfiltrating, or retaining any personal data or private user credentials.
* If your research involves data destruction, extortion, exfiltration of personal data, or intentional disruption of systems, your access is unauthorised, you are in breach of this policy, and no Safe Harbor protection applies.

## Alternative Contact

If you're unable to use GitHub's private vulnerability reporting, you can reach us at **security@snomed.org**.

## Coordinated Disclosure

We ask that you give us a reasonable opportunity to investigate and address a vulnerability before any public disclosure. We commit to working with you in good faith on a disclosure timeline once a fix is available.

Thank you for helping keep SNOMED International's software secure.
