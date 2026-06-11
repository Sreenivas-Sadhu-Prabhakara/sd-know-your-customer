# Know Your Customer

BIAN Service Domain microservice — **Phase 2b-c DEEP build** (graduated; see `.bian-graduated`). One of the three **flagship** counterparties (KYC).

| | |
|---|---|
| **Business Area / Domain** | Risk and Compliance / Financial Crime |
| **Pattern / Control Record** | Process / KYC Assessment Procedure |
| **K8s Namespace** | `bian-risk-compliance` |

## The screening pipeline (precedence order)

| Check | Outcome | Why |
|---|---|---|
| Watchlist hit (sanctions/PEP) | **REJECTED** — terminal, not API-overridable | hard fail |
| Missing required documents (`ID`, `ADDRESS` by default) | **REFERRED** | incompleteness is a follow-up, not a rejection |
| High-risk jurisdiction (`KP`, `IR` by default) | **REFERRED** | enhanced due diligence is a human decision |
| Otherwise | **APPROVED** | |

REFERRED cases are decided by an analyst via `PUT /{id}/control` — **reason mandatory** (audit requirement), recorded as `ANALYST:<reason>`.

## Flagship wiring (closing the KYC loop)

- Consumes `kyc.check.requested` (account openings) — **via `POST /initiate` over HTTP today**, Kafka consumer later.
- Publishes `kyc.assessment.completed` on `bian.kyc.assessment`.
- **Callback bridge:** when the check request carries a `callbackUrl`, the verdict is delivered as `PUT {approved, reason}` — exactly the account SDs' `kyc-result` shape. Callback failure never loses the assessment (recorded as `kyc.callback.failed`, re-deliverable).
- **To go live end-to-end:** point account SDs at this service with their callback URL and flip their `bian.kyc.auto-approve=false`.

```bash
mvn spring-boot:run
CR=/v1/kyc-assessment-procedure
curl -s -X POST localhost:8080$CR/initiate -H 'content-type: application/json' \
  -d '{"customerReference":"C-1","countryCode":"IN","documents":["ID","ADDRESS"]}'
# → {"status":"APPROVED","reasons":["CLEAN"],…}
```

Contracts owned by this repo: [`api/openapi.yaml`](api/openapi.yaml) · [`api/events.yaml`](api/events.yaml). Watchlist maintenance lives here pragmatically for the flagship; BIAN purists would carve it into its own SD later.

## Persistence & tests

In-memory port/adapter; watchlist seeds from `bian.kyc.watchlist` config. Postgres staged in [`db/schema.sql`](db/schema.sql) — gated. `mvn verify` proves each screening rule, precedence, the analyst flow (incl. the audit-reason requirement), and callback delivery/failure semantics.
