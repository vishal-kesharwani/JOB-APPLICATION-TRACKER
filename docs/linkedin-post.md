# Publishing this project on LinkedIn

## Before you post

1. **Make the repo presentable** — README renders with screenshots, CI badge green, repo is public.
2. **Pin the repo** on your GitHub profile (Customize your pins).
3. **Record a 60–90s screen capture** (optional but doubles engagement): create an application in the UI → flip it to Interview Scheduled → switch to Reminders and show the reminder appear → Dashboard funnel updating → Grafana trace. No narration needed; add text captions.

## What format to use

LinkedIn's ranking favours, roughly in this order: **native video > image carousel (PDF) > multiple images > single image > text-only > post with an external link**.

Links in the post body get suppressed. So:

> Put the GitHub link in the **first comment**, and add "link in comments" in the post.

Best combo for this project: **4–5 image carousel** (UI dashboard, applications page, reminders, Grafana, architecture diagram) or the short video.

## Draft post — Option A (build story, recommended)

> I spent the last few weeks building something more ambitious than a CRUD app: a job application tracker designed as a real distributed system.
>
> The interesting part isn't the tracking. It's that the three backend services never call each other.
>
> When you create or update an application, the write service persists it and publishes a Kafka event. Two independent services react on their own — one schedules follow-up and interview reminders, the other maintains an analytics read-model in Redis showing conversion rates through the pipeline.
>
> That decoupling is the whole point. Each service owns its own data. The analytics view can be rebuilt from scratch just by replaying Kafka. Adding a fourth consumer wouldn't require touching a single existing service.
>
> What's in it:
> → Java 21 / Spring Boot microservices communicating over Apache Kafka
> → PostgreSQL as the single source of truth, Redis as a derived read-model
> → React + Tailwind frontend behind an nginx reverse proxy
> → Full observability — metrics, logs, and distributed tracing in Grafana (Prometheus / Loki / Tempo via OpenTelemetry)
> → Kubernetes manifests with autoscaling, Terraform-provisioned AWS EKS
> → GitHub Actions CI/CD with ArgoCD GitOps delivery
>
> The most valuable thing I learned wasn't a framework. It was how much design thinking goes into deciding what a service should NOT know about.
>
> It's open source (MIT) — link in the comments. Feedback genuinely welcome.
>
> #Java #SpringBoot #Kafka #Microservices #Kubernetes #DevOps #Observability #SoftwareEngineering

**First comment:**
> GitHub: https://github.com/vishal-kesharwani/JOB-APPLICATION-TRACKER
> Happy to answer anything about the architecture or the tradeoffs.

## Draft post — Option B (short, punchy)

> Built a job application tracker as a production-grade distributed system.
>
> Three Spring Boot services that never call each other — they communicate purely through Kafka events. One owns PostgreSQL, one schedules reminders, one keeps an analytics read-model in Redis that can be rebuilt by replaying the event log.
>
> Wrapped in full observability (Prometheus + Loki + Tempo + Grafana), deployed to Kubernetes, provisioned with Terraform, delivered via ArgoCD GitOps.
>
> Java 21 · Spring Boot · Kafka · PostgreSQL · Redis · React · Docker · Kubernetes · Terraform · OpenTelemetry
>
> Open source, link in comments 👇
>
> #Java #Kafka #Kubernetes #Microservices #DevOps

## Draft post — Option C (problem/solution hook)

> "Just use one database and a few REST calls."
>
> That's the easy way to build a job application tracker. I deliberately didn't.
>
> Instead: three independent services, zero direct calls between them, all coordination through Kafka events. Why? Because that's how systems actually scale — and because I wanted to feel the tradeoffs rather than read about them.
>
> The payoff: my analytics service can be wiped and rebuilt entirely by replaying the event log. My notification service can go down without blocking a single write. Adding a new consumer requires changing nothing that already exists.
>
> The cost: eventual consistency, more moving parts, and genuinely needing distributed tracing to debug anything.
>
> Both sides of that trade are worth understanding before an interview asks you about it.
>
> Full stack: Java 21, Spring Boot, Kafka, PostgreSQL, Redis, React, Kubernetes, Terraform, ArgoCD, and the Grafana LGTM observability stack.
>
> Open source — link in comments.
>
> #SoftwareEngineering #Java #Kafka #DistributedSystems #Kubernetes

---

## Also do these (they outlast the post)

**1. Add to your LinkedIn Projects section**
Profile → Add section → Additional → Projects.
- Name: `Job Application Tracker — Cloud-Native Microservices Platform`
- Link the GitHub repo
- Description: 2–3 lines from the résumé bullets in `docs/resume-bullets.md`

**2. Add it to Featured**
Profile → Add section → Featured → Link. Pin the repo or the post so it sits at the top of your profile permanently.

**3. Update your headline** if you're job hunting, e.g.
`Backend Engineer · Java · Spring Boot · Kafka · Kubernetes` — recruiters search on these keywords.

## Posting mechanics

- **Best time:** Tue–Thu, 9–11am your audience's time. Avoid weekends.
- **First 60–90 minutes matter most** — reply to every comment quickly; engagement early drives reach.
- **5 hashtags max**, specific beats generic (`#Kafka` over `#Technology`).
- **Don't edit the post** for the first hour — editing can suppress reach.
- **Tag thoughtfully.** Only tag people/companies genuinely relevant; mass-tagging hurts you.
- **Reshare in 3–4 weeks** with a different angle ("what I learned", or a new feature) rather than reposting the same thing.

## A note on honesty

Describe what the project *demonstrates*, not invented scale. Don't claim production traffic, user counts, or uptime figures it never had — experienced engineers spot it instantly and it's the fastest way to lose credibility in an interview. "Built to demonstrate X" is respected; "handles 1M requests/day" on a personal project is not.
