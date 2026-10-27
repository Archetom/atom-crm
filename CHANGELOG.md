# Changelog

User-visible changes are recorded here. A section in this file is not proof that an artifact or tag has been published. Release notes should name the tested backend and `atom-crm-web` commit IDs together.

## Unreleased

### Added

- Provide an explicitly configured SMTP adapter for user welcome notifications in production. Production still requires a real notification adapter; the development logging adapter does not claim delivery.
- Add a non-root backend runtime container image definition and a production environment-variable example.

### Fixed

- Exclude local configuration and environment files from packaged application resources.
- Resolve the SMTP password-reset adapter after mail auto-configuration; explicitly enabling recovery without an SMTP sender now fails startup instead of silently omitting delivery.
- Require Docker and actual, unskipped MySQL test evidence in the release gate; pin CI actions, retain reports, and configure dependency update checks.
- Return a safe 404 for missing framework routes/resources, including disabled API documentation, without exposing request paths or exception details.

### Documentation

- Add contributor guidance, private security-reporting instructions, and issue/pull-request templates.
- Document the two-repository local startup and first-installation password flow in both README languages, with a dedicated production deployment guide.
- Remove legacy guidance that treated User source deletion as a supported CRM setup step.

## 1.0.0 baseline

The source declares Maven version `1.0.0`. Its frozen scope, deployment requirements, and exclusions are documented in [release-R1.md](release-R1.md); historical verification is recorded in [R1 acceptance](docs/acceptance/R1.md). Re-run the release gate for the actual revision being distributed. No publication date is asserted here.
