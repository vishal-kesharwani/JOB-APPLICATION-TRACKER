# Screenshots

Save the images here with these exact filenames so the links in the root `README.md` resolve:

| Filename | What to capture |
|---|---|
| `ui-applications.png` | Applications page — the table listing applications with status badges |
| `ui-new-application.png` | The "New application" modal, filled in |
| `ui-dashboard.png` | Dashboard page — funnel, conversion rates, breakdown charts |
| `ui-reminders.png` | Reminders page — stat cards + a triggered reminder card |
| `grafana-dashboard.png` | Grafana "JobTracker — Platform Overview", full page with data |
| `prometheus-targets.png` | Prometheus `/targets` showing all scrape targets UP |

Tips for good screenshots:

- Run `./scripts/demo.sh` (or `./scripts/load-test.sh`) first so the charts have data.
- Set Grafana's time range to **Last 15 minutes** — denser lines look better than mostly-flat ones.
- Use a maximised browser window and capture just the page content, not the whole desktop.
- PNG, roughly 1600px wide, keeps the README crisp without bloating the repo.
