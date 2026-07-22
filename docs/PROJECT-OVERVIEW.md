# Project Overview

A short, honest explanation of what this project is, what has actually been run, and how to
talk about it without overclaiming.

---

## In one sentence

A job application tracker built as an event-driven microservices platform: three Spring Boot
services that never call each other, communicating entirely through Kafka events, deployed to
Kubernetes with autoscaling and self-healing and delivered by ArgoCD GitOps.

---

## The design decision that matters

Most job trackers are CRUD over a single database. This one is deliberately built the way a
distributed system is built.

`application-service` owns PostgreSQL and is the **only writer** to it. When an application is
created or its status changes, it writes to Postgres and publishes an event. Two downstream
services react independently:

- `notification-service` schedules follow-up reminders, and cancels them when an application
  closes
- `analytics-service` maintains a conversion-funnel read-model in Redis

Neither consumer ever touches the other's database. Their state is derived, so it can be
rebuilt at any time by replaying Kafka. That is database-per-service with events as the
integration mechanism — an architectural stance, not a buzzword.

**The trade-off, stated plainly:** this is more machinery than a tracker needs. The point was
to build and operate the distributed-systems patterns end to end, not to ship the simplest
thing that stores job applications.

---

## Stack

| Layer | Technology |
|---|---|
| Services | Java 21, Spring Boot 3.3 |
| Messaging | Apache Kafka (KRaft mode, no ZooKeeper) |
| Storage | PostgreSQL (system of record), Redis (read-model) |
| Frontend | React 18, nginx reverse proxy |
| Containers | Docker, Docker Compose |
| Orchestration | Kubernetes, Kustomize (`local` / `prod` overlays), HPA |
| GitOps | ArgoCD |
| IaC | Terraform (EKS, VPC, IRSA, managed node groups) |
| CI/CD | GitHub Actions → GHCR |
| Observability | OpenTelemetry, Prometheus, Grafana, Loki, Tempo |

---

## What has actually been run

The distinction between "I wrote Kubernetes manifests" and "I deployed to Kubernetes" is the
whole point of this section.

| Component | Status |
|---|---|
| 3 microservices + React UI, Kafka, PostgreSQL, Redis | ✅ Running |
| Prometheus / Grafana / Loki / Tempo + OpenTelemetry tracing (Compose) | ✅ Running — dashboards and traces verified |
| GitHub Actions CI/CD → images published to GHCR | ✅ Running |
| Kubernetes (Kind) — full stack deployed, probes green | ✅ Deployed |
| In-cluster Prometheus + Grafana + OTel Collector | ✅ Deployed — metrics scraped via pod annotations |
| Self-healing under pod failure | ✅ Verified — replacement scheduled in 32s, Ready in ~3 min |
| HPA scale-out under load | ⚠️ Partial — 1→2 replicas verified; a sustained climb is not reachable on a 4-CPU laptop |
| ArgoCD GitOps | ✅ Running — 23 Synced / 0 OutOfSync; a Git push changed the cluster with no `kubectl` |
| AWS EKS via Terraform | ⬜ `init` + `validate` pass; **not provisioned** |
| Distributed tracing in Kubernetes | ⬜ Compose only — Tempo does not fit in available memory |

This is a personal project deployed to real infrastructure. It is **not** production, and it
has never served real users or traffic.

---

## The strongest artifact

**[KIND-DEPLOYMENT-LOG.md](KIND-DEPLOYMENT-LOG.md)** — a record of the eleven bugs found while
actually deploying this, each with the failing output, the diagnosis, and the fix. **Nine of
the eleven cannot occur under Docker Compose.** That document is the evidence the work
happened, and it is more convincing than any screenshot.

Three worth being able to explain out loud:

**Kubernetes corrupted the Kafka broker's own config.** Kubernetes injects an environment
variable for every Service in a namespace, including `KAFKA_PORT=tcp://10.96.x.x:9092`. The
Confluent image treats every `KAFKA_*` variable as broker configuration and writes it into
`server.properties`, so the broker exited 1 during startup. Fixed with
`enableServiceLinks: false`. Service-link env vars are legacy behaviour, on by default, and
hostile to any image using a `<COMPONENT>_*` naming convention.

**The autoscaler scaled out at idle.** HPA utilisation is a percentage of the CPU *request*,
not the limit. Requests had been trimmed to 75m to fit the stack on a laptop, but an idle
Spring Boot JVM uses 110–130m — so every service sat at 144–265% of a 75% target with zero
traffic, and the autoscaler climbed to `maxReplicas` and exhausted the node. Undersized
requests do not just mislead the scheduler; they break the HPA, because the request is the
denominator.

**ArgoCD and the HPA fought over the same field.** Git specified a replica count, the HPA set
a different one, `selfHeal` reverted it, repeat forever — a permanent `OutOfSync`. Neither
controller was malfunctioning; they had been given contradictory authority over
`/spec/replicas`. Fixed with `ignoreDifferences`. GitOps forces you to name the owner of
every field that anything mutates at runtime.

---

## Answering the obvious questions

**"Did you actually deploy this, or just write the YAML?"**
Deployed, to a local Kubernetes cluster (Kind), with screenshots and a written log of every
failure. Not to production, and not to AWS — the Terraform is written and validates, but was
never applied, because EKS costs roughly $230/month to leave running.

**"How do you know the autoscaling works?"**
metrics-server is installed and the HPA reads live CPU. Under load, `application-service`
scaled 1→2 with the event `SuccessfulRescale: New size: 2; reason: cpu resource utilization
(percentage of request) above target`. A sustained climb to max replicas was not achievable:
on 4 CPUs the load generator starves the Kubernetes control plane — etcd saturates, the API
server starts returning `TLS handshake timeout`, and pods get evicted. The test destroys what
it is measuring. That is documented rather than papered over.

**"What does GitOps actually mean here?"**
ArgoCD watches this repository and reconciles the cluster to match it. A commit pushed at
02:51 changed the running cluster at 02:54:30 with no `kubectl` involved — the ~3m30s gap is
ArgoCD's default polling interval. GitOps is eventually consistent by design; a demo that
hides that interval misrepresents the tool.

**"Why is the observability stack different in Kubernetes?"**
Tempo, Loki and Promtail run under Compose but not in the cluster: the full LGTM stack does
not fit in the ~4.3 GB this machine can give Docker. Traces in Kubernetes therefore go to the
collector's debug exporter, not a queryable backend. Only the Prometheus datasource is
provisioned, because pointing a Grafana datasource at a Service that does not exist produces
broken panels rather than useful ones.

**"What was the hardest part?"**
Not writing the manifests — diagnosing failures that only appear on Kubernetes. Every Java
service crashlooped with empty logs and exit code 143, which is SIGTERM: the kubelet was
killing them, not the applications crashing. A fixed `initialDelaySeconds: 40` assumed a JVM
boots in 40 seconds, and on a contended node it does not, so they were killed before they
could report why they were unhealthy. A `startupProbe` expresses "this may take a while"
without weakening liveness afterwards, and is the single most transferable fix in the log.

---

## How to describe this honestly

Accurate:

> Deployed to Kubernetes (Kind) with liveness, readiness and startup probes; verified
> self-healing under pod failure and HPA scale-out under load (1→2 replicas), with Prometheus
> and Grafana running in-cluster and metrics scraped via pod annotations. Delivered via ArgoCD
> GitOps, with a change pushed to Git reconciled into the cluster automatically. Terraform for
> EKS is written and validated, not provisioned.

**Do not say:** "production experience", "handles X requests/sec", or "deployed to
production". None of those are true here, and experienced reviewers detect overclaiming in a
single follow-up question.

Being explicit about what was and was not run is a credibility asset rather than a weakness.
Very few portfolio projects do it, and it pre-empts the exact challenge that sinks the ones
that do not.

---

## Where to look in the repo

| | |
|---|---|
| Architecture, screenshots, status table | [`README.md`](../README.md) |
| Every bug found while deploying, with evidence | [`docs/KIND-DEPLOYMENT-LOG.md`](KIND-DEPLOYMENT-LOG.md) |
| Reproduce the deployment, verified commands | [`docs/DEPLOY-TESTING.md`](DEPLOY-TESTING.md) |
| Running it locally, Compose through EKS | [`docs/RUNBOOK.md`](RUNBOOK.md) |
| Kubernetes manifests | [`infrastructure/kubernetes/`](../infrastructure/kubernetes/) |
| GitOps setup | [`infrastructure/argocd/`](../infrastructure/argocd/) |
| Cloud IaC | [`infrastructure/terraform/`](../infrastructure/terraform/) |
