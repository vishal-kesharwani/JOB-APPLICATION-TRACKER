# RUNBOOK — Running the Job Application Tracker

Four ways to run it, from easiest to most involved. Start with Level 1.

- **Level 1 — Local (Docker Compose):** the whole app on your laptop. Start here.
- **Level 2 — Local + Observability:** add Grafana/Prometheus/Tempo/Loki.
- **Level 3 — Local Kubernetes (Kind):** run it in a real cluster on your laptop.
- **Level 4 — Cloud (AWS EKS):** real cloud deploy. Billable — read the cost note.

---

## Prerequisites

| Level | Needs |
|---|---|
| 1 & 2 | [Docker Desktop](https://www.docker.com/products/docker-desktop/) (with Docker Compose) |
| 3 | Docker + [kind](https://kind.sigs.k8s.io/) + [kubectl](https://kubernetes.io/docs/tasks/tools/) + [kustomize](https://kubectl.docs.kubernetes.io/installation/kustomize/) |
| 4 | Everything above + [AWS CLI v2](https://aws.amazon.com/cli/) + [Terraform](https://developer.hashicorp.com/terraform/install) + an AWS account |

You do **not** need Java or Maven installed — the Docker builds compile everything
inside the container.

Verify Docker is running:

```bash
docker --version
docker compose version   # or: docker-compose --version
```

> **Note on the command:** newer Docker uses `docker compose` (space); older uses
> `docker-compose` (hyphen). Both work — use whichever your version has.

---

## Level 1 — Local (Docker Compose)

From the project root (`APPLICATION TRACKER/`):

```bash
docker compose up --build
```

First run takes a few minutes (it downloads images and compiles the three Java
services). It's ready when you see the three services log `Started ...Application`.

Leave that terminal running. Open a **second terminal** and smoke-test:

```bash
# health
curl localhost:8081/actuator/health
curl localhost:8082/actuator/health
curl localhost:8083/actuator/health

# create an application
curl -X POST localhost:8081/api/v1/applications \
  -H "Content-Type: application/json" \
  -d '{"company":"Atidan Technologies","role":"SDE","appliedDate":"2026-07-12","resumeVersion":"java-aiml"}'

# see it, and see the downstream reactions
curl localhost:8081/api/v1/applications
curl localhost:8082/api/v1/reminders/summary
curl localhost:8083/api/v1/analytics/summary
```

Or run the guided demo (creates data, moves it through interview→offer, prints the
notification + analytics results):

```bash
./scripts/demo.sh
```

**Open the UI at http://localhost:8080** — create applications, change their status,
and watch reminders and analytics update live. That's the easiest way to demo the
whole event-driven pipeline without touching curl.

**Ports:** UI 8080 · application 8081 · notification 8082 · analytics 8083 · Postgres 5434 · Redis 6379.

> The frontend also has a hot-reload dev server if you want to change the UI:
> `cd frontend && npm install && npm run dev` → http://localhost:5173 (it proxies
> API calls to the running backend containers).

**Stop:** `Ctrl-C` in the first terminal, then `docker compose down`.
To also wipe the database/Redis volumes: `docker compose down -v`.

---

## Level 2 — Local + Observability (Grafana LGTM)

Same as Level 1 but add the observability overlay so metrics, logs, and traces flow:

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml up --build
```

Generate some traffic (`./scripts/demo.sh`), then open:

| Tool | URL | Login |
|---|---|---|
| **Grafana** | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | — |
| Tempo (via Grafana Explore) | http://localhost:3200 | — |

In Grafana: **Dashboards → JobTracker → Platform Overview** for metrics.
For a distributed trace: **Explore → Tempo → Search**, pick a trace, and watch one
request span all three services. Logs are in **Explore → Loki** (`{service="application-service"}`).

Stop the same way: `Ctrl-C`, then
`docker compose -f docker-compose.yml -f docker-compose.observability.yml down`.

---

## Level 3 — Local Kubernetes (Kind)

Run the platform in a real Kubernetes cluster on your laptop.

```bash
# 1. Create the cluster (single node — see DEPLOY-TESTING.md for why)
kind create cluster --config infrastructure/kubernetes/overlays/local/kind-cluster.yaml

# 2. Build the four images and load them into the Kind node (~30 min first run)
for s in application-service notification-service analytics-service frontend; do
  docker build -t $s:latest ./$s
  kind load docker-image $s:latest --name jobtracker
done

# 3. Side-load Kafka. `kind load docker-image` fails here with
#    "content digest ...: not found" — Docker's containerd store holds a
#    multi-platform manifest whose non-amd64 blobs were never pulled.
docker pull --platform linux/amd64 confluentinc/cp-kafka:7.6.1
docker save --platform linux/amd64 -o cp-kafka.tar confluentinc/cp-kafka:7.6.1
kind load image-archive cp-kafka.tar --name jobtracker

# 4. Deploy everything (Postgres, Kafka, Redis, the 3 services, the UI)
kubectl apply -k infrastructure/kubernetes/overlays/local

# 5. Watch it come up. Postgres/Kafka ~1-2 min; the JVMs take 2-5 min and are
#    allowed up to 5 by their startup probes.
kubectl -n jobtracker get pods -w
```

Confirm you are on the right cluster if anything looks missing — Docker Desktop's built-in
Kubernetes will quietly take the current context if it is enabled:

```bash
kubectl config current-context      # expect kind-jobtracker
```

Once pods are `Running` and `Ready`, reach a service with port-forward:

```bash
kubectl -n jobtracker port-forward svc/application-service 8081:8081
# in another terminal:
curl localhost:8081/api/v1/applications
```

Autoscaling requires the metrics-server (Kind doesn't ship it):

```bash
# side-load the image first (same reason as Kafka above)
docker pull --platform linux/amd64 registry.k8s.io/metrics-server/metrics-server:v0.9.0
docker save --platform linux/amd64 -o ms.tar registry.k8s.io/metrics-server/metrics-server:v0.9.0
kind load image-archive ms.tar --name jobtracker

kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
# --kubelet-insecure-tls is required on Kind: the kubelet's serving cert is not
# signed by the cluster CA, so metrics-server refuses to scrape it.
kubectl patch -n kube-system deployment metrics-server --type=json \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
kubectl -n kube-system rollout status deployment/metrics-server

kubectl top pods -n jobtracker  # metrics flowing before trusting the HPA
kubectl -n jobtracker get hpa
./scripts/chaos.sh              # kill a pod, watch Kubernetes replace it
./scripts/load-test.sh          # drive load, watch replicas grow
```

> On a 4-CPU machine the load generator competes with the Kubernetes control plane and can
> starve it — `TLS handshake timeout`, frozen HPA metrics, evicted pods. Run load against a
> trimmed-down cluster, or accept a smaller scale-out. Details in
> [DEPLOY-TESTING.md](DEPLOY-TESTING.md#16-prove-autoscaling-hpa).

Optional in-cluster metrics stack (~600 Mi):

```bash
kubectl apply -k infrastructure/kubernetes/observability
kubectl -n jobtracker port-forward svc/grafana 3000:3000     # admin / admin
```

**Tear down:** `kind delete cluster --name jobtracker`.

---

## Level 4 — Cloud (AWS EKS)

> ⚠️ **This costs real money** — roughly **$230+/month** if left running (EKS control
> plane ~$73, two t3.large nodes ~$120, NAT gateway + load balancers the rest).
> **Always `terraform destroy` when you finish.** Full details:
> `infrastructure/terraform/README.md`.

```bash
# 0. Free, and needs no AWS account: check the config is well-formed.
#    `validate` is offline — passing it does NOT mean it would provision.
cd infrastructure/terraform
cp terraform.tfvars.example terraform.tfvars     # edit region etc. if you like
terraform init
terraform validate        # "Success! The configuration is valid."

# 1. Authenticate to AWS. `plan` refreshes state against the API, so without
#    credentials it stops at "No valid credential sources found".
aws configure          # or aws sso login
terraform plan

# 2. Provision the cluster (~15-20 min) — billing starts here
terraform apply

# 3. Point kubectl at the new cluster (this exact command is a terraform output)
aws eks update-kubeconfig --region us-east-1 --name jobtracker

# 4a. Deploy directly with Kustomize...
#     (first replace GHCR_OWNER in overlays/prod with your GitHub username, and
#      make sure images are pushed to GHCR by the CD pipeline)
kubectl apply -k ../kubernetes/overlays/prod

# 4b. ...or via GitOps with ArgoCD (see infrastructure/argocd/README.md).
#     --server-side is required: the ApplicationSet CRD exceeds the 256 KB
#     annotation limit that client-side apply uses, and is rejected outright.
kubectl create namespace argocd
kubectl apply --server-side=true --force-conflicts -n argocd \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl apply -f ../argocd/application.yaml
```

**Tear down (important):**

```bash
kubectl delete -k ../kubernetes/overlays/prod   # remove LoadBalancers/Ingress first
terraform destroy
```

---

## CI/CD (GitHub)

Push the repo to GitHub and the pipelines run automatically:

- **CI** (`.github/workflows/ci.yml`) — on every push/PR: builds + tests + Docker-builds all three services.
- **CD** (`.github/workflows/cd.yml`) — on push to `main`: pushes images to GHCR and bumps the prod overlay image tags for ArgoCD to deploy.

No secrets needed for CI. CD uses the built-in `GITHUB_TOKEN` to push to GHCR
(enable "Read and write permissions" under repo **Settings → Actions → General**).

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| A service exits/restarts on boot | It waits for Kafka/Postgres. Give it 1–2 min; Compose healthchecks gate startup. |
| `port is already allocated` | Something else uses 8081/5434/6379/3000. Stop it or change the host port in the compose file. |
| `docker-compose: command not found` | Use `docker compose` (with a space). |
| Kind pods stuck `ImagePullBackOff` | Image never reached the node. Check with `docker exec jobtracker-control-plane crictl images`. Re-run the load step. |
| `kind load` fails: `content digest ...: not found` | Multi-platform manifest with missing blobs. Use `docker save --platform linux/amd64` + `kind load image-archive`. |
| Kind pod `Exit Code: 143` with empty logs | Killed by its liveness probe during startup, so it never logged why. |
| `kubectl`: `No resources found` but the cluster is fine | Wrong context — `kubectl config use-context kind-jobtracker`. |
| HPA scaling up with no traffic | CPU requests below idle usage; utilisation is a percentage of the *request*. |
| `kubectl`: `TLS handshake timeout` | Control-plane starvation. Reduce load, then `docker restart jobtracker-control-plane`. |
| ArgoCD install: `Too long: may not be more than 262144 bytes` | Use `kubectl apply --server-side=true`. |
| One Deployment stuck `OutOfSync` in ArgoCD | ArgoCD and the HPA both own `/spec/replicas`; needs `ignoreDifferences`. |
| `analytics-service` summary all zeros | No events yet — create an application first (`./scripts/demo.sh`). |
| HPA shows `<unknown>` targets | metrics-server isn't installed/ready (Level 3 note above). |
