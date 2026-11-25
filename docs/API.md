# FitFlex REST API

Base URL: `http://localhost:8080/api`. Every request and response body is JSON, except the file upload
(multipart) and download (binary).

**Authentication:** send `Authorization: Bearer <token>` with the token returned by a login/register
endpoint. Tokens are valid for 24 hours.

**Roles:** `Public` = no token needed · `USER` = member account · `TRAINER` = trainer account ·
`Any` = either role, logged in.

Ready-made requests for every endpoint are in [`FitFlex.postman_collection.json`](FitFlex.postman_collection.json).

---

## Error format

Every error has the same shape (built by `GlobalExceptionHandler` / `RestAuthErrorHandler`):

```json
{
  "timestamp": "2026-11-22T15:00:38.45",
  "status": 400,
  "message": "Validation failed",
  "errors": { "email": "Enter a valid email address", "password": "Password must be at least 8 characters and contain a number" }
}
```

| Status | When |
|--------|------|
| 400 | Validation failed, malformed JSON, bad parameter, business rule (e.g. booking outside availability) |
| 401 | Missing/invalid/expired token, or wrong email/password |
| 403 | Logged in, but the role or ownership is wrong (e.g. a trainer calling a USER endpoint) |
| 404 | Record not found (trainer, session, file) or unknown URL |
| 409 | Conflict: duplicate email, booking clash, group session full, already joined, overlapping slot |
| 413 | Upload larger than 50 MB |

---

## Auth and profiles

| Method | Path | Role | Request body | Response |
|--------|------|------|--------------|----------|
| POST | `/user/register` | Public | `{fullName, email, password, phone?, age, gender, heightCm, weightKg, fitnessGoal, fitnessLevel, activityLevel}` | 201 `AuthResponse` |
| POST | `/user/login` | Public | `{email, password}` | 200 `AuthResponse` (401 if the account is a trainer) |
| GET | `/user/profile` | USER | | `UserProfile` |
| PUT | `/user/profile` | USER | `{fullName, phone?, age?, gender?, heightCm?, weightKg?, fitnessGoal?, fitnessLevel?, activityLevel?}` | `UserProfile` |
| POST | `/trainer/add` | Public | `{fullName, email, password, phone?, specialization, experienceYears, certifications?, bio?, pricePerSession, profileImageUrl?}` | 201 `AuthResponse` |
| POST | `/trainer/login` | Public | `{email, password}` | 200 `AuthResponse` (401 if the account is a user) |

`AuthResponse` = `{token, role, name, id, trainerId, email}` (`id` is the User id; `trainerId` only for trainers).
Enums: `gender` MALE/FEMALE/OTHER · `fitnessGoal` WEIGHT_LOSS/MUSCLE_GAIN/MAINTENANCE ·
`fitnessLevel` BEGINNER/INTERMEDIATE/ADVANCED · `activityLevel` SEDENTARY/LIGHT/MODERATE/ACTIVE/VERY_ACTIVE.
Password rule: at least 8 characters including a digit.

## Trainers

| Method | Path | Role | Request | Response |
|--------|------|------|---------|----------|
| GET | `/trainers?specialization=&minExperience=&maxPrice=&search=` | Public | all filters optional; `search` matches the name | `Trainer[]` sorted by rating |
| GET | `/trainers/{id}` | Public | | `Trainer` with `availability` |
| GET | `/trainer/profile` | TRAINER | | `Trainer` (includes email and phone) |
| PUT | `/trainer/profile` | TRAINER | `{fullName, phone?, specialization, experienceYears, certifications?, bio?, pricePerSession, profileImageUrl?}` | `Trainer` |
| GET | `/trainer/availability` | TRAINER | | `Slot[]` (Monday first) |
| POST | `/trainer/availability` | TRAINER | `{dayOfWeek: "MONDAY", startTime: "07:00", endTime: "10:00"}` | 201 `Slot` (409 if it overlaps a slot) |
| DELETE | `/trainer/availability/{slotId}` | TRAINER | | 204 |
| GET | `/trainer/clients` | TRAINER | | `UserProfile[]` (users who booked or joined a group class) |

`Trainer` = `{id, userId, fullName, email, phone, specialization, experienceYears, certifications, bio, pricePerSession, rating, profileImageUrl, verified, availability: Slot[]}`.
`Slot` = `{id, dayOfWeek, startTime, endTime}`.

## Sessions

| Method | Path | Role | Request | Response |
|--------|------|------|---------|----------|
| POST | `/sessions/book` | USER | `{trainerId, scheduledAt: "2026-12-01T08:00", durationMinutes? (30-120, default 60), title?, description?}` | 201 `Session` with status REQUESTED |
| POST | `/sessions/group` | TRAINER | `{title, description?, scheduledAt, durationMinutes (15-180), maxParticipants (2-100)}` | 201 `Session` (CONFIRMED, with link) |
| GET | `/sessions/group` | Any | | upcoming group sessions that can be joined |
| POST | `/sessions/{id}/join` | USER | | `Session` (409 if full or already joined) |
| GET | `/sessions/my?filter=upcoming\|past` | Any | filter optional | the caller's sessions |
| PUT | `/sessions/{id}/status` | Any | `{status}` | `Session` |

Booking rules: in the future; start and end on the same day; completely inside one of the trainer's
weekly slots (400 otherwise); no overlap with the trainer's REQUESTED/CONFIRMED sessions (409).

Status changes:

| Who | Allowed |
|-----|---------|
| TRAINER (own sessions) | REQUESTED → CONFIRMED (creates `https://meet.jit.si/FitFlex-<uuid>`), REQUESTED/CONFIRMED → CANCELLED, CONFIRMED → COMPLETED (only after the start time) |
| USER | CANCELLED only: cancels their own one-on-one, or leaves a group session they joined |

`Session` = `{id, sessionType, title, description, scheduledAt, durationMinutes, status, meetingLink, trainerId, trainerName, trainerSpecialization, userId, userName, userEmail, participantCount, maxParticipants, joined}`.
`meetingLink` is only filled in for the trainer, the booker and participants, and only while CONFIRMED.

## Workout plans

| Method | Path | Role | Request | Response |
|--------|------|------|---------|----------|
| POST | `/workout-plans` | TRAINER | `{userId, title, goal?, durationWeeks, exercises: [{name, sets, reps, restSeconds?, dayNumber (1-7), notes?}]}` | 201 `WorkoutPlan` (403 if the user isn't a client) |
| GET | `/workout-plans/my` | Any | | USER: plans assigned to me; TRAINER: plans I created |

## Trainer resources (files)

| Method | Path | Role | Request | Response |
|--------|------|------|---------|----------|
| POST | `/resources` | TRAINER | multipart: `file` (mp4/pdf/docx/jpg/png, max 50 MB), `title?`, `type?` (VIDEO/DOCUMENT/PLAN), `userId?` (share with one client) | 201 `Resource` |
| GET | `/resources/my` | Any | | TRAINER: my uploads; USER: files shared with me by my trainers |
| GET | `/resources/{id}/download` | Any | | the file, as an attachment (owner trainer or a client it is shared with; otherwise 403) |
| DELETE | `/resources/{id}` | TRAINER | | 204 (owner only) |

## Nutrition

| Method | Path | Role | Request | Response |
|--------|------|------|---------|----------|
| POST | `/diet/generate` | USER | `{goal? (defaults to profile goal), dietType: vegetarian\|vegan\|eggetarian\|non-veg, mealsPerDay? (3-5)}` | 201 `DietPlan` |
| GET | `/diet/my` | USER | | `DietPlan[]` newest first |
| GET | `/diet/search-food?query=paneer` | USER | at least 2 characters | `{source, message, items: [{name, servingSize, calories, protein, carbs, fat}]}` |

`DietPlan` = `{id, goal, dietType, mealsPerDay, bmr, tdee, targetCalories, source (SPOONACULAR/LOCAL_FALLBACK), note, createdAt, mealGroups: [{mealType, meals: [{mealType, name, servingSize, calories, protein, carbs, fat, imageUrl, sourceUrl, readyInMinutes, origin}]}], totals: {calories, protein, carbs, fat}}`.
Maths: BMR (Mifflin-St Jeor) → TDEE = BMR × activity factor → target = TDEE − 500 / + 300 / + 0 (minimum 1200).

## Gyms

| Method | Path | Role | Request | Response |
|--------|------|------|---------|----------|
| GET | `/gym/nearby?lat=21.1458&lng=79.0882&radius=3000` | Public | radius 200-20000 m, default 3000 | `{source (GOOGLE_PLACES/OPENSTREETMAP/SAVED/NONE), message, gyms: [{name, address, lat, lng, rating, distanceKm, directionsUrl}]}` nearest first |

Gym search order: Google Places (if a key is set) → Overpass (4 mirrors queried in parallel, each retried once, 30 s deadline) → Nominatim. If nothing is within the radius, the nearest 10 within 10 km are returned (with a `message`). If every live provider fails, the last results saved for that spot (`backend/data/gym-cache.json`) are returned with `source: SAVED` and their date.

## Progress

| Method | Path | Role | Request | Response |
|--------|------|------|---------|----------|
| POST | `/progress` | USER | `{date? (default today, not in the future), weightKg?, workoutMinutes?, caloriesBurned?, notes?}` (weight or minutes required) | 201 `ProgressEntry` |
| GET | `/progress/my?from=2026-11-01&to=2026-11-30` | USER | dates optional | `ProgressEntry[]` oldest first |
| GET | `/progress/summary` | USER | | `{currentStreak, totalWorkouts, workoutsThisWeek, totalWorkoutMinutes, totalCaloriesBurned, startWeightKg, currentWeightKg, weightChangeKg, poseSessions, totalPoseReps, averageFormAccuracy}` |
| POST | `/progress/pose` | USER | `{exercise, reps, correctFormPercent (0-100), durationSeconds, caloriesBurned?, notes?}` | 201 `ProgressEntry` with source POSE_DETECTION |

`ProgressEntry` = `{id, date, weightKg, workoutMinutes, caloriesBurned, notes, source, exercise, reps, correctFormPercent}`.

## Contact and dashboards

| Method | Path | Role | Request | Response |
|--------|------|------|---------|----------|
| POST | `/contact` | Public | `{name, email, subject?, message (10-2000 chars)}` | 201 `{message}` |
| GET | `/dashboard/user` | USER | | `{fullName, summary, upcomingSessions (max 5), latestDietPlan, workoutPlans, resources}` |
| GET | `/dashboard/trainer` | TRAINER | | `{profile, upcomingSessionCount, pendingRequestCount, clientCount, resourceCount, upcomingSessions, pendingRequests, clients}` |
