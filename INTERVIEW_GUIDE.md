# Interview Guide

This guide explains the engineering choices in this wallet service using the actual project structure.

## Frequently Asked Questions

### What does this project do?

It is a Spring Boot wallet backend where users can register, log in, create accounts, transfer money, view balance/history, publish transfer events through Kafka, and assess fraud risk.

The project is not just CRUD. The transfer path demonstrates authentication, Redis idempotency, database transactions, pessimistic locking, the Outbox Pattern, and Kafka event publishing.

### Why use JWT?

JWT keeps authentication stateless. After login, the client sends the token on each request and the backend validates it without storing a server-side session.

In this project, `JwtFilter` validates the token and stores the email in `SecurityContextHolder`. Services then use that authenticated email to load the current user.

### Why use Spring Security?

Spring Security centralizes route protection. Public routes are limited to static files and `/auth/login` plus `/auth/register`.

Every wallet and risk endpoint requires a valid JWT. This makes the security boundary easy to explain in interviews.

### Why PostgreSQL?

Wallet balances need transactional consistency. PostgreSQL gives ACID transactions, unique constraints, and row-level locking.

The transfer service locks account rows before updating balances. That prevents two concurrent transfers from corrupting the same balance.

### Why Redis?

Redis is used for request idempotency. The client sends an `Idempotency-Key`, and the service uses Redis `setIfAbsent` to accept the first request and reject duplicates.

This is useful when clients retry after timeouts. Without it, the same transfer could accidentally run twice.

Trade-off: the current Redis key has a 10-minute TTL, so it protects retry windows rather than serving as a permanent deduplication table.

### Why Kafka instead of synchronous REST?

The transfer should complete based on wallet correctness, not the availability of downstream systems. Kafka lets the service publish transfer events asynchronously.

Kafka also allows future consumers to be added without changing the transfer API. For example, analytics or audit services can subscribe later.

Trade-off: Kafka introduces eventual consistency. The transfer can be complete before notification processing finishes.

### Why the Outbox Pattern?

Publishing directly to Kafka inside a database transaction is unsafe because the database and Kafka do not share the same transaction.

The Outbox Pattern saves an event row in PostgreSQL in the same transaction as the balance update. A scheduled publisher later sends pending rows to Kafka.

This guarantees that if the transfer commits, there is a durable event to publish. It avoids distributed transactions while keeping the system reliable.

### Does the Outbox Pattern guarantee exactly-once delivery?

No. This implementation gives at-least-once delivery.

If Kafka receives the event but the app crashes before marking the row as `SENT`, the event can be sent again. Production consumers should deduplicate using an event id or business key.

### What happens if Kafka is down?

The transfer still commits because Kafka publishing is not part of the request transaction.

The outbox row remains `PENDING`. `OutboxPublisherService` retries after `nextAttemptAt`.

### What happens if the application crashes after the transfer?

If the database transaction committed, the outbox row is stored durably.

When the application starts again, the scheduled outbox publisher can find the pending row and publish it to Kafka.

### What is the main transfer transaction boundary?

`AccountService.transferMoney()` is the boundary.

Inside that transaction, the service validates the transfer, locks accounts, updates balances, saves a `WalletTransaction`, and saves an `OutboxEvent`.

### How does the project handle concurrent transfers?

`AccountRepository.findAllByIdForUpdate()` uses a pessimistic write lock.

The service locks both account rows before changing balances. This prevents simultaneous updates from reading stale balances.

### Why remove the custom `KafkaProducerService`?

It only forwarded calls to `KafkaTemplate`, so it added another jump without adding business meaning.

The cleaned version lets `OutboxPublisherService` publish directly through `KafkaTemplate`. Functionality is unchanged; the request flow is easier to explain.

### Why remove `UserController`?

It duplicated user creation outside the real registration flow and bypassed password hashing.

The proper entry point for users is `AuthController.register()`, which hashes passwords before saving.

### Why remove custom `KafkaConfig`?

Spring Boot already auto-configures `KafkaTemplate` from `spring.kafka.*` properties.

Removing the manual producer factory reduces boilerplate and lets Boot apply all configured Kafka properties, including security settings.

## Trade-offs

| Choice | Benefit | Trade-off |
| --- | --- | --- |
| Redis idempotency | Fast duplicate protection | TTL-based, not permanent |
| Pessimistic locking | Simple balance correctness | Can reduce throughput under heavy contention |
| Outbox Pattern | Reliable event publication | Requires background publisher and retry state |
| Kafka | Decoupled async processing | Eventual consistency and duplicate handling |
| Separate risk endpoint | Explainable deterministic scoring | Risk checks are not enforced before transfer |
| Scheduled outbox publisher | Easy to understand | Polling is less immediate than CDC/Debezium |

## Scalability Discussion

The service can scale horizontally for read endpoints and authentication because JWT is stateless.

Transfers require careful concurrency control because account rows are locked while balances change. This is correct for money movement, but hot accounts may become bottlenecks.

The outbox publisher can be scaled with stronger row-claiming logic, such as `SKIP LOCKED`, to avoid multiple app instances publishing the same row at the same time.

Kafka lets analytics, auditing, and other downstream systems consume transfer events independently.

## Interview Summary

The best way to explain the project:

```text
It is a wallet service where the core transfer is strongly consistent in PostgreSQL, while side effects are asynchronous through Kafka. Redis prevents duplicate transfer attempts, and the Outbox Pattern guarantees that committed transfers produce durable events without needing a distributed transaction.
```
