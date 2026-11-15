# Gallbladder Meal and Symptom Tracker

A single-user web app for logging meals with their fat content (and, later, symptom episodes)
so you can see which meals came before pain.

## Why I built this

On a weekend trip with friends, I felt nauseous all day and had pain in the upper right of my abdomen that
I tried to ignore. Early the next morning it was bad enough that I called an ambulance, and I was diagnosed
with cholecystitis, an inflamed gallbladder. My doctor told me to watch what I ate, especially fat. I found
that hard to do day to day, and my symptoms came back because of it.

So I built this tracker for myself, to keep an honest record of what I eat and when the pain comes, and for
anyone else who finds it hard to keep track of their diet with a gallbladder problem.

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

Everything runs on free plans: the app on [Render](https://render.com) and the database on [Neon](https://neon.tech),
whose free Postgres doesn't expire (0.5 GB, far more than this app needs). `render.yaml` describes the app.

**1. Create the database on Neon**

1. Sign up at https://console.neon.tech and create a project. Pick the **AWS Asia Pacific (Singapore)** region,
   next to the app.
2. On the project dashboard click **Connect**, turn **Connection pooling off**, and copy the connection string.
   It looks like `postgresql://neondb_owner:...@ep-....ap-southeast-1.aws.neon.tech/neondb?sslmode=require`.
   Treat it like a password: it gives full access to your records.

**2. Create the app on Render**

1. In the [Render dashboard](https://dashboard.render.com), choose **New → Blueprint** and pick this repo.
2. When asked, paste the Neon string into `DATABASE_URL`, enter a long `APP_PASSWORD` (the app is on the open
   internet), and optionally a `USDA_API_KEY`.
3. Click **Apply**. The first build takes a few minutes; then open the `onrender.com` address and log in as `me`.
   The tables are created on first start.

Every push to `main` redeploys. Two things to know about the free plans:

- The app sleeps after 15 minutes without visits, so the first page after a break takes about a minute.
- Neon's free plan keeps only a short restore history, so download the CSV from Settings now and then as your own copy.
