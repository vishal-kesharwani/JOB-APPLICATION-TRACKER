# ArgoCD (GitOps)

The CD pipeline pushes images to GHCR and commits new image tags into
`infrastructure/kubernetes/overlays/prod/kustomization.yaml`. ArgoCD watches that
path and reconciles the cluster to match Git — no manual `kubectl apply` for app
rollouts.

## Install ArgoCD on the cluster

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

## Register the app

Edit `application.yaml` and replace `GHCR_OWNER` with your GitHub org/user, then:

```bash
kubectl apply -f application.yaml
```

## Access the UI

```bash
kubectl -n argocd port-forward svc/argocd-server 8080:443
# user: admin  — password:
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d; echo
```

With `automated.selfHeal` + `prune` on, any drift is corrected automatically and
removed resources are pruned. A pushed commit to `main` flows: CI → images →
tag bump commit → ArgoCD sync → rollout.
