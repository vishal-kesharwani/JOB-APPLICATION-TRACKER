# Actually Deploying This — Verification Guide

The difference between "I wrote Kubernetes manifests" and "I deployed to Kubernetes"
is about three hours of work. This guide closes that gap, in order of cost.

| Phase | What it proves | Cost |
|---|---|---|
| 1. Kind (local K8s) | Real Kubernetes deployment, probes, HPA, self-healing | Free |
| 2. ArgoCD on Kind | Real GitOps — cluster syncing from your Git repo | Free |
| 3. AWS EKS via Terraform | Real cloud infrastructure provisioning | ~$5–15 if destroyed same day |

Capture a screenshot at each ✅ checkpoint. Those screenshots are the evidence.

> **Every command here has been run.** Where a command that looks obvious does not work,
> the working alternative is given with the reason. **[KIND-DEPLOYMENT-LOG.md](KIND-DEPLOYMENT-LOG.md)**
> records the eleven bugs this process surfaced, with full diagnosis.

---

## Phase 1 — Kubernetes on Kind (free)

### Prerequisites

```bash
winget install Kubernetes.kind
winget install Kubernetes.kubectl
```

Both install to a winget path that is not on `PATH` until you **restart your shell**.

**Memory.** Docker Desktop → Settings → Resources. 6 GB is comfortable; the stack has been
run in 4.3 GB but with no headroom for load testing (see 1.6). On Windows, Docker's memory
comes from WSL2 — check `%USERPROFILE%\.wslconfig`:

```ini
[wsl2]
memory=6GB
processors=4
```

Then `wsl --shutdown` and restart Docker Desktop.

**Turn off Docker Desktop's built-in Kubernetes** (Settings → Kubernetes). It competes for
the same memory and will silently steal your `kubectl` context.

### 1.1 Create the cluster

```bash
kind create cluster --config infrastructure/kubernetes/overlays/local/kind-cluster.yaml
kubectl cluster-info
kubectl get nodes
```

✅ **Checkpoint:** one node, `Ready`.

The config is **single-node on purpose**. Each kind node is a container running systemd and
a kubelet, costing ~1 GB before a single pod is scheduled; a 3-node cluster fails to boot in
4.3 GB with `could not find a log line that matches "Reached target .*Multi-User System.*"`.
Nothing in this guide needs more than one node. Uncomment the workers in
`kind-cluster.yaml` only if you have 12 GB+ free and want to show pods spread across nodes.

**If `kubectl` says resources are missing**, check your context before anything else:

```bash
kubectl config current-context     # must be kind-jobtracker
kubectl config use-context kind-jobtracker
```

### 1.2 Build and load images

Kind nodes have their own containerd store — images must be loaded explicitly.

```bash
for s in application-service notification-service analytics-service frontend; do
  docker build -t $s:latest ./$s
  kind load docker-image $s:latest --name jobtracker
done
```

Expect **~30 minutes** on first run; the Maven builds dominate (264s, 515s, 662s).

Kafka's image also has to be side-loaded, and here `kind load docker-image` **fails**:

```
ctr: content digest sha256:b2fa98…: not found
```

Docker's containerd store holds a multi-platform manifest whose non-amd64 blobs were never
pulled, and `kind load` demands all platforms. Export a single-platform archive instead:

```bash
docker pull --platform linux/amd64 confluentinc/cp-kafka:7.6.1
docker save --platform linux/amd64 -o cp-kafka.tar confluentinc/cp-kafka:7.6.1
kind load image-archive cp-kafka.tar --name jobtracker
```

Verify anything you load actually landed:

```bash
docker exec jobtracker-control-plane crictl images
```

> Use `kind load docker-image` for images **you built locally**, and the
> `docker save --platform` → `kind load image-archive` route for images **pulled from a
> registry**. This applies to every pulled image below.

### 1.3 Deploy

```bash
kubectl apply -k infrastructure/kubernetes/overlays/local
kubectl -n jobtracker get pods -w
```

Postgres and Kafka come up first; the Java services take ~2–3 minutes on a quiet machine and
considerably longer on a busy one — the startup probes allow up to 5 minutes.

✅ **Checkpoint:** all pods `Running` and `READY 1/1`. Screenshot this.

```bash
kubectl -n jobtracker get all
```

If a pod is stuck, these three tell you almost everything:

```bash
kubectl -n jobtracker describe pod <pod>          # events: probes, pulls, scheduling
kubectl -n jobtracker logs <pod>                  # current container
kubectl -n jobtracker logs <pod> --previous       # the one that just died
```

`Exit Code: 143` means the kubelet killed it (SIGTERM), usually a failed liveness probe —
not an application crash. Empty logs plus 143 means it never finished starting.

### 1.4 Reach the app

```bash
kubectl -n jobtracker port-forward svc/frontend 8080:80
```

Open http://localhost:8080 — the full UI, served from Kubernetes.

✅ **Checkpoint:** create an application in the UI, confirm the reminder appears.
The whole event pipeline is now running in-cluster.

### 1.5 Prove self-healing (chaos)

```bash
kubectl -n jobtracker get pods -l app=application-service -o wide
kubectl -n jobtracker delete pod <one-pod-name>
kubectl -n jobtracker get pods -l app=application-service -o wide
kubectl -n jobtracker wait --for=condition=ready pod -l app=application-service --timeout=300s
```

Then show that the *controller* did it, not you:

```bash
kubectl -n jobtracker get events --sort-by=.lastTimestamp | grep application-service
```

✅ **Checkpoint:** `SuccessfulCreate` from the ReplicaSet, a new pod name and pod IP, and
`0` restarts once Ready. Measured here: replacement scheduled in **32s**, Ready in **~3m**.
Screenshot before and after. (`./scripts/chaos.sh` does the same thing.)

### 1.6 Prove autoscaling (HPA)

metrics-server does not ship with kind, and needs `--kubelet-insecure-tls` because the
kubelet's serving certificate is not signed by the cluster CA.

```bash
curl -sL -o metrics-server.yaml \
  https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# side-load the image (see 1.2)
docker pull --platform linux/amd64 registry.k8s.io/metrics-server/metrics-server:v0.9.0
docker save --platform linux/amd64 -o ms.tar registry.k8s.io/metrics-server/metrics-server:v0.9.0
kind load image-archive ms.tar --name jobtracker

kubectl apply -f metrics-server.yaml
kubectl patch -n kube-system deployment metrics-server --type=json \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
kubectl -n kube-system rollout status deployment/metrics-server
```

Confirm metrics are actually flowing before trusting the HPA:

```bash
kubectl top pods -n jobtracker
kubectl -n jobtracker get hpa
```

Wait until `TARGETS` shows a percentage instead of `<unknown>`.

> **Watch for scale-out with no traffic.** If `TARGETS` reads over 100% at idle, the CPU
> *requests* are below what an idle JVM uses — HPA utilisation is a percentage of the
> request. The autoscaler will climb to `maxReplicas` and exhaust the node. This overlay
> requests 250m for that reason; an idle Spring Boot service here uses 110–130m.

Drive load:

```bash
# terminal 1
kubectl -n jobtracker port-forward svc/application-service 8081:8081
# terminal 2
./scripts/load-test.sh 5000 40
# terminal 3
kubectl -n jobtracker get hpa -w
```

✅ **Checkpoint:** `REPLICAS` climbs and an event appears:

```bash
kubectl -n jobtracker describe hpa application-service | grep SuccessfulRescale
```

```
SuccessfulRescale  New size: 2; reason: cpu resource utilization
                   (percentage of request) above target
```

> **Be realistic about the load.** On a 4-CPU machine the load generator competes with the
> control plane. At 12 concurrent workers this node hit load average **51**, etcd saturated,
> `kube-controller-manager` restarted 34 times, `kubectl` returned `TLS handshake timeout`,
> and pods were evicted — the test destroyed what it was measuring. If that happens, delete
> the observability stack, scale deployments to 1, and `docker restart jobtracker-control-plane`
> (which preserves the loaded images). For a clean run, deploy only `application-service` and
> Postgres and leave everything else off.

### 1.7 Tear down

```bash
kind delete cluster --name jobtracker
```

This discards every side-loaded image with the node. Rebuilding means re-running `kind load`,
though not the Maven builds — those images remain in your host Docker daemon.

---

## Phase 2 — ArgoCD GitOps on Kind (free)

**Push your work first.** ArgoCD syncs from GitHub, not from your working tree. With
`selfHeal: true` it will actively revert the cluster to whatever is on `main` — including
config you fixed locally but never pushed.

```bash
git status -sb          # must not say "ahead"
```

Side-load the images (Dex is skipped — it is only needed for SSO):

```bash
for img in quay.io/argoproj/argocd:v3.4.5 public.ecr.aws/docker/library/redis:8.2.3-alpine; do
  docker pull --platform linux/amd64 "$img"
  f=$(echo "$img" | tr '/:' '__').tar
  docker save --platform linux/amd64 -o "$f" "$img"
  kind load image-archive "$f" --name jobtracker
done
```

Install. **`kubectl apply` cannot do this** — the ApplicationSet CRD is larger than the
256 KB `last-applied-configuration` annotation that client-side apply writes:

```
The CustomResourceDefinition "applicationsets.argoproj.io" is invalid:
metadata.annotations: Too long: may not be more than 262144 bytes
```

Use server-side apply, which tracks ownership in managed fields instead:

```bash
kubectl create namespace argocd
curl -sL -o argocd-install.yaml \
  https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl apply --server-side=true --force-conflicts -n argocd -f argocd-install.yaml

# trim what this demo does not use
kubectl -n argocd scale deploy \
  argocd-dex-server argocd-notifications-controller argocd-applicationset-controller --replicas=0

kubectl -n argocd rollout status deployment/argocd-server
```

Four pods reach `1/1` in about two minutes.

```bash
# password
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d; echo

# UI
kubectl -n argocd port-forward svc/argocd-server 8090:443
```

Open https://localhost:8090 (accept the self-signed cert), log in as `admin`.

Edit `infrastructure/argocd/application.yaml` so `repoURL` is your repo. `path` should be
`infrastructure/kubernetes/overlays/local` — the prod overlay pulls from GHCR, which Kind
cannot reach without a pull secret, so a local cluster pointed at prod stays permanently
degraded and proves nothing.

```bash
kubectl apply -f infrastructure/argocd/application.yaml
kubectl -n argocd get application jobtracker -w
```

✅ **Checkpoint:** the app tree shows everything **Synced / Healthy**. Screenshot it.

> **If one Deployment sits permanently `OutOfSync`,** ArgoCD and the HPA are fighting over
> `/spec/replicas`: Git says one number, the HPA sets another, `selfHeal` reverts it, repeat.
> The Application already carries `ignoreDifferences` on that field for this reason. Check
> which resource is drifting with:
> ```bash
> kubectl -n argocd get application jobtracker \
>   -o jsonpath='{range .status.resources[?(@.status=="OutOfSync")]}{.kind}/{.name}{"\n"}{end}'
> ```

**The killer demo:** change something in Git — a replica count, an HPA bound — commit, push,
and run **nothing** against the cluster:

```bash
git commit -am "bump replicas" && git push origin main
kubectl -n jobtracker get hpa -w        # just watching
```

✅ **Checkpoint:** the cluster changes on its own. Measured here: pushed at **02:51**,
cluster changed at **02:54:30** — a ~3m30s lag, which is ArgoCD's default polling interval.
Hit **Refresh** in the UI to skip the wait. Note the lag rather than hiding it; GitOps is
eventually consistent by design.

If your CD pipeline commits on top of your pushes (this one does), ArgoCD reconciles against
the pipeline's commit, not yours — the deployed revision is never quite the one you pushed.

---

## Phase 3 — AWS EKS via Terraform (costs money)

> ⚠️ EKS bills ~$0.10/hr for the control plane plus ~$0.17/hr for two t3.large nodes,
> plus NAT gateway. Running it for **3 hours and destroying it costs roughly $2–5**.
> Leaving it up for a month is ~$230. **Set a calendar reminder to destroy it.**

### Free — no AWS account needed

```bash
cd infrastructure/terraform
cp terraform.tfvars.example terraform.tfvars

terraform init
terraform validate          # ✅ Checkpoint: "Success! The configuration is valid."
terraform fmt -check -recursive
```

`validate` is offline — syntax, types and references only. **It does not talk to AWS**, so
passing it does not mean the config would provision. Be precise about that distinction when
describing what you verified.

### Requires AWS credentials

```bash
aws configure               # or export AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY
terraform plan              # ✅ Checkpoint: screenshot the resource count
```

Without credentials `plan` stops at `Error: No valid credential sources found` — it
authenticates and refreshes state to build a diff, so it is not an offline operation.

### Billable

```bash
terraform apply             # ~15–20 min
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

## Quick reference — failures and their causes

| Symptom | Cause | Section |
|---|---|---|
| `could not find a log line that matches "Reached target…"` | Too many kind nodes for available RAM | 1.1 |
| `No resources found` but cluster is fine | `kubectl` context switched to `docker-desktop` | 1.1 |
| `ctr: content digest …: not found` | `kind load docker-image` on a multi-platform image | 1.2 |
| `ErrImagePull` after side-loading | Image never landed — check `crictl images` | 1.2 |
| Pod `Exit Code: 143`, empty logs | Liveness probe killed it during startup | 1.3 |
| Kafka exits 1 at `Configuring …` | K8s service-link env vars parsed as broker config | log |
| Kafka starts but never Ready | KRaft quorum voter pointed at a ClusterIP | log |
| HPA `TARGETS` over 100% at idle | CPU requests below idle usage | 1.6 |
| HPA `TARGETS` frozen / `<unknown>` | metrics-server starved or pods unready | 1.6 |
| `kubectl`: `TLS handshake timeout` | Control plane starved — reduce load, restart node | 1.6 |
| ArgoCD CRD `Too long: may not be more than 262144 bytes` | Client-side apply; use `--server-side` | 2 |
| One Deployment permanently `OutOfSync` | ArgoCD and HPA both own `/spec/replicas` | 2 |
| `terraform plan`: `No valid credential sources found` | `plan` needs AWS credentials; `validate` does not | 3 |

Full diagnosis for each: **[KIND-DEPLOYMENT-LOG.md](KIND-DEPLOYMENT-LOG.md)**.

---

## How to describe this honestly

Once Phases 1–2 are done you can truthfully say:

> "Deployed to Kubernetes (Kind) with liveness, readiness and startup probes; verified
> self-healing under pod failure and HPA scale-out under load. Delivered via ArgoCD GitOps
> syncing from Git. Terraform for EKS is written and validated."

After Phase 3:

> "…and provisioned an AWS EKS cluster with Terraform (VPC, IRSA, managed node groups),
> deployed the stack to it, then tore it down."

**What not to say:** "production experience", "handles X requests/sec", or "deployed to
production". This is a personal project deployed to real infrastructure — that framing is
respected. Overclaiming is not, and experienced reviewers detect it in one question.

Being explicit about what you *did* and *didn't* run is a credibility asset. Very few
portfolio repos do it, and it pre-empts exactly the "did you actually deploy this?"
challenge. The strongest version of that is a written record of what broke and why —
which is what [KIND-DEPLOYMENT-LOG.md](KIND-DEPLOYMENT-LOG.md) is for.
