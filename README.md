# Gallbladder Meal and Symptom Tracker

A single-user web app for logging meals with their fat content (and, later, symptom episodes)
so you can see which meals came before pain.

> **Not medical advice.** This app keeps a record for you and your doctor. It does not diagnose,
> recommend treatment, or tell you what to eat. Your fat limit should come from your doctor or dietitian.

## Run

```sh
APP_PASSWORD=choose-a-password docker compose up --build
```

Then open http://localhost:8080 and log in as `me` with that password (set `APP_USERNAME` to change the name).
"Stay logged in" keeps a device logged in for 30 days; changing the password logs every device out. Data lives in the `pgdata` Docker volume, not in the repo.
Set `TZ` in `compose.yaml` to your time zone.

### Food search

Typing a food you haven't logged before searches [USDA FoodData Central](https://fdc.nal.usda.gov/).
Without a key the app uses USDA's `DEMO_KEY`, which allows only a few searches an hour. Get a free key at
https://fdc.nal.usda.gov/api-key-signup and start the app with it:

```sh
USDA_API_KEY=your-key docker compose up --build
```

If the search fails, the form says so and you enter the fat per 100 g yourself.

## Your data

- **Settings → Download CSV** gives a zip with `meals.csv` (one row per food eaten) and `episodes.csv`.
- **Summary → Report for appointments** makes a one-page PDF for a date range: the counts, the chart, and every episode.

## Test

```sh
./mvnw test
```

Tests use an in-memory H2 database.

## Deploy to Render

`render.yaml` describes the app and its Postgres database, both in Singapore, on Render's free plans. Two limits:

- The app sleeps after 15 minutes without visits, so the first page after a break takes about a minute.
- **Render deletes a free database 30 days after it is created**, with everything in it. Download the CSV
  (Settings) every week, and before day 30 either upgrade the database in the Render dashboard or move to
  another free Postgres host.

1. Push this repo to GitHub.
2. In the [Render dashboard](https://dashboard.render.com), choose **New → Blueprint** and pick the repo.
3. When asked, enter `APP_PASSWORD` (use a long one: the app is on the open internet) and, optionally, `USDA_API_KEY`.
4. Click **Apply**. The first build takes a few minutes; then open the `onrender.com` address and log in as `me`.

Every push to `main` redeploys. Free databases have no backups, so the CSV download is your only copy.
