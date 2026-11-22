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

---

## Phase 5: Frontend (2026-11-22)

**Built**

- `css/style.css`: one shared theme. CSS variables (dark charcoal + lime accent), Poppins, responsive
  grid, cards, forms, badges, tabs, tables, toasts, spinners, empty states, fade-in on scroll
  (`.reveal`), hamburger menu under 960 px, and `prefers-reduced-motion` support.
- `js/config.example.js` (copy it to the gitignored `config.js`), `js/api.js` (fetch wrapper: JWT
  header, JSON, `ApiError` with field errors, 401 → clear session + redirect, authenticated file
  download through a Blob), `js/auth.js` (session in localStorage, JWT expiry check,
  `requireRole()` route guard, role-based navbar, footer, flash messages), `js/ui.js` (escaping
  against XSS, toasts, loaders, attribute-based form validation, formatting, shared trainer card,
  workout plan and session renderers).
- 16 pages, each with its own script in `js/pages/`: index, login, register, trainer-login,
  trainer-register, user-dashboard, trainer-dashboard (tabs: overview, clients, availability, group
  session, workout plan builder with dynamic rows, resources upload, profile), trainers, trainer-profile
  (availability table + booking form with slot hints), sessions (upcoming/past/group tabs,
  role-based actions, Jitsi join), nutrition (BMR/TDEE/target, meal cards, totals, Chart.js macro
  doughnut, food search, history), gyms (Leaflet + OSM tiles, or Google Maps when a browser key is
  set; geolocation → city → lat/lng fallback, default Nagpur), progress (3 Chart.js charts + table),
  pose, about, contact.

**Verified in headless Chrome** (DevTools protocol scripts kept outside the repo):

- Console check: all 16 pages × 3 roles (guest, USER, TRAINER) = 48 page loads with **0 JS exceptions,
  0 console errors and 0 failed requests**. Route guards send guests to the right login page and send
  the wrong role to its own dashboard.
- 21 click-through flows, all passing: client-side validation; register → dashboard; logout; login
  with the demo button; wrong-password toast; diet plan with chart; food search; booking inside and
  outside availability; progress log with 3 charts; trainer login; trainer confirms → Jitsi link; add
  and delete an availability slot; workout plan with 3 dynamic rows; file upload; group session
  creation; user sees Join Session and joins the group class; the user dashboard shows the plan and
  the file downloads; contact form; mobile hamburger menu at 390 px with no horizontal scroll.
- Screenshots were checked by eye. One bug fixed: the logo rendered as "Fit Flex" because of the
  flex gap.

**Decisions**

- The navbar and footer are rendered by `auth.js` into `<header id="navbar">` / `<footer id="footer">`,
  so the 16 pages don't repeat that markup.
- Plain `<script>` files with global `api` / `auth` / `ui` objects (no modules, no build step), so
  it is easy to read and runs on Live Server.
- `login.js` and `register.js` serve both portals and use `<body data-portal="user|trainer">` to tell them apart.
- After login, users go back to the page that sent them to login (`?redirect=`, same-site `.html` pages only).
- Testimonials are labelled as fictional, as the spec asks.

---

## Phase 6: Pose detection (2026-11-22)

**Built** (`pose-detection/`)

- `pose_utils.py`: `calculate_angle(a, b, c)` with `atan2` (angle at b, folded into 0–180),
  `landmark_to_pixel`, visibility helpers, `line_y_at` (used by the plank hip check) and `MovingAverage`.
- `pose_detector.py`: webcam (`--source 0`) or video file. MediaPipe Pose with the skeleton drawn, and
  one documented `CONFIG` dict for every threshold. `SquatTracker`, `BicepCurlTracker` and `PlankTracker`
  are pure-logic state machines, so they can be tested. The overlay panel shows exercise, reps or hold time,
  angles in "deg", Correct posture / Wrong posture!!, the reason and FPS. Keys s/b/p/r/q. It picks the
  body side the camera sees best. On quit it prints a summary and, with `--upload` (`--email/--password`
  or `--token`, `--api`), logs in and POSTs to `/api/progress/pose`. `--no-display` and `--max-frames`
  are there for headless testing.
- `pose_detection.ipynb`: imports → landmarks on a real photo → angle function with a worked example →
  squat logic with a plot → bicep logic → live webcam cell (off by default) → algorithm comparison table
  (labelled "from project report"). It was executed, and its outputs are saved.
- `test_pose_utils.py` (12 tests, including the 90/180/45 deg cases from the spec) and
  `test_pose_detector.py` (14 tests: reps, "Go lower", "Keep chest up", "Extend arm fully",
  "Keep elbow close", plank timer, hips too low/high, side selection).
- `requirements.txt` (pinned), `README.md` (Windows setup, run, upload, notebook, tests, troubleshooting,
  image credits), and two CC BY 2.0 sample photos in `assets/`.

**Verified**

- Python **3.11.17** venv made with `uv`. Unpinned `pip install mediapipe` gives **1.2.3, which has no
  `mp.solutions`** (the classic API was removed), and 0.10.30+ has no Windows wheels. So it is pinned to
  **mediapipe 0.10.21** (+ numpy 1.26.4, opencv-contrib-python 4.11.0.86, protobuf 4.25.9), which is
  verified to import and run `mp.solutions.pose`.
- `pytest`: **26 passed**.
- Real detection on a squat photo: 33 landmarks; left knee 90 deg (a real squat). Rendered the overlay
  and checked it by eye.
- End to end, headless: video → detector → summary → login → upload; the entry appears in
  `/api/progress/my` as `POSE_DETECTION`. The `--token` upload works too. Wrong password, bad token,
  missing video file and `--upload` without credentials all give clear messages instead of crashes.
- Notebook executed with `jupyter nbconvert --execute`: 0 errors.
- **Not verified here:** live webcam use. There is no camera on this machine, so please run
  `python pose_detector.py` yourself.

**Decisions**

- The classic `mp.solutions.pose` API (matching the project report) was kept, on a pinned version,
  instead of the newer Tasks API. The Tasks API would need a separate model-file download and more code
  for the same result.
- Model complexity 1, because that model ships inside the package. Complexity 2 downloads an extra
  model at runtime.
- Correct-form % = frames with correct posture ÷ frames where the body was visible. The frames of a
  shallow (not counted) squat are counted as incorrect.
- A rep is counted on the return to the start position (squat: up past 160 deg; curl: straight past 160 deg).
- Plank uploads `reps = 0` and `durationSeconds` = time actually held in good form.
- 2D limitation found while testing: in a front-facing photo the leg pointing at the camera reads 135 deg
  while the other reads 90 deg. The README, the pose page and the program's startup message all
  tell users to stand sideways.
- Setup instructions use `uv` first: on this PC `py -3.11` doesn't find the uv-installed Python, and uv
  venvs have no `pip`. Installing from python.org with `py -3.11` is given as option B.

---

## Phase 7: Tests + Postman collection (2026-11-22)

**Built**

- `AuthServiceTest` (Mockito, 6 tests): duplicate email rejected (and nothing saved), the password is
  stored hashed and the email normalised, wrong password rejected, unknown email gets the same message,
  a trainer can't use the user portal, and a correct login returns the token.
- `NutritionServiceTest` (11): BMR for a man (1673.75) and a woman (1320.25) checked against hand
  calculations, TDEE, target calories for all 3 goals, the 1200 kcal floor; local fallback when no key
  is set (Spoonacular never called); fallback when Spoonacular throws; Spoonacular meals plus local snack;
  incomplete profile rejected; diet type parsing; food-search fallback.
- `SessionServiceTest` (9): valid booking → REQUESTED with no link; **overlapping booking → 409**;
  back-to-back booking allowed; **outside availability → 400** (the message lists the slots); day with no
  slots; past time; trainer confirm → Jitsi link; user can't confirm; full group → 409.
- `HelperLogicTest` (5): upload file-name sanitising (`../../`, `C:\fakepath\`, hidden files, long names),
  haversine distance Nagpur–Mumbai ≈ 685 km, diet compatibility rules.
- `ApiIntegrationTest` (MockMvc + H2 + seed data, 12): register → login (201/200, JWT has 3 parts);
  duplicate email → 409; validation → 400 with field errors; wrong password → 401; trainer
  register/login and portal separation; public `GET /api/trainers` with filters (and no contact details
  shown); unknown trainer → 404; **protected endpoint without a token → 401** (also with a malformed token);
  with a token → 200 and no password in the response; wrong role → 403; the full HTTP flow
  book → clash 409 → confirm → Jitsi link; diet plan via the local fallback.
- `docs/FitFlex.postman_collection.json`: 50 requests in 11 folders, with example bodies and test
  scripts. The demo logins save `{{token}}` and `{{trainerToken}}`, and later requests save
  `sessionId`, `groupSessionId`, `slotId`, `resourceId`. Booking and group times are computed by
  pre-request scripts so they are always in the future.

**Verified**

- `mvnw.cmd test`: **44 tests, 0 failures**. `pytest`: **26 passed**.
- Mutation check: disabling the clash rule and changing the BMR constant made 5 tests fail (unit and
  integration), so the tests really do guard these rules. The code was restored from git afterwards.
- Postman collection: Newman could not be installed here (npm installs are blocked in this
  environment), so the collection was run with a small Node runner that uses the same request, variable
  and test-script data: **50 requests, 60 assertions, 0 failures**, against the H2 backend. Postman's own
  runner has not been tried; please run it once from Postman's Collection Runner.

**Decisions**

- Unit tests use Mockito with no Spring context (fast). Integration tests use `@SpringBootTest` +
  `@AutoConfigureMockMvc` (Spring Boot 4 package: `org.springframework.boot.webmvc.test.autoconfigure`)
  on the `h2` profile with the demo seed data. Uploads go to `target/test-uploads`.
- The collection uses two token variables, `{{token}}` (user) and `{{trainerToken}}`, because the API has
  two roles. The Trainer folder's auth is set to `{{trainerToken}}`.

---

## Phase 8: Docs + final check (2026-11-22)

**Built**

- `README.md`: features, tech stack, Mermaid architecture diagram, structure, prerequisites, Windows
  setup (H2 quick start, MySQL with `.env`, Live Server frontend, pose detection), demo accounts, how to
  get Spoonacular and Google keys, tests, a troubleshooting table and screenshot placeholders.
- `docs/LEARNING_GUIDE.md`: big picture (Mermaid), folder-by-folder and file-by-file tour, 4 flows traced
  with exact file and method names (auth/JWT, booking → confirm → Jitsi, diet plan with fallback, pose →
  upload), concepts (REST, layers, DTO vs entity, JPA relations, filter chain, JWT, BCrypt, CORS,
  fetch/async, localStorage, Mifflin-St Jeor, atan2), reading order, 30 viva questions with answers,
  and future scope.
- `docs/API.md`: every endpoint with role, request, response, error format and the business rules.

**Final end-to-end check**

- Checked the docs automatically: 270 identifiers mentioned in the docs were looked up in the source.
  All exist, apart from framework or PowerShell names.
- **Fresh `git clone`** into an empty folder, following the README: no `.env` and no `config.js` in the
  repo (secrets not committed). `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=h2"` starts and
  seeds; copying `config.example.js` → `config.js` and serving `frontend/` on 127.0.0.1:5500 works.
- Against that clone in headless Chrome: 48 page loads (16 pages × guest/user/trainer) with **0 console
  errors**, and **21/21 UI flows** passed (register, login, diet plan, book → confirm → Join, group join,
  workout plan, upload/download, progress charts, contact, mobile menu).
- Tests: **44 JUnit + 26 pytest, all passing**. Postman collection: 50 requests and 60 assertions passing.

### Definition of done: status

| Item | Status |
|------|--------|
| `mvnw spring-boot:run` on the `h2` profile | ✅ verified (also from a fresh clone) |
| Runs on MySQL | ⚠️ needs your MySQL password in `backend/.env` (not available to me); H2 runs in MySQL mode |
| Demo user and trainer log in; book → confirm → join link | ✅ verified in the browser |
| Diet plan generates (fallback) | ✅ |
| Gym map shows results (OSM fallback) | ✅ (Nominatim fallback when Overpass is busy; can take 10–20 s) |
| Progress charts render | ✅ |
| Trainer upload / user download | ✅ |
| Pose script counts squats/curls, posture feedback, uploads | ✅ logic, detection on photos and upload verified; ⚠️ live webcam not testable here |
| All JUnit and pytest tests pass | ✅ 44 + 26 |
| No secrets committed; `.env` and `config.js` gitignored | ✅ |

### What you must do manually

1. **MySQL run:** `Copy-Item backend\.env.example backend\.env`, set `DB_PASSWORD` (and a `JWT_SECRET` of 32+
   characters), then `.\mvnw.cmd spring-boot:run` from `backend/`.
2. **Frontend config:** `Copy-Item frontend\js\config.example.js frontend\js\config.js` (one time), then
   open `frontend/index.html` with VS Code Live Server.
3. **Pose detection with your webcam:** follow `pose-detection/README.md` (create the Python 3.11 venv,
   `python pose_detector.py`), and test once with `--upload`.
4. **Optional keys:** `SPOONACULAR_API_KEY` and `GOOGLE_MAPS_API_KEY` in `backend/.env`, and
   `GOOGLE_MAPS_BROWSER_KEY` in `frontend/js/config.js`. The live Spoonacular and Google paths are written
   to the current API docs and unit-tested with mocks, but were never called for real (no keys).
5. **Postman:** import `docs/FitFlex.postman_collection.json` and run it once in Postman's Collection Runner.
6. **Screenshots** for the README and the report (`docs/screenshots/`).
