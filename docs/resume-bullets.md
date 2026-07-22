# Résumé Material — Job Application Tracker

Copy-paste blocks for a résumé, LinkedIn, or portfolio. Keep only what's true for
your build; edit the italic placeholders. Avoid inventing production traffic
numbers — for a personal project, describe capability and design, not fake scale.

---

## One-line project title

**Cloud-Native Job Application Tracker** — Event-driven Java microservices platform
(Spring Boot, Kafka, PostgreSQL, Redis) with full observability and GitOps delivery
on Kubernetes/AWS EKS.

---

## Résumé bullets (concise, impact-first)

- Designed and built an **event-driven microservices platform** in **Java 21 / Spring Boot 3.3** — three services communicating through **Apache Kafka** using the database-per-service pattern, with `application-service` as the single writer to **PostgreSQL** (Flyway-managed schema).
- Implemented an **analytics read-model** in a dedicated service that consumes Kafka events into **Redis** aggregates and serves a read-only reporting API (pipeline funnel, conversion rates, breakdowns by company/status/résumé version).
- Instrumented every service with **OpenTelemetry + Micrometer**, shipping metrics, logs, and distributed traces to a **Grafana LGTM stack** (Prometheus, Loki, Tempo) via an OpenTelemetry Collector, with a provisioned Grafana dashboard of custom business + JVM metrics.
- Containerized all services with **multi-stage Docker builds** and authored **Kubernetes manifests** (Kustomize base + local/prod overlays) including liveness/readiness probes and **HPA autoscaling** on CPU.
- Automated delivery end-to-end: **GitHub Actions** CI (build/test/dockerize a 3-service matrix) → image publish to GHCR → **ArgoCD** GitOps sync to **AWS EKS** provisioned with **Terraform** (VPC, EKS, IRSA, managed node groups).
- Validated resilience with **chaos and load scripts** demonstrating Kubernetes self-healing (pod kill → automatic reschedule) and HPA scale-out under sustained write load.

---

## Shorter variant (2 bullets)

- Built a **Java 21 / Spring Boot** event-driven microservices platform (Kafka, PostgreSQL, Redis) with an analytics read-model, full **Grafana LGTM** observability (metrics/logs/traces via OpenTelemetry), and Prometheus dashboards.
- Shipped it with **Docker + Kubernetes** (Kustomize, HPA), **Terraform**-provisioned **AWS EKS**, and a **GitHub Actions → ArgoCD** GitOps pipeline; proved resilience with chaos + load tests.

---

## Skills surfaced (for a skills section)

`Java 21` · `Spring Boot` · `Apache Kafka` · `PostgreSQL` · `Redis` · `Event-driven architecture`
`Docker` · `Kubernetes` · `Kustomize` · `Helm-ready` · `Terraform` · `AWS EKS` · `GitHub Actions` · `ArgoCD (GitOps)`
`OpenTelemetry` · `Prometheus` · `Grafana` · `Loki` · `Tempo` · `Micrometer`

---

## Talking points for interviews

- **Why database-per-service?** `application-service` owns PostgreSQL and is the only writer; other services never touch that DB — they react to Kafka events. This keeps ownership clear and lets consumers evolve independently.
- **How is the analytics model kept correct?** Redis holds current-state counters (incremented/decremented) plus monotonic historical funnel counters. Because it's derived purely from Kafka, it can be rebuilt by replaying topics (consumer group resets to earliest).
- **How do you see a request across services?** OpenTelemetry propagates trace context; the Spring log pattern embeds `traceId`/`spanId`, and Grafana links a log line straight to its Tempo trace.
- **What happens on failure?** Kafka consumers use a `DefaultErrorHandler` with backoff; Kubernetes probes + HPA handle pod health and scaling; the chaos script demonstrates self-healing.
