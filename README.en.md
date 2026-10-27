# atom-crm

An open-source CRM backend built with Java 25 and Spring Boot 4.1, using domain-driven design and a multi-module architecture.

[简体中文](README.md) · [Quick start](#quick-start) · [Documentation](#documentation) · [Web frontend](https://github.com/Archetom/atom-crm-web)

## Features

- **Customer management**: customers, contacts, leads, shared pools, and activity records.
- **Sales workflows**: opportunities and sales stages, products, contracts, invoices, receivables, and payment plans.
- **Team collaboration**: organization members, tasks, approvals, announcements, and worklists.
- **Analytics**: sales reports, dashboards, and targets.
- **Access and customization**: tenant isolation, roles, record scopes, field permissions, and custom fields.

This repository provides the backend API. The browser application lives in [atom-crm-web](https://github.com/Archetom/atom-crm-web). Integrations such as telephony, enterprise data sources, and file storage require additional configuration; see the [configuration guide](docs/configuration.md).

## Quick start

### Requirements

- JDK 25
- Docker and Docker Compose
- The Maven Wrapper is included; no separate Maven installation is needed. Redis is optional and disabled by default.

### Start the backend

```bash
git clone https://github.com/Archetom/atom-crm.git
cd atom-crm
docker compose up -d mysql
sh ./mvnw clean install
sh ./mvnw -f start/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

Once running, open the [health endpoint](http://localhost:8080/actuator/health) or [Swagger UI](http://localhost:8080/swagger-ui/index.html). Local settings are in [`conf/application-dev.yml`](conf/application-dev.yml).

### Start the frontend and create an account

In another terminal, start the frontend using the Node.js and pnpm versions specified in its [README](https://github.com/Archetom/atom-crm-web#readme):

```bash
git clone https://github.com/Archetom/atom-crm-web.git
cd atom-crm-web
pnpm install --frozen-lockfile
pnpm dev
```

Open the address printed by the frontend, then visit `/setup` to create the first administrator. Use `/login` for subsequent sign-ins. There are no default credentials, and normal use does not require development identity headers. The frontend proxies API requests to `http://localhost:8080` by default.

These steps are for local development. See the [deployment guide](docs/deployment.md) for production HTTPS, database, session-key, and email configuration.

## Development and testing

```bash
sh ./mvnw test                                        # Includes MySQL integration tests when Docker is available
sh ./mvnw test -Datom.test.skip-database-tests=true    # Explicitly skips database tests
```

Database tests are skipped when Docker is unavailable. See the [testing guide](docs/test-guide.md) for full verification requirements.

## Documentation

| Guide | Topics |
| --- | --- |
| [Deployment](docs/deployment.md) | Build artifacts, containers, production setup, and upgrades |
| [Configuration](docs/configuration.md) | Authentication, database, cache, and optional integrations |
| [API reference](docs/api-reference.md) | HTTP endpoints, request/response contracts, and permissions |
| [Architecture](docs/architecture.md) | Module responsibilities, domain model, and transactions |
| [Development](docs/usage-guide.md) | Adding use cases, endpoints, and migrations |
| [Testing](docs/test-guide.md) | Test layers and verification commands |

[Changelog](CHANGELOG.md) · [R1 release scope](release-R1.md) · [Full documentation index](llms.txt)

## Contributing

Issues and pull requests are welcome. Read the [contributing guide](CONTRIBUTING.md) before getting started. Report vulnerabilities privately through the process in [SECURITY.md](SECURITY.md).

## License

[MIT](LICENSE)
