# Actually Deploying This — Verification Guide

The difference between "I wrote Kubernetes manifests" and "I deployed to Kubernetes"
is about three hours of work. This guide closes that gap, in order of cost.

| Phase | What it proves | Cost |
|---|---|---|
| 1. Kind (local K8s) | Real Kubernetes deployment, probes, HPA, self-healing | Free |
| 2. ArgoCD on Kind | Real GitOps — cluster syncing from your Git repo | Free |
| 3. AWS EKS via Terraform | Real cloud infrastructure provisioning | ~$5–15 if destroyed same day |

Capture a screenshot at each ✅ checkpoint. Those screenshots are the evidence.

---

## Phase 1 — Kubernetes on Kind (free)

### Prerequisites

```bash
# Windows (winget) — or use choco/scoop
winget install Kubernetes.kind
winget install Kubernetes.kubectl
```

Give Docker Desktop at least **6 GB RAM** (Settings → Resources). Kafka + Postgres +
Redis + 4 app pods is a real workload.

### 1.1 Create the cluster

```bash
kind create cluster --config infrastructure/kubernetes/overlays/local/kind-cluster.yaml
kubectl cluster-info
kubectl get nodes
```

✅ **Checkpoint:** three nodes (1 control-plane, 2 workers) in `Ready`.

### 1.2 Build and load images

Kind nodes have their own image store — images must be loaded explicitly.

```bash
for s in application-service notification-service analytics-service frontend; do
  docker build -t $s:latest ./$s
  kind load docker-image $s:latest --name jobtracker
done
```

### 1.3 Deploy

```bash
kubectl apply -k infrastructure/kubernetes/overlays/local
kubectl -n jobtracker get pods -w
```

Postgres and Kafka come up first; the Java services take ~2–3 minutes.

✅ **Checkpoint:** all pods `Running` and `READY 1/1`. Screenshot this.

```bash
kubectl -n jobtracker get all
```

### 1.4 Reach the app

```bash
kubectl -n jobtracker port-forward svc/frontend 8080:80
```

Open http://localhost:8080 — the full UI, now served from Kubernetes.

✅ **Checkpoint:** create an application in the UI, confirm the reminder appears.
The whole event pipeline is now running in-cluster.

### 1.5 Prove self-healing (chaos)

```bash
kubectl -n jobtracker get pods -l app=application-service
kubectl -n jobtracker delete pod <one-pod-name>
kubectl -n jobtracker get pods -l app=application-service -w
```

✅ **Checkpoint:** Kubernetes schedules a replacement automatically and it passes
readiness. Screenshot the before/after. (`./scripts/chaos.sh` does this for you.)

### 1.6 Prove autoscaling (HPA)

HPA needs metrics-server, which Kind doesn't ship:

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl patch -n kube-system deployment metrics-server --type=json \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'

kubectl -n kube-system rollout status deployment/metrics-server
kubectl -n jobtracker get hpa
```

Wait until the `TARGETS` column shows a percentage instead of `<unknown>`, then drive load:

```bash
# terminal 1
kubectl -n jobtracker port-forward svc/application-service 8081:8081
# terminal 2
./scripts/load-test.sh 5000 40
# terminal 3
kubectl -n jobtracker get hpa -w
```

✅ **Checkpoint:** `REPLICAS` climbs from 2 upward under load. Screenshot it —
this is the single most convincing K8s artifact you can produce.

### 1.7 Tear down

```bash
kind delete cluster --name jobtracker
```

---

## Phase 2 — ArgoCD GitOps on Kind (free)

This makes the GitOps claim real without spending anything.

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl -n argocd rollout status deployment/argocd-server

# Password
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d; echo

# UI
kubectl -n argocd port-forward svc/argocd-server 8090:443
```

Open https://localhost:8090 (accept the self-signed cert), log in as `admin`.

Point ArgoCD at your repo — edit `infrastructure/argocd/application.yaml` so `repoURL`
is your repo and `path` is `infrastructure/kubernetes/overlays/local` (the prod overlay
pulls from GHCR, which Kind can't reach without a pull secret), then:

```bash
kubectl apply -f infrastructure/argocd/application.yaml
```

✅ **Checkpoint:** the ArgoCD UI shows the app tree with everything **Synced / Healthy**.
Screenshot it.

**The killer demo:** change something trivial in Git (e.g. bump `replicas` in
`application-service.yaml`), push, then hit **Refresh** in ArgoCD and watch it detect
drift and reconcile the cluster to match Git. That's GitOps, demonstrated end to end.

---

## Phase 3 — AWS EKS via Terraform (costs money)

> ⚠️ EKS bills ~$0.10/hr for the control plane plus ~$0.17/hr for two t3.large nodes,
> plus NAT gateway. Running it for **3 hours and destroying it costs roughly $2–5**.
> Leaving it up for a month is ~$230. **Set a calendar reminder to destroy it.**

```bash
cd infrastructure/terraform
cp terraform.tfvars.example terraform.tfvars

terraform init
terraform validate          # ✅ Checkpoint: "Success! The configuration is valid."
terraform plan              # ✅ Checkpoint: screenshot the resource count
terraform apply             # ~15–20 min
```

```bash
aws eks update-kubeconfig --region us-east-1 --name jobtracker
kubectl get nodes           # ✅ Checkpoint: EKS nodes Ready
```

Deploy (images already in GHCR from your CD pipeline — make the packages public first,
under GitHub → Packages → each package → Settings → Change visibility):

```bash
kubectl apply -k infrastructure/kubernetes/overlays/prod
kubectl -n jobtracker get pods
```

✅ **Checkpoint:** pods running on EKS. Screenshot `kubectl get nodes` + `get pods`
together — that's proof it ran on real cloud infrastructure.

### Destroy — do not skip this

```bash
kubectl delete -k infrastructure/kubernetes/overlays/prod   # removes LoadBalancers first
terraform destroy
```

Then verify in the AWS console that the EKS cluster, NAT gateway, and any ELBs are gone.
Orphaned load balancers are the usual cause of surprise bills.

---

## How to describe this honestly

Once Phases 1–2 are done you can truthfully say:

> "Deployed to Kubernetes (Kind) with liveness/readiness probes and HPA autoscaling;
> verified self-healing under pod failure and scale-out under load. Delivered via ArgoCD
> GitOps syncing from Git. Terraform for EKS is written and validated."

After Phase 3:

> "…and provisioned an AWS EKS cluster with Terraform (VPC, IRSA, managed node groups),
> deployed the stack to it, then tore it down."

**What not to say:** "production experience", "handles X requests/sec", or "deployed to
production". This is a personal project deployed to real infrastructure — that framing is
respected. Overclaiming is not, and experienced reviewers detect it in one question.

Being explicit about what you *did* and *didn't* run is a credibility asset. Very few
portfolio repos do it, and it pre-empts exactly the "did you actually deploy this?"
challenge.
