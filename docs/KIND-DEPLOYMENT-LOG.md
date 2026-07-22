# Deploying to Kubernetes: an engineering log

This is a factual record of taking manifests that had never been applied to a cluster and
making them actually run on Kubernetes. It documents every failure, the diagnosis, the fix,
and the numbers — because the failures are where the learning is, and because "I wrote
Kubernetes manifests" and "I deployed to Kubernetes" are different claims.

Nine distinct bugs were found. **Not one of them was visible in Docker Compose.**

---

## Environment

Everything below ran on a single laptop. The constraints matter, because several failures are
consequences of them.

| | |
|---|---|
| Host | Windows 11, **8 GB** total RAM (7,958 MB visible), **4** logical CPUs |
| Docker Desktop | 29.1.5, allocated **4,629,704,704 bytes (4.31 GiB)** and 4 CPUs via WSL2 |
| kind | v0.32.0 |
| kubectl | v1.34.1 (Kustomize v5.7.1) |
| Node image | `kindest/node:v1.36.1` |
| Cluster | `jobtracker`, single node |

Workload: 3 Spring Boot services + React/nginx frontend + Kafka + PostgreSQL + Redis, later
joined by Prometheus, Grafana, an OTel Collector and metrics-server.

---

## Phase 1 — Kubernetes on kind

### 1.1 Cluster creation

The first attempt used the 3-node topology (1 control-plane, 2 workers) from the original
`kind-cluster.yaml`:

```
 • Preparing nodes 📦 📦 📦   ...
 ✗ Preparing nodes 📦 📦 📦
ERROR: failed to create cluster: could not find a log line that matches
       "Reached target .*Multi-User System.*|detected cgroup v1"
```

**Diagnosis.** Not a config error — resource exhaustion. Each kind node is a full container
running systemd and a kubelet, costing roughly 1 GB before scheduling a single pod. Three
nodes did not fit in 4.31 GiB, and the nodes never finished booting, so kind timed out
waiting for systemd's multi-user target.

**Fix.** Dropped to a single node. Nothing that needed proving — probes, HPA, self-healing,
rescheduling — requires more than one node.

```
kubectl get nodes
NAME                       STATUS   ROLES           AGE    VERSION
jobtracker-control-plane   Ready    control-plane   101s   v1.36.1
```

> **Learning.** Multi-node kind is for testing topology constraints (anti-affinity, spread).
> If you are not testing those, extra nodes cost ~1 GB each and buy nothing.

### 1.2 Building and side-loading images

kind nodes have their own containerd image store; images must be loaded explicitly. Build
times on this hardware, from the actual build output:

| Image | Build time | Size on node |
|---|---|---|
| application-service | 290.8s | 145 MB |
| notification-service | 570.6s | 120 MB |
| analytics-service | 735.8s | 129 MB |
| frontend | 221.8s | 21 MB |

Roughly **30 minutes** of Maven builds. The Java builds dominate: `mvn package` took 264s,
515s and 662s respectively.

### 1.3 Deploy — and then nine bugs

`kubectl apply -k` succeeded and created every object. Then nothing became healthy. Each
failure below is a separate root cause.

---

#### Bug 1 — Kafka image no longer exists

```
Failed to pull image "bitnami/kafka:3.7": failed to resolve reference
"docker.io/bitnami/kafka:3.7": ... dial tcp: lookup registry-1.docker.io on
192.168.65.254:53: no such host
```

Bitnami withdrew their free public Docker Hub images. The tag the manifest referenced cannot
be pulled by anyone any more.

**Fix.** Switched to `confluentinc/cp-kafka:7.6.1` — the image `docker-compose.yml` already
used, so it was already on the machine and could be side-loaded.

> **Learning.** A manifest referencing an upstream tag is a dependency on someone else's
> hosting decisions. It worked when written and broke without any change to this repo.

---

#### Bug 2 — the node resolved registries to IPv6 only

The DNS error above was investigated separately:

```
docker exec jobtracker-control-plane getent hosts registry-1.docker.io
2600:1f18:2148:bc01:6617:752a:5be3:ee3f  registry-1.docker.io
2600:1f18:2148:bc02:f0b3:5f17:1316:85e3  registry-1.docker.io
...
```

Every answer is AAAA (IPv6). The node has no working IPv6 route, so pulls failed even for
images that do exist.

**Consequence.** Every image had to be side-loaded from the host rather than pulled. This is
why `kind load` appears so often below.

---

#### Bug 3 — `kind load docker-image` failed on a multi-platform manifest

```
ERROR: failed to load image: command "docker exec --privileged -i jobtracker-control-plane
ctr --namespace=k8s.io images import --all-platforms --digests --snapshotter=overlayfs -"
failed with error: exit status 1
Command Output: ctr: content digest
sha256:b2fa9828e9f3f4eff25294798c362acf76d82118376b97fbff622d9c8a06df01: not found
```

**Diagnosis.** Docker's containerd image store holds a multi-platform manifest list, but only
the amd64 blobs are present locally. `kind load` passes `--all-platforms`, so `ctr` demands
blobs for platforms that were never pulled.

**Fix.** Export a single-platform archive and load that instead:

```bash
docker save --platform linux/amd64 -o cp-kafka.tar confluentinc/cp-kafka:7.6.1
kind load image-archive cp-kafka.tar --name jobtracker
```

Archive size: **452,819,968 bytes (452 MB)**. Verified on the node:

```
docker.io/confluentinc/cp-kafka   7.6.1   5b4c63590c11c   453MB
```

> **Learning.** `kind load docker-image` and `kind load image-archive` are not equivalent.
> The archive path is the reliable one when Docker uses the containerd image store.

---

#### Bug 4 — Kubernetes service env vars broke the Kafka broker

With the image finally present, the broker started and immediately exited 1:

```
===> Configuring ...
Running in KRaft mode...
port is deprecated. Please use KAFKA_ADVERTISED_LISTENERS instead.
[exit code 1]
```

**Diagnosis.** That deprecation warning is the tell. Kubernetes injects an environment
variable for every Service in the namespace — including `KAFKA_PORT=tcp://10.96.136.206:9092`,
`KAFKA_SERVICE_HOST`, `KAFKA_PORT_9092_TCP`. The Confluent image's entrypoint treats **every**
`KAFKA_*` variable as broker configuration and writes it into `server.properties`. So
Kubernetes' own service discovery silently corrupted the broker config.

**Fix.**

```yaml
spec:
  enableServiceLinks: false
```

> **Learning.** Service-link env vars are legacy behaviour, on by default, and hostile to any
> image that uses a `<COMPONENT>_*` env-var convention. This failure mode cannot occur in
> Compose — it is created by Kubernetes itself.

---

#### Bug 5 — KRaft controller hairpinned through its own ClusterIP

The broker now started but never became ready:

```
WARN [BrokerToControllerChannelManager id=1 name=heartbeat] Connection to node 1
(kafka/10.96.136.206:9093) could not be established. Broker may not be available.
INFO [BrokerLifecycleManager id=1] Unable to register the broker because the RPC got
timed out before it could be sent.
```

**Diagnosis.** `KAFKA_CONTROLLER_QUORUM_VOTERS` was `1@kafka:9093`, which resolves to the
ClusterIP. The pod was therefore trying to reach *itself* by routing out to a virtual IP and
back — a hairpin that kube-proxy in kind does not return. In a single-node KRaft cluster the
broker and controller are the same JVM, so no network hop is needed at all.

**Fix.** `KAFKA_CONTROLLER_QUORUM_VOTERS: "1@localhost:9093"`.

```
[2026-07-22 18:52:20,769] INFO [KafkaRaftServer nodeId=1] Kafka Server started
```

---

#### Bug 6 — liveness probes killed every JVM mid-boot

All three Java services crashlooped. The logs were empty, which was itself the clue.

```
Exit Code:    143
Normal   Killing   kubelet   Container application-service failed liveness probe,
                             will be restarted
Warning  Unhealthy kubelet   Liveness probe failed: Get ".../health/liveness":
                             dial tcp 10.244.0.5:8081: connect: connection refused
```

**Diagnosis.** Exit 143 is SIGTERM (128 + 15) — the kubelet killing the container, not the
app crashing. `livenessProbe.initialDelaySeconds: 40` assumed a JVM boots in 40 seconds. On a
node with 4 shared CPUs and three JVMs starting at once, it does not. The services were being
killed before they could report *why* they were unhealthy, which is why the logs were empty.

**Fix.** A `startupProbe` gating liveness, allowing up to 5 minutes to boot, after which
liveness takes over at its normal cadence:

```yaml
startupProbe:
  httpGet: { path: /actuator/health/liveness, port: 8081 }
  periodSeconds: 10
  failureThreshold: 30      # 30 × 10s = 5 minutes
  timeoutSeconds: 3
```

Probe `timeoutSeconds` was also raised from the 1s default — under CPU pressure a healthy
JVM regularly takes longer than 1s to answer, producing false failures.

**Result:** all three services reached `1/1 Running` with **0 restarts**.

> **Learning.** `initialDelaySeconds` guesses at startup time and is wrong on any machine
> that is not the one you tuned it on. A startup probe expresses *"this may take a while"*
> without weakening liveness afterwards. This is the single most transferable fix here.

---

### 1.3 Checkpoint — full stack running

```
NAME                                    READY   STATUS    RESTARTS   AGE
analytics-service-b78899bd4-fsrbp       1/1     Running   0          2m43s
application-service-588dcf87dd-hxp7d    1/1     Running   0          11m
frontend-55458b7684-bt9pr               1/1     Running   0          13m
frontend-55458b7684-mlx2p               1/1     Running   0          116m
kafka-0                                 1/1     Running   0          13m
notification-service-7f6c84c76f-7mwmf   1/1     Running   0          6m25s
postgres-0                              1/1     Running   0          116m
redis-7dfcbc5bc4-47qhq                  1/1     Running   0          116m
```

Every pod started after the fixes shows **0 restarts**.

Peak memory at this point: **2.2 GiB of 4.31 GiB**.

---

### 1.4 A bug that was not a bug: the wrong kubectl context

At one point every command returned:

```
kubectl -n jobtracker get pods
No resources found in jobtracker namespace.
```

The cluster was fine. `kubectl config current-context` was `docker-desktop` — Docker
Desktop's own Kubernetes had been enabled and had silently taken the current context.

```
kubectl config get-contexts
CURRENT   NAME              CLUSTER
*         docker-desktop    docker-desktop
          kind-jobtracker   kind-jobtracker
```

Worse, that second cluster was competing for the same 4.31 GiB. It was disabled.

> **Learning.** Check `current-context` before believing that resources have vanished.

---

## Phase 1b — Observability in-cluster

The app pods pointed `OTEL_EXPORTER_OTLP_ENDPOINT` at `http://otel-collector:4318`, but no
such Service existed in the cluster — trace export was failing silently. Deployed OTel
Collector + Prometheus + Grafana (`infrastructure/kubernetes/observability/`), applied
separately so it can be removed when memory is needed.

**Tempo, Loki and Promtail were deliberately omitted.** The full LGTM stack does not fit in
the available memory. Traces therefore go to the collector's debug exporter, not a queryable
backend — distributed tracing remains a Compose-only demonstration. Only the Prometheus
datasource is provisioned, because pointing a datasource at a Service that does not exist
produces broken panels rather than useful ones.

Prometheus uses Kubernetes service discovery driven by the `prometheus.io/*` annotations
already on the pods, rather than Compose's static host list — so replicas created by the HPA
are scraped automatically with no config change.

All five targets healthy:

```
application-service   :8081/actuator/prometheus   up
notification-service  :8082/actuator/prometheus   up
analytics-service     :8083/actuator/prometheus   up
otel-collector        :8889/metrics               up
prometheus            localhost:9090/metrics      up
```

---

#### Bug 7 — the shared dashboard's "Services Up" panel read `No data`

The panel query is:

```promql
count(up{job=~".*-service"} == 1)
```

Under Compose each service is its own scrape job. Under Kubernetes service discovery every
target lands in `job="kubernetes-pods"`, so the regex matched nothing.

**Fix — in Prometheus, not the dashboard.** A relabel rule rewrites `job` from the pod's
`app` label, reproducing the Compose convention:

```yaml
- source_labels: [__meta_kubernetes_pod_label_app]
  action: replace
  target_label: job
```

Verified against the exact panel query:

```json
{"status":"success","data":{"resultType":"vector",
 "result":[{"metric":{},"value":[1784748625.413,"3"]}]}}
```

`3`. One dashboard JSON now works unmodified in both environments.

> **Learning.** When a dashboard breaks across environments, fix the label pipeline, not the
> dashboard. Otherwise you maintain two copies that drift.

---

## Phase 1.5 — Chaos: self-healing

```
== Pods before ==
application-service-588dcf87dd-hxp7d   1/1   Running   0   64m   10.244.0.28

== Deleting pod ==
pod "application-service-588dcf87dd-hxp7d" deleted

== 32 seconds later ==
application-service-588dcf87dd-tn7t4   0/1   Running   0   32s   10.244.0.35

== Recovered ==
application-service-588dcf87dd-tn7t4   1/1   Running   0   3m7s  10.244.0.35
```

Events confirming the controller acted without intervention:

```
Normal  SuccessfulCreate  replicaset/application-service-588dcf87dd
                          Created pod: application-service-588dcf87dd-tn7t4
Normal  Pulled            Container image "application-service:latest" already present
Normal  Started           Container started
```

**Recovery time: ~3 minutes**, almost all of it JVM startup.

A second observation from the same events — the startup-probe fix working:

```
Warning  Unhealthy  85s  Startup probe failed: ... connect: connection refused
```

The startup probe failed repeatedly during boot and the container was **not** killed. Before
Bug 6's fix, liveness would have killed it there and the pod would never have recovered.

---

## Phase 1.6 — HPA: partially achieved

### metrics-server

kind requires `--kubelet-insecure-tls` (the kubelet's serving cert is not signed by the
cluster CA). The image was side-loaded for the same IPv6 reason as everything else:
`registry.k8s.io/metrics-server/metrics-server:v0.9.0`, **23.3 MB** on the node.

First real measurements of the stack:

```
NAME                                    CPU(cores)   MEMORY(bytes)
kafka-0                                 349m         482Mi
application-service-588dcf87dd-tn7t4    126m         282Mi
analytics-service-b78899bd4-fsrbp       121m         234Mi
notification-service-7f6c84c76f-7mwmf   108m         197Mi
grafana-7b4bcccd5b-m44tb                44m          53Mi
prometheus-584fcb9665-bg79p             41m          40Mi
otel-collector-56c55f7b74-qjjlv         38m          31Mi
postgres-0                              28m          51Mi
redis-7dfcbc5bc4-47qhq                  16m          5Mi
frontend-55458b7684-bt9pr               1m           4Mi
```

---

#### Bug 8 — the HPA scaled out at idle

The moment metrics arrived, with **zero traffic**:

```
NAME                   TARGETS         MINPODS  MAXPODS  REPLICAS
notification-service   cpu: 144%/75%   1        4        1
```

and 45 seconds later:

```
analytics-service      cpu: 102%/75%   1        4        3
notification-service   cpu: 265%/75%   1        4        2   → 4
```

**Diagnosis.** HPA utilisation is a percentage **of the CPU request**, not of the limit or of
the node. The local overlay had trimmed requests to 75–100m to make the stack fit, but the
table above shows an idle Spring Boot JVM uses 108–126m. Idle usage was therefore already
144–265% of a 75% target, so the autoscaler scaled out at rest — and each new replica was
equally "overloaded", so it kept going.

This is visible in the Prometheus targets page captured at the time: `kubernetes-pods
(8/10 up)`, with two `analytics-service` replicas `DOWN` — pods created by the idle
scale-out that never became ready, because there was no CPU left for them to start.

**Fix.** Requests raised to 250m to reflect real idle cost:

| | before | after |
|---|---|---|
| application-service | 100m | 250m |
| notification-service | 75m | 250m |
| analytics-service | 75m | 250m |

Result at idle:

```
analytics-service      cpu: 38%/75%   1   4   1
application-service    cpu: 7%/70%    1   3   1
frontend               cpu: 3%/75%    2   5   2
notification-service   cpu: 29%/75%   1   4   2
```

> **Learning.** Undersized requests do not just mislead the scheduler — they actively break
> the HPA, because the request *is* the denominator. "Make it fit" and "make autoscaling
> work" pull in opposite directions, and the request has to reflect reality for either to
> function.

---

#### Bug 9 — Grafana restart loop (the same bug as Bug 6)

```
RESTARTS: 7    Exit Code: 2
Warning  Unhealthy  Liveness probe failed: Get "http://10.244.0.31:3000/api/health":
                    context deadline exceeded
Normal   Killing    Container grafana failed liveness probe, will be restarted
```

Grafana was given a liveness probe and no startup probe — exactly the mistake fixed for the
Java services. Provisioning datasources and dashboards on a contended node exceeded the
probe's patience. Same fix; restarts went to **0**.

> **Learning.** The fix for Bug 6 was applied to the services but not carried to new
> workloads added later. A fix is not complete until it is applied to the pattern, not the
> instance.

---

### Load test — what was and was not proven

**Attempt 1 — 12 concurrent workers, in-cluster.** The HPA scaled out, twice:

```
Normal  SuccessfulRescale  New size: 2; reason: cpu resource utilization
                           (percentage of request) above target
```

```
application-service-5fcfdcfd8-8g4bt   542m   286Mi
application-service-5fcfdcfd8-8p8k7   584m   119Mi
```

Two pods at 542m and 584m against a 250m request — genuinely over target, and the autoscaler
responded correctly.

Then the node collapsed:

```
load average: 51.64, 59.59, 46.73        # on 4 CPUs
%Cpu(s): 26.2 us, 57.7 sy, 4.6 id        # 58% system time — pure thrash
PID   COMMAND      %CPU
610   etcd         62.9
111508 kube-apiserver 48.6
```

Container restart counts tell the story:

| Component | Restart attempt |
|---|---|
| kube-controller-manager | 34 |
| kube-scheduler | 32 |
| kube-apiserver | 4 |

`kubectl` began returning `net/http: TLS handshake timeout`, the HPA metric froze, and
`application-service` pods were evicted.

**Attempt 2 — 4 workers.** Generated no load at all:

```
wget: bad address 'application-service:8081'
```

CoreDNS was still starved from attempt 1 and could not resolve the Service name. The HPA sat
at a stale `43%` for five minutes — not a live reading, an artefact of a broken metrics
pipeline.

**Recovery.** Deleted the observability stack, scaled deployments to 1, and restarted the
node container (`docker restart jobtracker-control-plane`), which preserves the containerd
image store so nothing needed re-loading. All 7 pods returned to `1/1 Running`; memory
dropped to **1.0 GiB**, then settled at **1.9 GiB** with HPAs restored and reading live
metrics (5–24%).

### Honest conclusion on 1.6

**Proven:** metrics-server working, HPAs reading live CPU utilisation, and a real scale-out
under real load — `application-service` 1 → 2 replicas with `SuccessfulRescale` naming CPU
utilisation as the reason.

**Not proven:** a sustained climb to `maxReplicas` with the `TARGETS` column moving live. On
4 CPUs there is not enough headroom to run Kafka + PostgreSQL + Redis + three JVMs + an
observability stack + a load generator + a Kubernetes control plane at once. The load
generator competing with the control plane is what broke it — an artefact of the test
environment, not of the manifests.

> **Learning.** A load test that starves the control plane measures nothing. The observer
> and the observed were sharing 4 CPUs. Isolating load generation from the cluster under
> test is not a nicety; without it the results are noise.

---

## Phase 2 — ArgoCD GitOps

### Install

Three images are referenced by `install.yaml`. Dex was skipped entirely (SSO only) and its
Deployment scaled to 0, along with the notifications and applicationset controllers, to
protect memory. The other two were side-loaded as archives, for the IPv6 reason above:

| Image | Archive | On node |
|---|---|---|
| `quay.io/argoproj/argocd:v3.4.5` | 193,763,840 bytes | 194 MB |
| `public.ecr.aws/docker/library/redis:8.2.3-alpine` | 28,016,640 bytes | 27.5 MB |

---

#### Bug 10 — `kubectl apply` cannot install ArgoCD

```
The CustomResourceDefinition "applicationsets.argoproj.io" is invalid:
metadata.annotations: Too long: may not be more than 262144 bytes
```

**Diagnosis.** Client-side `kubectl apply` stores the entire manifest in the
`kubectl.kubernetes.io/last-applied-configuration` annotation. The ApplicationSet CRD is
larger than the 256 KB annotation limit, so the object cannot be written at all.

**Fix.** Server-side apply, which tracks field ownership in managed fields instead of
stuffing a copy into an annotation:

```bash
kubectl apply --server-side=true --force-conflicts -n argocd -f install.yaml
```

Four pods reached `1/1 Running` in **114 seconds**: `application-controller`, `repo-server`,
`server`, `redis`.

> **Learning.** Client-side apply has a hard size ceiling that large CRDs routinely exceed.
> Server-side apply is not merely newer, it is the only thing that works here.

---

#### Bug 11 — ArgoCD and the HPA fought over replica counts

Within a minute of applying the Application, it settled into a permanent flap:

```
02:47:21  Synced   | Healthy
02:48:04  OutOfSync| Progressing
02:49:12  OutOfSync| Healthy
```

The single drifting resource:

```
Deployment/frontend -> OutOfSync
```

**Diagnosis.** Two controllers claimed the same field. Git asked for 1 frontend replica; the
frontend HPA had `minReplicas: 2` and set 2; ArgoCD's `selfHeal` reverted it to 1; the HPA
set it back. Neither is malfunctioning — they were given contradictory authority over
`/spec/replicas`.

**Fix, in two parts, because there were two problems.**

1. ArgoCD should not own replica counts of HPA-managed Deployments:

```yaml
ignoreDifferences:
  - group: apps
    kind: Deployment
    jsonPointers:
      - /spec/replicas
```

2. The overlay was *itself* contradictory — asking for 1 frontend replica while leaving that
   HPA at `minReplicas: 2`. That conflict existed with or without ArgoCD; ArgoCD just made it
   visible. The overlay now patches the frontend HPA to 1/3 like the others.

Result: **23 Synced / 0 OutOfSync, 37 Healthy / 0 Degraded.**

> **Learning.** GitOps forces you to name the owner of every field. Anything mutated at
> runtime — replica counts, injected secrets, admission-webhook defaults — needs an explicit
> `ignoreDifferences` or the reconciler will fight the thing doing the mutating. The
> contradiction had been sitting in the overlay all along; nothing before ArgoCD surfaced it.

---

### The GitOps demonstration

The fix above was itself the demo. A commit was pushed to GitHub and **nothing was applied to
the cluster by hand**:

```
02:51:00  git push origin main       frontend HPA: minReplicas 2, maxReplicas 5
02:54:30  (no kubectl run)           frontend HPA: minReplicas 1, maxReplicas 3
```

**Reconciliation lag: ~3m30s**, consistent with ArgoCD's default 3-minute polling interval.
The ArgoCD UI records the same event independently: `Sync OK ... Succeeded (Thu Jul 23 2026
02:54:26 GMT+0530)`.

The revision it synced is authored by `github-actions[bot]` with the message
`ci: deploy d7f38b4…` — the CD pipeline commits on top of pushes, so ArgoCD is reconciling
against the pipeline's output rather than the hand-written commit directly. That is worth
knowing: in this repo the deployed revision is never quite the commit you pushed.

> **Learning.** The lag is the honest part. GitOps is eventually consistent by design — the
> cluster converges on Git, it does not track it instantly. A demo that hides the interval
> misrepresents how the tool behaves.

---

## Phase 3 — AWS EKS via Terraform (not provisioned)

Terraform v1.15.8. Everything free was run; nothing was applied, so **no AWS resources were
created and nothing was billed**.

```
terraform init      ✅ providers installed (aws, kubernetes, helm, tls, time, cloudinit, null)
terraform validate  ✅ Success! The configuration is valid.
terraform fmt       ✅ (main.tf was unformatted; fixed)
terraform plan      ❌ Error: No valid credential sources found
terraform apply     — not attempted
```

`plan` is the checkpoint that produces a resource count, and it cannot run here:

```
Error: No valid credential sources found
  with provider["registry.terraform.io/hashicorp/aws"],
  on versions.tf line 22, in provider "aws"

Error: failed to refresh cached credentials, no EC2 IMDS role found ...
dial tcp 169.254.169.254:80: A socket operation was attempted to an
unreachable network.
```

`plan` is not an offline operation. It authenticates to AWS and refreshes state to compare
desired against actual, so without credentials it stops before producing a diff. `validate`
*is* offline — it checks syntax, types and references only. The distinction matters when
claiming what has been verified: **valid configuration is not the same as a working plan.**

`.terraform.lock.hcl` was being gitignored, which is backwards — HashiCorp recommends
committing it so provider versions and checksums resolve identically elsewhere. Now tracked.

### What this phase does and does not establish

Established: the configuration parses, type-checks, and its provider requirements resolve.

Not established: that it would successfully provision, that the resource graph is correct,
or that the VPC/IRSA/node-group wiring works. Those need `plan` at minimum and `apply` to be
sure. Cost was the reason to stop — roughly $0.10/hr for the EKS control plane plus ~$0.17/hr
for two t3.large nodes plus NAT gateway, so ~$2–5 for a short session and ~$230/month if left
running.

---

## Summary

| # | Bug | Root cause | Visible in Compose? |
|---|---|---|---|
| 1 | Kafka image gone | Upstream withdrew Bitnami images | Yes |
| 2 | Registry DNS IPv6-only | No IPv6 route from node | No |
| 3 | `kind load` digest error | Multi-platform manifest, partial blobs | No |
| 4 | Broker exits 1 | K8s service-link env vars parsed as config | **No** |
| 5 | Broker never registers | ClusterIP hairpin for KRaft quorum | **No** |
| 6 | JVMs killed at boot | Fixed `initialDelaySeconds` too short | **No** |
| 7 | Dashboard panel empty | Different `job` labels under SD | **No** |
| 8 | HPA scales at idle | CPU requests below idle usage | **No** |
| 9 | Grafana restart loop | Bug 6 not applied to new workloads | **No** |
| 10 | ArgoCD install rejected | CRD exceeds 256 KB annotation limit | **No** |
| 11 | Permanent OutOfSync | ArgoCD and HPA both own `/spec/replicas` | **No** |

Nine of eleven could only appear on Kubernetes.

### Status

| Checkpoint | Status |
|---|---|
| 1.1 Cluster created | ✅ single node, Ready |
| 1.2 Images built and side-loaded | ✅ 4 images |
| 1.3 Full stack deployed | ✅ all pods 1/1, 0 restarts |
| 1.4 App reachable, events flowing | ✅ verified via UI and dashboards |
| 1.5 Self-healing under pod deletion | ✅ replacement in 32s, ready in ~3m |
| 1.6 HPA scale-out under load | ⚠️ partial — 1→2 verified; sustained climb not achievable on this hardware |
| Observability in-cluster | ✅ metrics; ❌ tracing backend (memory) |
| 2. ArgoCD installed and syncing | ✅ 23 Synced / 0 OutOfSync, 37 Healthy |
| 2. GitOps reconciliation from Git | ✅ push at 02:51 → cluster changed 02:54:30, no kubectl |
| 3. Terraform `init` + `validate` | ✅ "Success! The configuration is valid." |
| 3. Terraform `plan` | ❌ needs AWS credentials — not configured |
| 3. EKS provisioned | ❌ not attempted — costs money |

### What can honestly be claimed

> Deployed to Kubernetes (kind) with liveness, readiness and startup probes; verified
> self-healing under pod failure and HPA scale-out under load (1→2 replicas), with
> Prometheus and Grafana running in-cluster and metrics scraped via pod annotations.
> Delivered via ArgoCD GitOps, with a change pushed to Git reconciled into the cluster
> automatically.

What is **not** claimed: production experience, throughput numbers, or a sustained autoscaling
demonstration. Being precise about the boundary is the point of this document.
