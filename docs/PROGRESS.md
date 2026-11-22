# FitFlex: Build Progress Log

One short entry per phase: what was built, how it was verified, and any decisions made where
the spec was ambiguous.

---

## Phase 0: Environment check + scaffold (2026-11-22)

**Environment detected (Windows 11, PowerShell)**

| Tool   | Found                         | Notes                                                                     |
| ------ | ----------------------------- | ------------------------------------------------------------------------- |
| Java   | Temurin JDK 17.0.21           | Meets the 17+ requirement                                                 |
| Maven  | not installed                 | Not needed: the project uses the Maven wrapper (`mvnw.cmd`)                |
| MySQL  | MySQL Server 8.0, service `MySQL80` running | H2 profile also provided                                    |
| Python | 3.14.7 (default), 3.12.15 (via uv) | MediaPipe has no 3.14 wheels; pose module will use a separate venv (Phase 6) |
| Git    | 2.55                          |                                                                           |
| Node   | installed                     | Not required by the project                                               |
| curl   | `curl.exe` (Windows built-in) | Used to generate the Spring Boot project and test endpoints               |

**Built**

- Full folder structure from Section 2 (`backend/`, `frontend/`, `pose-detection/`, `docs/`), with
  `.gitkeep` files so empty folders are tracked
- `.gitignore`: ignores `.env`, `frontend/js/config.js`, `backend/target/`, `backend/uploads/*`,
  Python venvs/caches, IDE files
- `README.md` skeleton and this progress log
- `git init` + first commit

**Decisions**

- The project root is this folder itself (the spec's `fitflex/` top-level folder = `Fitflex_project/`).
- Python version for pose detection: the spec asks for 3.10/3.11. Neither is installed, but `uv` is,
  so Phase 6 will create the venv with `uv` (3.11 if it installs cleanly, otherwise 3.12) and verify
  the MediaPipe import before relying on it.

---

## Phase 1: Backend foundation (2026-11-22)

**Built**

- Spring Boot **4.2.0** project generated from start.spring.io (Java 17, Maven wrapper 3.3.4 /
  Maven 3.10.1). Dependencies: Web MVC, Data JPA, Security, Validation, MySQL driver, H2 + H2 console.
- 10 enums: `Role`, `Gender`, `FitnessGoal`, `FitnessLevel`, `ActivityLevel` (carries the TDEE
  multiplier), `SessionType`, `SessionStatus`, `ResourceType`, `DietSource`, `ProgressSource`.
- 10 entities with plain getters/setters (no Lombok): `User`, `Trainer`, `AvailabilitySlot`,
  `TrainingSession`, `WorkoutPlan`, `WorkoutExercise`, `TrainerResource`, `DietPlan`,
  `ProgressEntry`, `ContactMessage`.
- 9 Spring Data repositories, including the trainer search query, the "sessions for a user" query
  and the "clients of a trainer" queries.
- `application.properties` (everything from env vars / `backend/.env`), `application-h2.properties`,
  `backend/.env.example`.

**Verified**

- `mvnw.cmd test`: context-load test passes on H2.
- `mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=h2` starts in ~7 s and creates all 11 tables
  (10 entities + the `session_participants` join table). Requests currently get 401 from Spring
  Security's default config; real security comes in Phase 2.
- MySQL run not tested yet: it needs your MySQL root password in `backend/.env`.

**Decisions**

- Table names are plural (`users`, `trainers` ...) because `user` is a reserved word in H2/MySQL.
- `ProgressEntry.date` maps to column `entry_date` (`date` is also reserved).
- `spring.jpa.open-in-view=false`: services convert entities to DTOs inside a transaction, so lazy
  relations never leak into controllers.
- H2 profile uses `ddl-auto=create-drop` (fresh in-memory DB every run); MySQL uses `update` as specified.
- `TrainerResource` has two extra fields, `contentType` and `sizeBytes`, used when downloading.
- `DietPlan` stores `mealsPerDay` too, so the history list can show it.
- `JWT_SECRET` has no hardcoded default: if it is empty, a random key is generated at startup
  (implemented in Phase 2).

---

## Phase 2: Security + auth (2026-11-22)

**Built**

- `security/`: `JwtUtil` (jjwt **0.13.0**, HS256, 24 h expiry), `JwtAuthFilter` (reads
  `Authorization: Bearer`), `CustomUserDetailsService`, `UserPrincipal` (id + email + role of the
  logged-in user), `RestAuthErrorHandler` (JSON 401/403 from inside the filter chain).
- `config/SecurityConfig`: stateless, CSRF off, BCrypt, URL rules by role (public: register/login,
  `GET /api/trainers/**`, `POST /api/contact`, `GET /api/gym/nearby`, H2 console). `@EnableMethodSecurity` on.
- `config/CorsConfig`: origins from `CORS_ORIGINS` (default Live Server 5500 on localhost and 127.0.0.1).
- `exception/`: `GlobalExceptionHandler` + `ResourceNotFound`/`BadRequest`/`Conflict`/`Unauthorized`/`Forbidden`
  exceptions, all returning `{timestamp, status, message, errors}`.
- `AuthService` (register user/trainer, login per portal), `UserService` (profile get/update),
  `UserController` (`/api/user/register|login|profile`), `TrainerController` (`/api/trainer/add|login`).
- `config/DataSeeder`: demo user, 5 trainers with weekly availability, 5 sessions
  (confirmed/requested/completed one-on-ones + 2 group sessions), a 3-day workout plan,
  28 days of progress (manual + pose detection entries).

**Verified with curl (H2 profile)**: login returns a token; profile works with the token; 401
without/with a bad token; 401 on wrong password; 401 when a trainer uses the user portal; 403 when
a trainer calls a USER endpoint; 400 with per-field errors; 409 on duplicate email (case-insensitive);
400 on invalid enum; CORS preflight allowed for `http://127.0.0.1:5500`, rejected for other origins.

**Decisions**

- DTOs are Java **records** (immutable, Java writes the accessors). Entities stay plain classes
  with hand-written getters/setters, as requested.
- `AuthResponse` = `{token, role, name, id, trainerId, email}`: `id` is the User id, and `trainerId` is
  added so the trainer dashboard knows its profile id.
- Login is portal-specific: a trainer account cannot log in through `/api/user/login` and vice versa.
- Emails are stored lower-case and compared case-insensitively.
- `JWT_SECRET`, when set, must be at least 32 characters (HS256 requirement); startup fails with a clear message otherwise.
- New trainers start with `rating = null` (shown as "New") and `verified = false`; seeded trainers are verified.
- Added a root `.gitattributes` to normalise line endings.

---

## Phase 3: Core features (2026-11-22)

**Built**

- **Trainers**: public `GET /api/trainers` (filters: specialization, minExperience, maxPrice, search
  by name) and `GET /api/trainers/{id}` with availability; trainer's own `GET/PUT /api/trainer/profile`,
  `GET/POST /api/trainer/availability`, `DELETE /api/trainer/availability/{id}`, `GET /api/trainer/clients`.
- **Sessions** (`SessionService`): `POST /book` (future time, inside a weekly slot, same day, no
  overlap with the trainer's REQUESTED/CONFIRMED sessions), `POST /group`, `GET /group` (open group
  classes), `POST /{id}/join` (confirmed, upcoming, not full, not already joined),
  `GET /my?filter=upcoming|past`, `PUT /{id}/status`. Confirming creates
  `https://meet.jit.si/FitFlex-<uuid>`.
- **Workout plans**: `POST /api/workout-plans` (only for the trainer's own clients; nested
  validation of exercise rows), `GET /api/workout-plans/my` for both roles.
- **Resources**: multipart upload (mp4/pdf/docx/jpg/png, 50 MB, file names sanitised, stored under
  a UUID name in `uploads/`), `GET /my`, permission-checked `GET /{id}/download`, `DELETE /{id}`.
- **Progress**: `POST /api/progress`, `POST /api/progress/pose`, `GET /my?from=&to=`, `GET /summary`
  (streak, workouts, minutes, calories, weight change, pose sessions/reps, average form %).
- **Contact** (`POST /api/contact`, public) and **dashboards** (`GET /api/dashboard/user`,
  `GET /api/dashboard/trainer`).

**Verified**: about 60 curl checks against the H2 profile (script in my scratchpad), covering
happy paths plus 400/403/404/409 cases: booking clash, outside availability, past time, wrong role,
other trainer's session/slot/file, full group, duplicate join, disallowed file type, non-client
download, invalid contact form. Found and fixed one bug: availability was sorted alphabetically
(FRIDAY before MONDAY) because `dayOfWeek` is stored as text, so it is now sorted in Java with
`AvailabilitySlot.WEEK_ORDER`.

**Decisions**

- "Client of a trainer" = has a non-cancelled one-on-one with them, or joined one of their group sessions.
- Group sessions are CONFIRMED (with a link) as soon as the trainer creates them, and they don't need
  to fit inside availability slots.
- A USER "cancelling" a group session means leaving it (they are removed from the participants);
  the class itself stays on.
- A trainer can mark a session COMPLETED only once it has started.
- The meeting link is only sent to the trainer, the booking user and participants, and only while
  the session is CONFIRMED.
- One-on-one duration defaults to 60 min (30–120 allowed); group sessions can be 15–180 min.
- Logging today's weight also updates the profile weight, so the next diet plan uses current data.
- Pose uploads without calories get an estimate of 6 kcal per minute.
- Extra endpoints beyond the spec: `GET /api/sessions/group`, `DELETE /api/resources/{id}`,
  `DELETE /api/trainer/availability/{id}`.

---

## Phase 4: External APIs (2026-11-22)

**Built**

- **Nutrition** (`NutritionService`, `SpoonacularClient`, `LocalMealDataset`, `NutritionController`):
  - Mifflin-St Jeor BMR → TDEE (activity factor) → target calories (−500 / +300 / 0, with a 1200 kcal floor).
  - Spoonacular `mealplanner/generate` plus `recipes/informationBulk` for per-recipe macros. The key
    is sent in the `x-api-key` header from the backend only.
  - Fallback: `resources/fallback/meals.json` (41 Indian meals: veg, vegan, eggetarian and non-veg;
    each meal's macros were checked against its calories) with portion scaling (0.5×–2× in 0.25 steps).
  - `POST /api/diet/generate`, `GET /api/diet/my`, `GET /api/diet/search-food?query=` (Spoonacular
    ingredient search, falling back to `fallback/foods.json`, 40 foods per 100 g, plus the meal list).
- **Gyms** (`GymService`, `GooglePlacesClient`, `OverpassClient`, `NominatimClient`, `GymController`):
  Google Places API (New) Nearby Search when `GOOGLE_MAPS_API_KEY` is set → OSM Overpass
  (`leisure=fitness_centre`) → OSM Nominatim name search → empty list with a message. Haversine
  distance, nearest first, max 30 results, 10-minute in-memory cache.
- `config/RestClientConfig`: shared `RestClient` with 5 s connect / 10 s read timeouts and an
  identifying User-Agent.

**Verified (no API keys)**: BMR for the demo user = 1733.75 (hand calculation 10·75.5 + 6.25·175 − 5·24 + 5),
TDEE 2687.3, target 2187 for weight loss / 2987 for muscle gain. Plans with 3, 4 and 5 meals for all four
diet types come back as `LOCAL_FALLBACK`, and totals are within about 2% of the target. History and the dashboard's
latest plan work; bad diet type and 6 meals give 400. Food search for "paneer" returns local results.
Gyms: during testing the public Overpass servers answered 406/502 or timed out, so the Nominatim
fallback was added. It returned 7 real gyms within 5 km of Nagpur and 7 within 2 km of Bandra, Mumbai.
The cached repeat call takes 0.1 s.

**Not verified (needs keys)**: the live Spoonacular and Google Places calls. Both are written against
the current public API docs and fall back automatically on any error. They'll be unit-tested with
mocks in Phase 7, and to try them for real you add the keys to `backend/.env`.

**Decisions**

- Eggetarian maps to Spoonacular's `vegetarian` (which allows eggs); non-veg sends no diet filter.
- Spoonacular's day plan has only 3 meals, so for 4–5 meals/day the snacks come from the local
  dataset. Each meal has an `origin` field and the plan has a `note`.
- Calorie split: 3 meals 30/40/30; 4 meals 25/35/10 (snack)/30; 5 meals 25/10/30/10/25.
- When picking local meals, meals of exactly the chosen diet get a small bonus, so non-veg users also
  get non-veg dishes, with some randomness among the 3 closest matches.
- `GET /api/gym/nearby` and `/search-food` return a small wrapper `{source, message, items/gyms}`
  instead of a bare list, so the UI can show where the data came from.
- Gender `OTHER` uses the average of the male/female constants (−78) in Mifflin-St Jeor.
