# Job Application Tracker — Cloud-Native Observability Platform

An event-driven microservices platform that tracks job applications end to end —
built to demonstrate production-grade backend engineering, event-driven
architecture, Kubernetes deployment, and full-stack observability (metrics, logs,
traces).

> **Status:** all three services (`application-service`, `notification-service`,
> `analytics-service`) are complete and functional. Observability (LGTM),
> Kubernetes manifests, Terraform (EKS), and the CI/CD + GitOps pipeline are in place.

## Architecture

```
                     GitHub Actions CI/CD  →  ArgoCD (GitOps)  →  AWS EKS
                                                                   │
                              ┌────────────────────────────────────┤
                              │                                    │
                    application-service                            │
                    (owns PostgreSQL, only writer)                 │
                              │ publishes                          │
                    ┌─────────┴──────────┐                         │
                    ▼                    ▼                         │
              Kafka topics:                                        │
              - application-created                                │
              - application-status-updated                         │
              - application-deleted                                │
                    │                    │                         │
                    ▼                    ▼                         │
        notification-service     analytics-service                 │
        (in-memory scheduler)     (Redis: aggregates)               │
                                                                     │
              Observability: OpenTelemetry → Prometheus / Loki / Tempo → Grafana
```

A rendered diagram lives in [`docs/architecture.mermaid`](docs/architecture.mermaid).

**Design principle:** `application-service` is the single source of truth and the
only writer to PostgreSQL (database-per-service pattern). Other services never
write application data directly — they react to Kafka events.

## Tech Stack

- Java 21, Spring Boot 3.3
- PostgreSQL (via Flyway migrations), Redis
- Apache Kafka
- Docker, Kubernetes (Kind for local, EKS for cloud), Kustomize
- Terraform, GitHub Actions, ArgoCD
- OpenTelemetry, Prometheus, Grafana, Loki, Tempo (LGTM stack)

## Services

| Service | Responsibility | Data store | Port |
|---|---|---|---|
| `application-service` | CRUD for applications, publishes Kafka events | PostgreSQL | 8081 |
| `notification-service` | Consumes events, schedules follow-up/interview/offer reminders | In-memory scheduler | 8082 |
| `analytics-service` | Consumes events, maintains aggregates, serves read-only reporting API | Redis | 8083 |

## Local Development

```bash
docker-compose up --build
```

Starts PostgreSQL 18, Kafka, Redis, and all three services (8081/8082/8083).

PostgreSQL defaults:
- database: `jobtracker`, user: `jobtrackeru`, password: `jobtracker1`
- host port: `5434` (Docker only; your laptop's local PostgreSQL on `5432` stays untouched)

Kafka: host `localhost:9092`, container-to-container `kafka:29092`. Redis: `localhost:6379`.

### Try it (or run `./scripts/demo.sh`)

```bash
curl -X POST localhost:8081/api/v1/applications \
  -H "Content-Type: application/json" \
  -d '{"company":"Atidan Technologies","role":"SDE","appliedDate":"2026-07-12","resumeVersion":"java-aiml"}'

curl localhost:8081/api/v1/applications
curl localhost:8082/api/v1/reminders/summary
curl localhost:8083/api/v1/analytics/summary
```

## API Reference

### application-service

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/applications` | Create a new application |
| GET | `/api/v1/applications` | List all applications (optional `?status=` filter) |
| GET | `/api/v1/applications/{id}` | Get a single application |
| PATCH | `/api/v1/applications/{id}/status` | Update status (`{"status":"INTERVIEW_SCHEDULED"}`) |
| DELETE | `/api/v1/applications/{id}` | Delete an application |

### analytics-service (read-only)

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/v1/analytics/summary` | Total, breakdown by status, funnel + conversion rates |
| GET | `/api/v1/analytics/by-status` | Live count of applications per status |
| GET | `/api/v1/analytics/by-company` | Applications per company |
| GET | `/api/v1/analytics/by-resume` | Applications per résumé version |
| GET | `/api/v1/analytics/funnel` | Historical funnel (created → interview → offer) |

All services expose `/actuator/health/{liveness,readiness}` and `/actuator/prometheus`.

## Custom Metrics

- `job_applications_total` (created counter — Prometheus strips the reserved `_created` suffix), `job_status_updates_total`
- `application_creation_latency` (histogram, p50/p95/p99)
- `kafka_events_published_total`, `kafka_events_publish_failed_total`
- `notification_events_consumed_total`, `reminders_scheduled_total`, `reminders_triggered_total`
- `analytics_events_consumed_total`, `analytics_aggregation_errors_total`

## Observability (LGTM)

Run the app stack together with the observability overlay:

```bash
docker-compose -f docker-compose.yml -f docker-compose.observability.yml up --build
```

- **Grafana** — http://localhost:3000 (admin / admin) — pre-provisioned datasources
  (Prometheus, Loki, Tempo) and a **JobTracker — Platform Overview** dashboard.
- **Prometheus** — http://localhost:9090 · **Tempo** — http://localhost:3200

Traces are emitted via OpenTelemetry to the Collector → Tempo; logs are shipped by
Promtail → Loki; a Grafana derived field links a log line's `traceId` to its trace.

## Kubernetes

Manifests use Kustomize (`infrastructure/kubernetes/base` + `overlays/{local,prod}`).

```bash
# Local Kind cluster
kind create cluster --config infrastructure/kubernetes/overlays/local/kind-cluster.yaml
for s in application-service notification-service analytics-service; do
  docker build -t $s:latest ./$s && kind load docker-image $s:latest --name jobtracker
done
kubectl apply -k infrastructure/kubernetes/overlays/local

kubectl -n jobtracker get pods
kubectl -n jobtracker get hpa
```

Chaos & load demos:

```bash
./scripts/chaos.sh        # kill a pod, watch Kubernetes reschedule it
./scripts/load-test.sh    # drive write load, watch the HPA scale out
```

## Cloud (AWS EKS via Terraform + ArgoCD)

See [`infrastructure/terraform/README.md`](infrastructure/terraform/README.md) for
apply steps and a **cost warning** (EKS is billable — always `terraform destroy`
when done), and [`infrastructure/argocd/README.md`](infrastructure/argocd/README.md)
for the GitOps setup.

```bash
cd infrastructure/terraform && terraform init && terraform apply
aws eks update-kubeconfig --region us-east-1 --name jobtracker
kubectl apply -f infrastructure/argocd/application.yaml   # ArgoCD then syncs the app
```

## CI/CD

- **CI** (`.github/workflows/ci.yml`) — build + test + Docker build for all three services on every push/PR.
- **CD** (`.github/workflows/cd.yml`) — publish images to GHCR, bump image tags in the prod overlay; ArgoCD reconciles the cluster.

## Roadmap

- [x] `application-service` — CRUD + PostgreSQL + Kafka producer
- [x] `notification-service` — Kafka consumer + reminder logic
- [x] `analytics-service` — Kafka consumer + Redis aggregates + reporting API
- [x] OpenTelemetry + Prometheus + Loki + Tempo + Grafana dashboards
- [x] Kubernetes manifests (Kustomize, Kind local + prod overlay)
- [x] Terraform (EKS, VPC, IAM/IRSA)
- [x] GitHub Actions CI/CD
- [x] ArgoCD GitOps deployment
- [x] HPA auto-scaling
- [x] Chaos + load test scripts (pod kill + recovery, HPA scale-out)
- [ ] Canary deployment (Argo Rollouts) — stretch
- [ ] Architecture screenshots + demo video

## Author

Vishal Kesarwani — [vishalkesarwani.in](https://vishalkesarwani.in) · [GitHub](https://github.com/vishal-kesharwani) · [LinkedIn](https://linkedin.com/in/vishal-kesharwani02)
