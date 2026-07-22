# ArgoCD (GitOps)

ArgoCD watches a path in this repository and reconciles the cluster to match Git — no
manual `kubectl apply` for app rollouts.

`application.yaml` currently targets **`infrastructure/kubernetes/overlays/local`**, because
that is the overlay a Kind cluster can actually run: the prod overlay pulls images from GHCR,
and a Kind node has no pull secret for it, so a local cluster pointed at prod sits
permanently degraded. Switch `path` to the prod overlay when running against EKS, where the
CD pipeline's image-tag commits land.

## Install ArgoCD on the cluster

`kubectl apply` **cannot** install ArgoCD — the ApplicationSet CRD is larger than the 256 KB
`last-applied-configuration` annotation that client-side apply writes, and the API server
rejects it with `metadata.annotations: Too long`. Server-side apply tracks ownership in
managed fields instead:

```bash
kubectl create namespace argocd
kubectl apply --server-side=true --force-conflicts -n argocd \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

On a memory-constrained cluster, drop the components this setup does not use — Dex is SSO
only, and neither notifications nor ApplicationSets are in play:

```bash
kubectl -n argocd scale deploy \
  argocd-dex-server argocd-notifications-controller argocd-applicationset-controller --replicas=0
kubectl -n argocd rollout status deployment/argocd-server
```

## Register the app

`repoURL` is already set to this repository; change it if you forked.

```bash
kubectl apply -f application.yaml
kubectl -n argocd get application jobtracker -w
```

**Push before you apply.** ArgoCD syncs from GitHub, not your working tree, and with
`selfHeal` on it will revert the cluster to whatever is on `main` — including config you
fixed locally but never pushed.

## Access the UI

```bash
kubectl -n argocd port-forward svc/argocd-server 8090:443
# https://localhost:8090 — accept the self-signed cert
# user: admin  — password:
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d; echo
```

## Replica counts are owned by the cluster, not Git

`ignoreDifferences` excludes `/spec/replicas` on Deployments. Without it ArgoCD and the
HorizontalPodAutoscaler fight permanently: the HPA scales up, `selfHeal` reverts to the count
in Git, the HPA scales up again, and the app never leaves `OutOfSync`.

Anything mutated at runtime — replica counts, injected secrets, admission-webhook defaults —
needs the same treatment.

```bash
# which resource is drifting?
kubectl -n argocd get application jobtracker \
  -o jsonpath='{range .status.resources[?(@.status=="OutOfSync")]}{.kind}/{.name}{"\n"}{end}'
```

## Behaviour

With `automated.selfHeal` and `prune` on, drift is corrected automatically and removed
resources are pruned. A pushed commit flows: CI → images → tag bump commit → ArgoCD sync →
rollout.

Reconciliation is **not instant** — the default polling interval is 3 minutes (measured here:
a push at 02:51 changed the cluster at 02:54:30). Hit **Refresh** in the UI to force a check.
