# Wallet Service

Spring Boot wallet backend that demonstrates authenticated money transfers with PostgreSQL transactions, Redis idempotency, Kafka-based notifications, the Outbox Pattern, and deterministic plus AI-assisted fraud/risk assessment.

The project is intentionally shaped for backend interviews: the main request flows are easy to trace, but the system still contains real distributed-systems concepts.

## Architecture

```text
Client
  ↓
Controller
  ↓
Service
  ↓
Repository
  ↓
PostgreSQL Transaction
  ↓
Outbox Event
  ↓
Scheduled Outbox Publisher
  ↓
Kafka Topic: money-transfers
  ↓
Kafka Consumer
  ↓
Email Notification

Risk Assessment Flow:

Client
  ↓
RiskAssessmentController
  ↓
RiskAssessmentService
  ↓
PostgreSQL Account + Transaction History
  ↓
Optional AiRiskAssessmentClient
  ↓
External AI Fraud Detection Service
```

## Package Structure

| Package | What it does | Why it exists | Entry point |
| --- | --- | --- | --- |
| `controller` | Defines REST endpoints. | Keeps HTTP concerns separate from business logic. | `AuthController`, `AccountController`, `RiskAssessmentController` |
| `service` | Holds business workflows. | Keeps transaction, auth, risk, Kafka, and notification logic out of controllers. | `AccountService`, `AuthService`, `RiskAssessmentService`, `OutboxPublisherService` |
| `repository` | Database access through Spring Data JPA. | Keeps persistence queries behind focused interfaces. | `AccountRepository`, `WalletTransactionRepository`, `OutboxEventRepository` |
| `entity` | JPA database models. | Represents persistent domain state. | `User`, `Account`, `WalletTransaction`, `OutboxEvent` |
| `dto` | API and message payload objects. | Prevents controllers from exposing database entities where response/request shapes are different. | `CreateTransferRequest`, `TransactionResponse`, `RiskAssessmentResponse`, `TransferEvent` |
| `security` | JWT request authentication and Spring Security rules. | Protects wallet endpoints while allowing register/login. | `SecurityConfig`, `JwtFilter` |
| `config` | Framework beans that are still useful. | Holds explicit framework setup only when auto-configuration is not enough. | `RestClientConfig` |

## Request Flow

Most requests follow this shape:

```text
HTTP request
  ↓
Controller validates the route and extracts request data
  ↓
Service performs business rules
  ↓
Repository reads/writes PostgreSQL
  ↓
Controller returns response
```

The transfer flow adds Redis, transactions, outbox, and Kafka:

```text
POST /accounts/transfer
  ↓
JwtFilter authenticates the caller
  ↓
AccountController.transferMoney()
  ↓
AccountService.transferMoney()
  ↓
Redis idempotency lock
  ↓
PostgreSQL transaction locks account rows
  ↓
Balances and ledger are saved
  ↓
OutboxEvent is saved in the same DB transaction
  ↓
Scheduled OutboxPublisherService publishes to Kafka
  ↓
KafkaConsumerService receives the event
  ↓
EmailNotificationService sends transfer emails when enabled
```

## Authentication Flow

`POST /auth/register` stores a user after hashing the password with BCrypt.

`POST /auth/login` verifies the password and returns a JWT containing the user's email as the token subject.

For protected endpoints, `JwtFilter` reads the `Authorization: Bearer <token>` header, validates the token through `JwtService`, and stores the authenticated email in Spring Security's `SecurityContextHolder`.

## Transaction Flow

`AccountService.transferMoney()` is annotated with `@Transactional`, so account updates, transaction history, and outbox event creation commit or roll back together.

The service locks both accounts with a pessimistic database lock before moving money. This protects balances from race conditions when two transfers touch the same accounts at the same time.

The wallet transaction row stores the idempotency key, amount, source account, destination account, status, and timestamp.

## Outbox Flow

The service does not publish to Kafka directly during the money transfer. Instead, it saves an `OutboxEvent` row with status `PENDING`.

This solves the classic problem where the database commit succeeds but Kafka publishing fails. If the transfer commits, the outbox row also commits, so the system can retry publishing later.

`OutboxPublisherService` runs on a schedule, reads pending outbox rows, sends them to Kafka, and marks them as `SENT`. Failed publish attempts remain pending and are retried after `nextAttemptAt`.

## Kafka Flow

The Kafka topic is `money-transfers`.

`OutboxPublisherService` uses Spring Kafka's `KafkaTemplate<String, String>` to publish the serialized transfer event.

`KafkaConsumerService` listens to the same topic and passes the event to `EmailNotificationService`. Email delivery is asynchronous from the original transfer request, so a slow or unavailable mail server does not block the money movement.

## AI Fraud Detection Flow

`POST /risk/assess-transfer` runs deterministic risk checks inside this service.

`POST /risk/assess-transfer-ai` first runs the deterministic assessment, then sends that result to an external AI risk service through `AiRiskAssessmentClient`.

AI is intentionally outside the core transfer transaction. This keeps money movement reliable and lets the risk explanation service evolve independently.

## Failure Scenarios

| Scenario | Handling |
| --- | --- |
| Duplicate transfer request | Redis `setIfAbsent` rejects an already-used idempotency key. |
| Invalid amount, self-transfer, or insufficient balance | The service throws an error and the transaction rolls back. |
| Database write fails | The transfer, ledger entry, and outbox event all roll back together. |
| Kafka is unavailable | The outbox event stays `PENDING` and is retried later. |
| App crashes after transfer commit but before Kafka publish | The committed outbox row remains in PostgreSQL and is published when the app runs again. |
| Kafka publishes but app crashes before marking `SENT` | The event may be published again; consumers should be idempotent for production-grade systems. |
| Email is disabled | The consumer logs the skipped notification and keeps the transfer flow unaffected. |
| AI risk service is unavailable | Only the AI endpoint fails; core transfer functionality remains independent. |

## Design Decisions

| Decision | Reason |
| --- | --- |
| JWT instead of server sessions | Keeps authentication stateless and easy to scale. |
| PostgreSQL for wallet state | Relational transactions and row locks are a strong fit for money movement. |
| Redis for idempotency | Fast atomic `setIfAbsent` prevents duplicate transfer processing from retries. |
| Outbox Pattern | Makes database changes and message publication reliable without a distributed transaction. |
| Kafka for transfer events | Decouples transfer completion from notification processing and future event consumers. |
| Scheduled publisher | Simple to understand and enough for an interview-ready outbox implementation. |
| AI service kept outside transfer transaction | Avoids making money movement depend on a slow or unavailable external service. |

## Running Tests

```bash
./mvnw test
```
