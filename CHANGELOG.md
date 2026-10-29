# Changelog

User-visible changes are recorded here. A section in this file is not proof that an artifact or tag has been published. Release notes should name the tested backend and `atom-crm-web` commit IDs together.

## Unreleased

### Added

- Strengthen executable architecture guards and add regression fixtures while preserving CRM-specific facade, attachment, naming, and persistence-model boundaries.
- Add five CRM-adapted Agent Skills, a generic domain-contract guide, and `make agent-check` with explicit fast/full checks, conservative affected-change selection, and structured results. Full checks retain the existing Docker and database-report gate.
- Provide an explicitly configured SMTP adapter for user welcome notifications in production. Production still requires a real notification adapter; the development logging adapter does not claim delivery.
- Add a non-root backend runtime container image definition and a production environment-variable example.

### Changed

- Refresh stable dependencies as of 2026-10-29: Temurin 27.0.1.0.2+1 in CI/runtime, MyBatis-Plus 3.5.18.1, Log4j 2.26.1, Prometheus client 1.10.0 and current transitive utility/test libraries. Keep Maven 3.10.0 and Spring Boot 4.1.2, already latest stable, and retain MyBatis 3.5.19 because 3.6.0 removes APIs required by MyBatis-Plus.
- Require JDK 27.0.1 or newer for builds, verify runtime JRE archive checksums, and update development/test services to MySQL 26.10.0 Innovation and Redis 8.10.3. See the [upgrade guidance](docs/deployment.md#数据与升级) before changing existing database volumes.
- Align engineering tooling with Atom Archetype 2.3.0 while retaining CRM business modules, human/connector identity contracts, and database migration history.
- **Breaking toolchain change:** require Java 27 for builds and runtime, Maven 3.10.0, and atom-common 1.2.0. Spring Boot remains on 4.1.2; application versioning remains independent of the archetype.
- Run the development application from the Maven reactor so dependent modules are rebuilt from current sources.

### Fixed

- Restore the configured database-test skip flag after condition tests so a fast run cannot accidentally enable later MySQL tests.
- Exclude local configuration and environment files from packaged application resources.
- Resolve the SMTP password-reset adapter after mail auto-configuration; explicitly enabling recovery without an SMTP sender now fails startup instead of silently omitting delivery.
- Require Docker and actual, unskipped MySQL test evidence in the release gate; pin CI actions, retain reports, and configure dependency update checks.
- Return a safe 404 for missing framework routes/resources, including disabled API documentation, without exposing request paths or exception details.

### Documentation

- Add contributor guidance, private security-reporting instructions, and issue/pull-request templates.
- Document the two-repository local startup and first-installation password flow in both README languages, with a dedicated production deployment guide.
- Remove internal batch plans, historical acceptance records, and the obsolete source-deletion script; consolidate maintained integration contracts into feature guides.

## 1.0.0 baseline

The source declares Maven version `1.0.0`. Current capabilities and integration prerequisites are documented in the [README](README.en.md), [configuration guide](docs/configuration.md), and [deployment guide](docs/deployment.md). Run the release gate for the actual revision being distributed and record its test results. No publication date is asserted here.
