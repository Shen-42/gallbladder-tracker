# Gallbladder Meal and Symptom Tracker

A single-user web app for logging meals with their fat content (and, later, symptom episodes)
so you can see which meals came before pain.

**[Try the demo](https://shen-42.github.io/gallbladder-tracker/)**: the app's pages filled with made-up data.
It is read-only; to log your own meals, run it yourself with Docker (below).

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

## Demo

The demo in `docs/` is a static copy of the app's pages, captured from a local run with made-up data and served by
GitHub Pages. Forms in it don't save.

## Test

```sh
./mvnw test
```

Tests use an in-memory H2 database.
