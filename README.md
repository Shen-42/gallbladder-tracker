# Gallbladder Meal and Symptom Tracker

A single-user web app for logging meals with their fat content (and, later, symptom episodes)
so you can see which meals came before pain.

> **Not medical advice.** This app keeps a record for you and your doctor. It does not diagnose,
> recommend treatment, or tell you what to eat. Your fat limit should come from your doctor or dietitian.

> (i hope u feel better soon! -amira)
## Run

```sh
docker compose up --build
```

Then open http://localhost:8080. Data lives in the `pgdata` Docker volume, not in the repo.
Set `TZ` in `compose.yaml` to your time zone.

## Test

```sh
./mvnw test
```

Tests use an in-memory H2 database.
