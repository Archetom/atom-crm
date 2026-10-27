# Object layering and naming

Names identify both meaning and architectural ownership. Prefer a domain or boundary term over generic names such as `Model`, `Data`, `Manager`, `Helper`, or `Util`.

## Object types

| Suffix or type | Owner | Purpose | Example |
| --- | --- | --- | --- |
| `*Request` | `api` | Public input contract and transport validation | `UserCreateRequest` |
| `*Response` | `api` | Public output contract | `UserResponse` |
| `InitialTenantAdminRequest` / `InitialInstallationStatusResponse` | `api` | The one-time public fresh-system setup contract; it neither accepts tenant/role IDs nor exposes installation facts beyond availability | tenant 1 owner bootstrap |
| `AuthenticatedCaller` | `api` | Verified actor, tenant, and authorities | `AuthenticatedCaller` |
| `AuthenticatedConnector` | `api` | Verified connector reference without actor, tenant, or authorities | `AuthenticatedConnector` |
| `*VO` | `application` | Use-case output before facade mapping | `UserVO` |
| `SystemInstallationStore` | `application.port.out` | Atomic singleton installation marker used to admit exactly one initial tenant-admin command; only an active, login-capable tenant owner closes initial setup | MySQL conditional insert |
| Aggregate/entity | `domain` | Identity, state, invariants, and behavior | `User`, `Customer`, `FieldDefinition`, `SavedView`, `SavedViewCondition`, `ReportDefinition` |
| `CallInteraction` / `CallLeg` | `domain` (future contract) | CRM interaction root plus its bounded normalized participant legs; not provider Call/channel/bridge models | one `AGENT` and one `REMOTE_PARTY` leg |
| `RegisteredIdentifier` | `domain` value object | Scheme-validated registry identity plus orthogonal standing; not a provider company ID, Customer field DTO, or legal-jurisdiction string | LEI, CN USCC, or Companies House number in a governed identity scope |
| `EnterpriseDataReadPolicy` | `application` output-policy contract | Tenant-resolved source kind, TTL and contextual attribution, independent of adapter facts | synthetic `SEARCH_RESULT`/`DETAIL` policy |
| `AttachmentFile` / `AttachmentTargetType` | `domain` aggregate/catalog | Orthogonal file lifecycle plus closed target vocabulary independent of customization/approval object types | `WORK_TASK` first, then eight admitted CRM roots |
| recording media descriptor / stream handle | `application` port (future contract) | Typed WAV/MP3 representation and closeable/cancelable byte source; never a URL, path, file, provider response, header map or full buffer | full stream or one normalized byte range |
| connector readiness snapshot | `application` output/security contract (future contract) | Provider-neutral, per-operation short-lived evidence bound to one connector, adapter build and private binding revision | `START_OUTBOUND=READY`, `RECORDING_READ=UNSUPPORTED` |
| file inspection subject/evidence | `application.port.out.attachment` contract | Exact immutable content version plus controlled detector/scanner evidence; never a path, URL, provider event/tag, raw threat report, parser metadata or full buffer | pinned streamed-engine or managed exact-version result |
| `PlannedObjectLocator` / `StoredObjectIdentity` | `application.port.out.attachment` storage value | Opaque tenant-binding locator plus immutable adapter generation used only for exact store/probe/read/delete reconciliation | S3 key+version or secure-local descriptor behind opaque encoding |
| `AttachmentContentEvidence` | `domain` value | Exact actual bytes plus canonical full-object SHA-256 bound to one immutable file generation | never ETag, mtime, file key, client digest or provider checksum map |
| `AttachmentByteSource` / `AttachmentReadHandle` | `application.port.out.attachment` | Closeable/cancelable bounded byte source and per-operation short-lived build/binding evidence | full or one normalized native range; no URL, Path, File or SDK type |
| `AttachmentUploadOrigin` | `domain` closed value | Exact MEMBER or PUBLIC_MARKETING_FORM origin without fabricating a member for anonymous bytes | one member or one exact public file draft |
| `MarketingFileDraftCredential` | `application.security` | Hash-only capability returned once and consumed once for one canonical form/version/draft; not an authenticated tenant subject | create/upload/status/consume only, never download |
| `AutomationExecutionContext` | `application` / `application.port.out.automation` | Internal exact tenant/kind/occurrence/fence context claimed from MySQL; never an authenticated subject or arbitrary job payload | Customer-pool or Contact-pool release occurrence |
| `AutomationRevisionReconciliation` / `AutomationSchedulingSnapshot` | `application` / `application.port.out.automation` | Fixed-cardinality control-plane revision readiness and ledger census; never exposes tenant, run, pool, actor, payload or exception data | V2 reconciliation result; per-kind run/schedule state gauges |
| `BackgroundWorkPump` | `application` | Scheduler-facing bounded wake-up only; durable ownership remains in automation run store or the owning worker store | automation run pump or attachment worker pump |
| `WorkTaskSeries` / `WorkRecurrenceRule` / `WorkTaskSeriesOccurrence` | `domain` | Versioned Work-owned future-task template, bounded local-calendar rule and independently paged occurrence fact; never an automation schedule PO, cron string, RRULE or job payload | DAILY/WEEKLY/MONTHLY with fixed DST policy |
| `WorkTaskReminderPlan` / `WorkTaskReminderOccurrence` | `domain` plus application work ledger | Task-owned participant-only reminder policy plus exact due execution identity; not a worklist item or task state | BEFORE_DUE, AT_DUE or OVERDUE step |
| `Notification` / `NotificationDelivery` | `domain` inbox fact plus `application` external-effect ledger | Per-recipient in-app fact; provider acceptance and member read remain distinct | Work reminder inbox plus optional EMAIL outbox |
| `NotificationDispatchCommand` / `NotificationDispatchResult` | `application.port.out.notification` | Typed deadline/cancellation-aware one-attempt channel contract with fixed template revision/digest and explicit accepted/rejected/ambiguous result | never a provider DTO, address-bearing public request or header map |
| `OaAnnouncementPublicationOccurrence` | `application` durable work ledger | Exact one-shot announcement generation, database-UTC due/deadline and fenced execution; not an automation run or public schedule DTO | one `(tenant, announcement, generation)` publication fact |
| `NotificationDeliverySourceKind` / `NotificationDeliverySourceAdmission` | `application` | Closed source identity and source-owned current admission without generic source IDs, maps or table routing | Work notification or exact OA recipient snapshot |
| Value object | `domain` | Immutable validated concept | `TenantId`, `Email`, `FieldCode` |
| Domain enum | `domain` | Business state vocabulary | `UserStatus`, `BusinessObjectType` |
| `PageResult<T>` | `domain` | Framework-neutral repository page result | `PageResult<User>` |
| `*PO` | `infra/persistence` | Relational persistence representation | `UserPO` |
| Domain event | `domain` | Immutable fact raised by an aggregate | `UserCreatedEvent` |

API Request/Response types and persistence objects do not belong in `domain`. Infrastructure never exposes `UserPO` through a public contract.

## Mapping path

```text
HTTP JSON -> Request -> application use case -> aggregate -> PO -> MySQL
MySQL -> PO -> reconstituted aggregate -> VO -> Response -> HTTP JSON
provider/PBX -> private transport adapter -> typed normalized call fact -> application ingress -> CallInteraction
authenticated human GET -> media authorization transaction -> typed private-adapter byte stream -> uncached same-origin HTTP body
authenticated human query -> tenant guard -> adapter capability + deployment read policy -> allowlisted observation -> VO -> Response
authenticated human upload/download -> target authorization transaction -> exact-generation storage port -> bounded proxy stream outside transaction
anonymous marketing FILE -> canonical public form + exact draft credential -> quarantined attachment stream -> atomic Lead FILE binding
composition-root pulse -> bounded work pump -> claimed durable tenant/kind/fence -> owning application handler -> tenant-scoped business transaction
composition-root pulse -> claimed OA publication occurrence -> tenant/announcement/current-authority transaction -> recipient snapshot + optional typed delivery
```

- `infra/rest` binds JSON and maps verified human authentication to `AuthenticatedCaller`; the connector normalized ingress maps only a connector-authenticated infrastructure principal to `AuthenticatedConnector`.
- `infra/facade` owns the public Request/Response boundary and maps VO to Response without exposing domain objects.
- `application` validates Request input, creates domain value objects, and uses an Assembler only for aggregate -> VO -> Response output mapping.
- `infra/persistence` maps aggregate to PO and calls `reconstitute` on reads.
- Persistence converters are written explicitly; only `UserPOConverter` uses MapStruct. Where it is used it handles mechanical field mapping only, and validation, defaults, identity, version restoration, and event registration stay explicit either way.

Reporting keeps definition state and executable projections distinct. `ReportDefinition` is the versioned personal aggregate; `ReportDefinitionPO` plus named `filtersJson`/`measuresJson` properties are its storage representation; `ReportDefinitionVO` and `ReportDefinitionResponse` are application and public projections. `CustomReportQuery` is an immutable, fully resolved domain query passed to exactly one owning business repository, not a persistence model and not a public request. It contains typed field references rather than SQL identifiers or expressions.

`ProductQueryRequest.customSortFieldCode` is an API input only. `ProductQueryServiceImpl` resolves it under current PRODUCT field policy into the domain `ProductSort` value containing a definition ID, scalar type and direction; it never carries a caller-supplied SQL identifier. `ProductRepositoryImpl` owns the correlated `crm_product_field_value` order expression and accepts only that resolved domain value. POs and raw scalar storage stay in `infra/persistence`, while `ProductVO` remains the field-filtered application output.

`ReceivableQueryRequest.customSortFieldCode` and `ReturnVisitQueryRequest.customSortFieldCode` follow that same boundary. Their application services resolve a current active, non-built-in, readable and unmasked field to `ReceivableSort` or `ReturnVisitSort`; those domain values contain only definition ID, scalar type and direction. Their repositories own the corresponding correlated custom-value ordering expressions, including numeric casts, and never receive a request-controlled identifier or SQL fragment.

`ContractQueryRequest.customSortFieldCode` and `InvoiceQueryRequest.customSortFieldCode` follow the same boundary. Their application services resolve a current active, non-built-in, readable and unmasked CONTRACT or INVOICE scalar field to `ContractSort` or `InvoiceSort`; those domain values contain only definition ID, scalar type and direction. Their repositories own the corresponding correlated custom-value ordering expressions, including numeric casts, and never receive a request-controlled identifier or SQL fragment.

## Creation and reconstruction

- A `create...` method or domain factory creates new state and may raise a creation event.
- `reconstitute(NamedSnapshot)` restores persisted state by named fields and does not raise events.
- Methods such as `changeStatus`, `changeEmail`, and `delete` enforce state transitions.
- `onPersisted(...)` synchronizes generated identity, audit timestamps, and version after a successful write.

Aggregates do not expose public setters or use Lombok `@Data`. Define equality from aggregate identity or value-object value, not every mutable field.

`AuthenticatedCaller` stays at the API/application boundary. The application validates its tenant and passes a `TenantId` explicitly to domain, repository, and cache operations. Ordinary request bodies do not carry tenant identity, and repositories do not recover it from a `ThreadLocal`.

`AuthenticatedConnector` carries only a core-authenticated opaque reference. A dedicated guard resolves its active tenant registration and commands lock it before writes. Credential and identity projections remain separate narrow application security ports. Native call topology, provider payloads and sensitive address/media values stay behind typed private-adapter boundaries; the current runtime does not expose a production call or recording API. See [connector integration](connector-integration.md).

`RegisteredIdentifier` owns scheme-validated identity and standing; legal jurisdiction and profile facts stay separate. Tenant policy owns source kind, TTL, usage rights and attribution independently of adapter output. The Customer identity relation has a dedicated tenant uniqueness reservation, while adoption provenance stores policy references and selected target locators without a raw profile. Provider-native records and simulator/oracle projections remain in external adapters and test sources. See [enterprise data integration](enterprise-data.md).

`AttachmentFile`, target type and content evidence belong to domain. Opaque locators/generations, byte sources, read handles, inspection/readiness evidence and work stores belong to application output ports. Human command/query/transfer services remain separate from workers, which receive exact durable work context rather than `AuthenticatedCaller`. The target registry explicitly lists supported adapters without reflection or default routing. Structured `fileFields`, ordinary attachments, draft bindings and evidence snapshots remain distinct; none is stored as a generic scalar field map. Raw streaming remains in dedicated REST controllers, while storage/scanner technology stays in `infra/external` and POs stay in `infra/persistence`. See [files and attachments](file-storage.md).

Conformance drivers, fault injectors, native event fixtures and independent oracle projections remain test-private. Their shapes are not production DTOs, domain values, public responses or application ports. Passing a model or simulator does not provide current deployment readiness.

Customer overview keeps the public `CustomerBusinessOverviewResponse` in `api`, the `CustomerBusinessOverviewVO` and `CustomerBusinessOverviewService` orchestration in `application`, and seven typed `*CustomerSummary` projections plus repository capabilities in their owning domain modules. Each persistence adapter implements only its source's aggregate SQL and returns its source projection; it does not return a PO or a `Map` through the domain port. Card availability and cross-source ordering belong to application, while HTTP mapping remains in the customer controller/facade. There is deliberately no `CrossDomainRepository`, generic overview DTO, dynamic card definition, or generic customer-to-record graph API.

Automation keeps scheduled execution in an application control plane rather than inventing a scheduler domain aggregate or public DTO. `AutomationKind` is a closed application catalog; `AutomationScheduleStore` is an output capability; `AutomationSchedulePO`/`AutomationRunPO` and lease SQL terminate in `infra/persistence`; exact Customer/Contact handlers remain in their owning application boundary. `AutomationExecutionContext` is not `AuthenticatedCaller`, `AuthenticatedConnector`, an event or a serialized payload: it carries only the tenant and occurrence evidence returned by an exact durable claim. `BackgroundWorkPump` means only “look for at most bounded work now”; attachment pumps continue to own `AttachmentWorkStore` durability. `start` may schedule and isolate pumps but cannot select tenants, compute business eligibility, settle runs, or interpret provider/file state. See [scheduled automation](architecture.md#scheduling-and-automation-boundary).

Recurrence belongs to the Work domain. `WorkTaskSeries` owns the typed local-calendar rule, task template, future cursor and generation; a generated `WorkTask` stores a named `WorkTaskOrigin` snapshot and otherwise remains an ordinary Work aggregate. Automation sees only the closed `WORK_TASK_RECURRENCE` kind and exact tenant/run context, never a rule, series ID, actor or payload. Work reminders provide task-owned reminder plans, fenced reminder occurrences, and current-member inbox facts, but they are not automation runs or recurrence origins. `NotificationDelivery` remains a notification delivery external boundary: its application ledger may retain a tenant/purpose-domain-separated HMAC endpoint digest, digest-key version and conservative effect-certainty fact after dispatch, while the plaintext endpoint remains only in the short-lived adapter command. See [Work recurrence and notifications](architecture.md#work-recurrence-and-reliable-notification-boundary).

OA scheduled publication stays beside `OaAnnouncement` without turning the scheduling ledger into a domain child collection or automation payload. The domain root owns `SCHEDULED`, frozen selectors and publication generation; the application occurrence/store owns due, lease, retry and terminal mechanics; persistence POs and claim SQL terminate in `infra/persistence`. `OaAnnouncementPublicationCoordinator` accepts either an already-verified human publication context or exact background occurrence context and participates in the caller's command transaction; it never accepts `AuthenticatedCaller` synthesized by a worker. OA notification delivery keeps OA recipient/read rows separate from the Work `Notification` aggregate while evolving `NotificationDelivery` into two explicit source shapes. Each source admission returns a named fixed content variant, and only the final typed command may carry the short-lived plaintext endpoint into `infra/external`. See [OA scheduling and notifications](architecture.md#oa-announcement-scheduling-and-reliable-notification-integration).

Business-file support keeps byte/content/inspection/deletion ownership in the implemented attachment types. Product and Return Visit file support implements explicit Product/ReturnVisit target adapters, typed ordinary/FILE parent columns, and root-owned ordinary/binding withdrawal; Product and ReturnVisit responses expose only structured `fileFields`, never scalar custom fields, snapshots, Activity or worklist facts. Public marketing upload uses a closed `AttachmentUploadOrigin` for `AttachmentFile` attribution: existing human rows retain one member, while a public Marketing candidate names one exact draft and never a synthetic owner. The raw credential is an application-security input returned once; persistence stores only its purpose-separated digest, and API/VO/domain/event/log types never retain it. OA announcement files are represented as ordinary target relations frozen by the OA root lifecycle rather than recipient-level copies or delivery payloads. OA request collaboration represents post-submit OA collaboration as ordinary relations whose access adapter consults the authoritative approval instance; it never mutates `OaRequest.facts`, OA FILE values or approval snapshot types. See [files and attachments](file-storage.md).

Customer CSV export keeps the public `CustomerCsvExportRequest` in `api`, the scoped-read intersection, field selection, scalar normalization, formula-safe encoder, per-replica admission and bounded `CustomerCsvDocument` in `application`, and typed `CustomerExportQuery`/`CustomerExportRow`/ordered `CustomerExportCell` projections in the Customer domain boundary. The Customer persistence adapter reads only fixed built-in columns and parameterized custom scalar values; it never returns a PO, arbitrary object map, JDBC metadata or CSV bytes through the domain port. `infra/rest` owns only fixed download headers and writing the already-completed document after the query transaction. There is deliberately no generic export repository, dynamic object/column router, attachment file, persisted artifact or import DTO. See [Customer CSV export](architecture.md#bounded-customer-csv-export-boundary).

Contact CSV export implements the matching Contact layering without promoting Customer CSV export into a generic business exporter. `ContactCsvExportRequest` is assigned to `api`; Contact access intersection, relationship-target coordination, field selection, scalar normalization, completed `ContactCsvDocument` and byte limits belong to `application`; typed `ContactExportQuery`/`ContactExportRow`/`ContactExportCell` projections belong to the Contact domain repository boundary. Customer and Contact share only an application-internal bounded format encoder and non-queueing process admission lease. Each persistence adapter continues to own fixed columns and parameterized custom values for its exact aggregate, and neither sees an arbitrary object map, dynamic identifier nor CSV bytes. See [Contact CSV export](architecture.md#bounded-contact-csv-export-boundary).

Lead CSV export keeps Lead export equally aggregate-specific. `LeadCsvExportRequest` is in `api`; the independent read/export scope intersection, saved-view and relation-target coordination, field selection, scalar normalization, completed `LeadCsvDocument` and byte limits are in `application`; typed `LeadExportQuery`/`LeadExportRow`/`LeadExportCell` projections are added only to the Lead domain repository port. The Lead persistence adapter projects fixed root columns and parameterized custom scalar values for bounded Lead IDs. It shares only the application-internal CSV encoder and admission lease, never a generic object router, arbitrary column identifier, PO or CSV bytes across the domain boundary. See [Lead CSV export](architecture.md#bounded-lead-csv-export-boundary).

Receivable-plan export follows the same aggregate-specific boundary: `ReceivablePlanCsvExportRequest` remains in `api`; the independent read/export visibility intersection, saved-view and relation filtering, fixed field selection, scalar normalization, completed `ReceivablePlanCsvDocument`, bounded CSV encoder and admission lease remain in `application`; and `ReceivablePlanRepository` exposes only typed plan export projections. Its persistence adapter owns the tenant and owner-scope predicates for both scopes and never receives arbitrary column names or produces CSV bytes. The REST adapter only sets fixed attachment headers and writes the completed document.

The Opportunity pipeline catalog assigns public pipeline/stage requests and responses to `api`, the independently versioned `OpportunityPipeline` and `OpportunityPipelineStage` roots plus exact code/lifecycle policy to `domain`, and permission, tenant-catalog guard, bootstrap, cross-root activation rules and Opportunity-pair validation to `application`. Persistence owns V85, binary code columns, POs/converters/mappers, the MySQL guard and same-tenant pair foreign key. The catalog is not a customization option list or an approval workflow child: customization continues to govern the Opportunity code fields, while workflow-local stages retain their own aggregate and runtime. There is no generic business catalog, arbitrary object/code map or reflective router. See [Opportunity pipeline catalog](architecture.md#opportunity-pipeline-catalog-boundary).

## Naming conventions

| Concern | Convention |
| --- | --- |
| Application write policy | `CommandServiceTemplate` |
| Application read policy | `QueryServiceTemplate` |
| Use-case lifecycle | `ServiceOperation<T>` |
| Public interface | Business name without an `I` prefix, such as `UserService` |
| Implementation | `*Impl` only for a meaningful interface |
| Output port | Business capability, such as `CacheStore` or `PasswordHasher` |
| Adapter | Technology plus capability when useful, such as `RedisCacheService` |
| API mapping | `*Assembler` or explicit static mapper methods |
| Persistence mapping | `*POConverter` |
| Repository port | `*Repository` in `domain` |
| Repository adapter | `*RepositoryImpl` in `infra/persistence` |
| Configuration | `*Config` or `*Configuration`, consistently within a package |
| Exception | Specific cause, not a catch-all name |
| Test | Class under test plus `Test`; integration intent visible in the name |

Avoid `Abstract*` on concrete Spring beans. Use `Request`, `Response`, `VO`, or a domain term instead of a generic `DTO` when the role is known.

## Error names

| Type | Meaning |
| --- | --- |
| `DomainException` | Stable domain failure with a domain error classification |
| `ApplicationException` | Application workflow failure that may be retried according to policy |
| `NonRetryableApplicationException` | Stable application rejection |
| Infrastructure exception | Translated at the adapter boundary or by a dedicated mapper |

Public errors expose a stable code and safe message, never an arbitrary internal exception message. Specific names include `UserNotFoundException`, `UserAlreadyExistsException`, and `AggregateVersionConflictException`.

## Persistence and collections

Java fields use camelCase and SQL columns use snake_case:

| Java | SQL |
| --- | --- |
| `tenantId` | `tenant_id` |
| `phoneNumber` | `phone_number` |
| `externalUser` | `is_external_user` |
| `passwordHash` | `password` (explicit legacy mapping) |
| `createdTime` | `created_time` |
| `updatedTime` | `updated_time` |
| `optionsJson` | `options_json` |

Persistence converters map every PO field in both directions, including `version` and audit timestamps. The optimistic-lock field is `version` in every layer. A JSON column is represented by a named `*Json` PO property and is decoded into validated domain types by the converter; domain objects do not expose raw JSON. Avoid Java property names that force SQL reserved words as generated aliases. Soft deletion uses `status=DELETED`; there is no `deleted_time` field.

Aggregate-owned collections may use separate child POs and tables while remaining one domain aggregate. For example, `CustomerAddressPO`, `CustomerTagPO`, `CustomerCollaboratorPO`, and `CustomerFieldValuePO` are hydrated in batches and reconstructed into one `Customer`; `CustomerPoolAdministratorPO`, `CustomerPoolEligibleMemberPO`, `CustomerPoolEligibleDepartmentPO`, and `CustomerPoolRulePO` reconstruct one `CustomerPool`. Public APIs never expose those persistence types, and child writes share the root command transaction and version check. Current pool membership and append-only claim history are persistence relations with named domain records, not mutable fields smuggled into the pool aggregate as JSON.

Repository single-result queries return `Optional<T>`, collections return an empty list rather than null, and domain pagination uses `PageResult<T>`. Convert it to public `Pager<T>` outside the domain boundary. Validate required input at the nearest trusted boundary and enforce domain invariants again in value objects.
