# Terraform — AWS EKS for JobTracker

Provisions a production-style Kubernetes platform on AWS: a 3-AZ VPC, an EKS
control plane, a managed node group, and core add-ons (CoreDNS, kube-proxy,
VPC-CNI, EBS CSI). IRSA is enabled so add-ons and workloads can assume IAM roles.

## ⚠️ Cost warning

This creates **billable** AWS resources. Rough on-demand estimate (us-east-1):

| Resource | ~Cost |
|---|---|
| EKS control plane | ~$0.10 / hour (~$73 / month) |
| 2 × t3.large nodes | ~$0.166 / hour (~$120 / month) |
| NAT gateway + EBS + ELB | ~$40+ / month |

**Always run `terraform destroy` when you are done to stop charges.**

## Prerequisites

- Terraform >= 1.6, AWS CLI v2, `kubectl`
- AWS credentials with permission to create VPC/EKS/IAM (`aws configure` or SSO)

## Deploy

```bash
cd infrastructure/terraform
cp terraform.tfvars.example terraform.tfvars   # edit as needed

terraform init
terraform plan
terraform apply            # ~15-20 min to create the cluster

# Point kubectl at the cluster (command is also a terraform output):
aws eks update-kubeconfig --region us-east-1 --name jobtracker

# Deploy the app via Kustomize (or let ArgoCD do it — see infrastructure/argocd):
kubectl apply -k ../kubernetes/overlays/prod
```

## Tear down

```bash
kubectl delete -k ../kubernetes/overlays/prod   # remove LoadBalancers first
terraform destroy
```

Deleting Kubernetes `Service type=LoadBalancer` / Ingress resources before
`terraform destroy` avoids orphaned ELBs that block VPC deletion.
