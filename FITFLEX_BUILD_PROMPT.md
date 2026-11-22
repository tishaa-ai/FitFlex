# FitFlex: Full Build Instructions for Claude Code

## 0. Who I am and how you should work

You are building **FitFlex** from scratch in this (empty) folder. FitFlex is my final-year B.Tech major project: an online fitness platform that connects users with certified trainers. It has online training sessions, one-on-one coaching, personalized diet plans (Nutrition API), a nearby gym locator (Google Maps), progress tracking, and ML-based pose detection (Python + OpenCV + MediaPipe) that gives real-time posture feedback.

I will study this code after you build it, and I'll be asked about it in viva and job interviews. So the code must be **clean, beginner-readable and well commented**.

**Working rules**

- Work through the phases in Section 11 **in order**. Keep a todo list.
- After each phase: compile/run it and fix all errors before moving on, append a short summary to `docs/PROGRESS.md`, make a git commit with a clear message, and tell me in 3–4 simple lines what you built. Then **continue to the next phase without waiting**, unless you need something from me (an API key, an install, a permission).
- First detect my OS (Windows/Mac/Linux) and give every command for **my** OS.
- Check what's installed: `java -version` (need 17+), Maven (or use the Maven wrapper), `mysql`, `python` (3.10 or 3.11 for MediaPipe), `git`. If something is missing, tell me exactly how to install it and keep going with the parts that don't need it.
- **Never hardcode secrets.** Use env vars or gitignored files. Create a proper `.gitignore`.
- **Every external API must have a fallback**, so the app works fully even with no API keys.
- Prefer simple, explicit code over clever code. **No Lombok**: write getters/setters so I can read everything. Use DTOs; never return JPA entities directly from controllers.
- Comments: every class gets a 1–3 line header comment explaining its role. Non-obvious logic gets a "why" comment. Don't comment every line.
- Use current stable library versions, and **verify by compiling** instead of trusting memory for APIs that have changed (Spring Security 6+ lambda DSL, jjwt 0.12+ API, MediaPipe Python API).
- Don't ask me design questions. Follow this spec, and where something is ambiguous pick the simplest option and note it in `docs/PROGRESS.md`.

---

## 1. Tech stack (fixed, matches my project report)

| Layer          | Technology                                                                                                                                                                       |
| -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Frontend       | HTML5, CSS3, vanilla JavaScript (no React/Angular/Bootstrap). Chart.js via CDN for charts, Font Awesome via CDN for icons, Google Font (Poppins or Inter)                        |
| Backend        | Java 17+, Spring Boot (latest stable on start.spring.io), Maven + Maven wrapper (`mvnw`), Spring Web, Spring Data JPA (Hibernate), Spring Security + JWT (jjwt), Bean Validation |
| Database       | MySQL 8 (`fitflex_db`), plus an **H2 in-memory profile `h2`** so I can run without MySQL                                                                                         |
| External APIs  | Spoonacular (nutrition + meal plans), Google Maps JavaScript API + Places (nearby gyms), Jitsi Meet links for video sessions (no key needed)                                     |
| Pose detection | Python 3.10/3.11, OpenCV, MediaPipe Pose, NumPy, Jupyter Notebook                                                                                                                |
| Testing        | JUnit 5, Mockito, MockMvc (H2 for tests), pytest, Postman collection                                                                                                             |

---

## 2. Folder structure

```
fitflex/
├── backend/
│   ├── src/main/java/com/fitflex/
│   │   ├── FitFlexApplication.java
│   │   ├── config/        (SecurityConfig, CorsConfig, DataSeeder, RestClientConfig)
│   │   ├── security/      (JwtUtil, JwtAuthFilter, CustomUserDetailsService)
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/        (+ enums)
│   │   ├── dto/           (request/ and response/ subfolders)
│   │   └── exception/     (GlobalExceptionHandler + custom exceptions)
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   ├── application-h2.properties
│   │   └── fallback/      (local meals JSON for nutrition fallback)
│   ├── src/test/java/com/fitflex/...
│   ├── uploads/           (gitignored, trainer uploaded files)
│   └── .env.example
├── frontend/
│   ├── index.html  (+ all pages listed in Section 7)
│   ├── css/style.css      (shared theme, plus page CSS only if needed)
│   ├── js/
│   │   ├── config.example.js   (API_BASE_URL, GOOGLE_MAPS_BROWSER_KEY)
│   │   ├── api.js              (fetch wrapper that adds JWT, handles errors and 401 → logout)
│   │   ├── auth.js             (login state, role guards, navbar rendering)
│   │   ├── ui.js               (toasts, loaders, form validation helpers)
│   │   └── pages/              (one JS file per page)
│   └── assets/
├── pose-detection/
│   ├── pose_detector.py
│   ├── pose_utils.py           (calculate_angle and other helpers)
│   ├── pose_detection.ipynb
│   ├── test_pose_utils.py
│   ├── requirements.txt
│   └── README.md
├── docs/
│   ├── LEARNING_GUIDE.md
│   ├── API.md
│   ├── PROGRESS.md
│   └── FitFlex.postman_collection.json
├── README.md
└── .gitignore
```

---

## 3. Architecture

Layered architecture, exactly as in my report:

- **Controller layer**: `@RestController` classes. Only routing, request validation (`@Valid`) and HTTP responses. No business logic.
- **Service layer**: all business logic. Matching users with trainers, booking checks, calorie calculation, calling external APIs.
- **Repository layer**: Spring Data JPA interfaces doing CRUD on User, Trainer, Session, Diet, etc.
- **Database**: MySQL via JPA/Hibernate (`ddl-auto=update`).

The frontend calls the REST API with `fetch`. The JWT is stored in `localStorage` and sent as `Authorization: Bearer <token>`.

---

## 4. Roles and authentication

- Two roles: `USER` and `TRAINER`, with **separate login/register portals** (pages and endpoints).
- Stateless JWT auth, BCrypt password hashing, Spring Security role checks (`@PreAuthorize` or `requestMatchers`).
- Public endpoints: register/login, `GET /api/trainers/**`, `POST /api/contact`, `GET /api/gym/nearby`.
- CORS allows `http://127.0.0.1:5500` and `http://localhost:5500` (VS Code Live Server). Make origins configurable in properties.
- JWT secret and expiry come from config/env (24h default).

---

## 5. Data model (JPA entities)

- **User**: id, fullName, email (unique), password (BCrypt), role (USER/TRAINER), phone, age, gender, heightCm, weightKg, fitnessGoal (WEIGHT_LOSS / MUSCLE_GAIN / MAINTENANCE), fitnessLevel (BEGINNER / INTERMEDIATE / ADVANCED), activityLevel (SEDENTARY / LIGHT / MODERATE / ACTIVE / VERY_ACTIVE), createdAt
- **Trainer**: id, user (OneToOne → User), specialization (Strength, Yoga, Weight Loss, Cardio, Zumba, CrossFit…), experienceYears, certifications, bio, pricePerSession, rating, profileImageUrl, verified
- **AvailabilitySlot**: id, trainer, dayOfWeek, startTime, endTime
- **TrainingSession**: id, trainer, user (null for group sessions), participants (ManyToMany users, for group sessions), sessionType (ONE_ON_ONE / GROUP), title, description, scheduledAt, durationMinutes, status (REQUESTED / CONFIRMED / COMPLETED / CANCELLED), meetingLink, maxParticipants
- **WorkoutPlan**: id, trainer, user, title, goal, durationWeeks, createdAt, exercises (OneToMany → **WorkoutExercise**: name, sets, reps, restSeconds, dayNumber, notes)
- **TrainerResource**: id, trainer, title, type (VIDEO / DOCUMENT / PLAN), originalFileName, storedPath, uploadedAt, user (null = visible to all the trainer's clients)
- **DietPlan**: id, user, goal, dietType, bmr, tdee, targetCalories, planJson (TEXT/LONGTEXT), source (SPOONACULAR / LOCAL_FALLBACK), createdAt
- **ProgressEntry**: id, user, date, weightKg, workoutMinutes, caloriesBurned, notes, source (MANUAL / POSE_DETECTION), exercise, reps, correctFormPercent
- **ContactMessage**: id, name, email, subject, message, createdAt

---

## 6. REST API (all under `/api`)

**User**

- `POST /api/user/register`, `POST /api/user/login` → returns `{token, role, name, id}`
- `GET /api/user/profile`, `PUT /api/user/profile`

**Trainer** (`/api/trainer/add` name is from my report, keep it)

- `POST /api/trainer/add` (trainer registration), `POST /api/trainer/login`
- `GET /api/trainers` (public; filters: specialization, minExperience, maxPrice, search by name), `GET /api/trainers/{id}` (with availability)
- `GET /api/trainer/profile`, `PUT /api/trainer/profile` (TRAINER)
- `GET/POST/DELETE /api/trainer/availability` (TRAINER)
- `GET /api/trainer/clients` (TRAINER: users who booked them)

**Sessions**

- `POST /api/sessions/book`: USER books a one-on-one with a trainer at a datetime. Validate: future time, inside the trainer's availability, no clash with the trainer's other sessions.
- `POST /api/sessions/group` (TRAINER creates a group session), `POST /api/sessions/{id}/join` (USER joins; respect maxParticipants)
- `GET /api/sessions/my` (both roles; `?filter=upcoming|past`)
- `PUT /api/sessions/{id}/status`: TRAINER confirms/cancels/completes; USER can cancel their own
- On confirm, generate `meetingLink = https://meet.jit.si/FitFlex-<random-uuid>`

**Workout plans**

- `POST /api/workout-plans` (TRAINER, for one of their clients), `GET /api/workout-plans/my` (USER sees assigned, TRAINER sees created)

**Trainer resources (videos, documents, plans)**

- `POST /api/resources` (multipart, TRAINER; max 50 MB; allow mp4, pdf, docx, jpg, png; sanitize file names; store with UUID name in `uploads/`)
- `GET /api/resources/my`, `GET /api/resources/{id}/download` (only owner trainer or their clients)

**Nutrition and diet**

- `POST /api/diet/generate` with body `{goal, dietType (vegetarian/vegan/eggetarian/non-veg), mealsPerDay}`:
  1. Compute **BMR with Mifflin-St Jeor** from the user profile, then **TDEE** = BMR × activity factor, then target calories by goal (−500 for weight loss, +300 for muscle gain, 0 for maintenance)
  2. Call Spoonacular `GET /mealplanner/generate?timeFrame=day&targetCalories=…&diet=…` from the **backend** (key never goes to the browser)
  3. **Fallback**: if there's no key or the call fails, build the plan from `resources/fallback/meals.json`, a local dataset of ~40 Indian-friendly meals (veg + non-veg) with calories/protein/carbs/fat per meal type (breakfast/lunch/snack/dinner)
  4. Save as DietPlan and return meals grouped by type, with calories and macros
- `GET /api/diet/my` (history), `GET /api/diet/search-food?query=` (Spoonacular ingredient/food search, local fallback)

**Gyms**

- `GET /api/gym/nearby?lat=&lng=&radius=3000`: if `GOOGLE_MAPS_API_KEY` is set, use Google Places Nearby Search (`type=gym`). Otherwise use the free OpenStreetMap Overpass API (`leisure=fitness_centre`). Return a normalized list `{name, address, lat, lng, rating, distanceKm}`.

**Progress**

- `POST /api/progress` (manual log), `GET /api/progress/my?from=&to=`
- `GET /api/progress/summary` → current streak, total workouts, weight change, average form accuracy from pose sessions
- `POST /api/progress/pose` (called by the Python pose script with the user's JWT; source = POSE_DETECTION)

**Contact and dashboards**

- `POST /api/contact`
- `GET /api/dashboard/user`, `GET /api/dashboard/trainer` (aggregated data for the dashboard pages)

**Errors**: one consistent JSON shape `{timestamp, status, message, errors: {field: msg}}` from a `@RestControllerAdvice`. Return proper status codes (400/401/403/404/409).

**Config** (`application.properties`): read everything from env vars with safe defaults, and load a gitignored `backend/.env` file using `spring.config.import=optional:file:.env[.properties]`. Keys: `DB_URL` (default `jdbc:mysql://localhost:3306/fitflex_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Kolkata&allowPublicKeyRetrieval=true`), `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `SPOONACULAR_API_KEY`, `GOOGLE_MAPS_API_KEY`, `CORS_ORIGINS`. Provide `.env.example`.

**Seed data** (`DataSeeder`, runs only if the DB is empty):

- Demo user `user@fitflex.com` / `User@1234` with a full profile
- 5 demo trainers `trainer1@fitflex.com` … `trainer5@fitflex.com` / `Trainer@1234`, each with a different specialization, realistic Indian names, bios, prices in ₹ and availability
- A few sessions (requested/confirmed/completed), one workout plan, 4 weeks of progress entries (including some POSE_DETECTION ones) so the charts look real
- List all demo credentials in README

---

## 7. Frontend pages

**Common to all pages**: responsive navbar (links change by login state and role, hamburger menu on mobile), footer, toast notifications, loading spinners, empty states, JS form validation (required fields, email format, password min 8 chars with a number, positive numbers for height/weight). Use CSS variables for the theme and a clean, modern fitness look: dark charcoal plus one energetic accent (lime or orange), good spacing, subtle hover effects and fade-in-on-scroll animations. Route guards in `auth.js` redirect if the user isn't logged in or has the wrong role.

1. `index.html` (Home): hero with CTA ("Join as User" / "Join as Trainer"), features section (Online Training, Pose Detection, Nutrition Plans, Gym Locator, Progress Tracking), how-it-works steps, featured trainers fetched from the API, testimonials-style section with clearly fictional names, footer
2. `login.html` and `register.html`: **user portal**. Register collects profile + goal + fitness level + activity level
3. `trainer-login.html` and `trainer-register.html`: **trainer portal**. Specialization, experience, certifications, price per session, bio
4. `user-dashboard.html`: welcome, stat cards (streak, workouts, weight change, form accuracy), upcoming sessions, latest diet plan summary, assigned workout plans, resources shared by trainers, quick links
5. `trainer-dashboard.html`: upcoming sessions, booking requests (confirm/cancel buttons), clients list, availability manager, create group session form, create workout plan for a client (dynamic exercise rows), upload resources with a list of uploaded files
6. `trainers.html`: search + filter trainer cards. `trainer-profile.html?id=` shows details, availability and a booking form
7. `sessions.html`: my sessions (Upcoming/Past tabs), status badges, "Join Session" button opening the Jitsi link (only when CONFIRMED), available group sessions to join
8. `nutrition.html`: generate form (goal, diet type, meals/day) → shows BMR / TDEE / target calories, then meals grouped by type in cards with calories and protein/carbs/fat, daily totals, a small macro chart, food search, plan history
9. `gyms.html`: map + side list of nearby gyms (name, address, rating, distance, "Get Directions" link opening Google Maps). Uses browser geolocation, falling back to a manual city/lat-lng input (default Nagpur: 21.1458, 79.0882). If `GOOGLE_MAPS_BROWSER_KEY` in `config.js` is set, render Google Maps; otherwise render **Leaflet + OpenStreetMap tiles** (CDN)
10. `progress.html`: log form, summary cards, Chart.js charts: weight over time, workout minutes per week, pose form accuracy over time; table of recent entries
11. `pose.html`: explains the pose detection feature (how angles and reps work, supported exercises), steps to run the Python app, and the user's pose-session history from the API
12. `about.html`, `contact.html` (contact form posting to `/api/contact`)

`js/config.example.js` → I copy it to `config.js` (gitignored). Default `API_BASE_URL = "http://localhost:8080/api"`.

---

## 8. Pose detection module (`pose-detection/`)

My report shows the pose detection running in Python with OpenCV + MediaPipe and an on-screen overlay like: `Bicep Reps: 27`, `Squats: 0`, `Knee: 178°`, `Torso: 177°`, `Wrong posture!!` / `Correct posture`. Rebuild that properly.

- `pose_utils.py`: `calculate_angle(a, b, c)` using `atan2` (angle at point b, 0–180), plus small helpers (landmark → pixel, visibility check, smoothing with a moving average to reduce jitter).
- `pose_detector.py`: webcam by default, or `--source video.mp4`. MediaPipe Pose (33 landmarks). Draws the skeleton.
  - **Squat**: knee angle (hip–knee–ankle) and torso angle (shoulder–hip–knee). A rep counts when the knee goes below ~90° (down) and back above ~160° (up). Form checks: torso leaning too far forward → "Keep chest up"; not deep enough → "Go lower". Green "Correct posture" / red "Wrong posture!!" status.
  - **Bicep curl**: elbow angle (shoulder–elbow–wrist). Rep when going >160° → <40° → back. Checks: elbow drifting away from the torso → "Keep elbow close"; incomplete extension → "Extend arm fully".
  - **Plank** (bonus): shoulder–hip–ankle line (~165–180° is good). Hold timer, "Hips too low" / "Hips too high".
  - Overlay panel: exercise name, reps, key angles, feedback, FPS. **Note:** OpenCV `putText` can't render the `°` symbol (it showed `??` in my report screenshots), so write `deg` instead.
  - Keys: `s` squat, `b` bicep curl, `p` plank, `r` reset, `q` quit.
  - All thresholds go in one documented `CONFIG` dict at the top.
  - On quit, print a summary (exercise, total reps, correct-form %, duration). With the optional `--upload --email --password` flags (or `--token`), log in and POST the summary to `/api/progress/pose`.
- `pose_detection.ipynb`: a step-by-step teaching notebook. Imports → landmarks on a sample image → the angle function explained with a worked example → squat logic → bicep logic → live webcam cell → markdown cell with the algorithm comparison table from my report (MediaPipe 90%, OpenPose 85%, AlphaPose 76%, HRNet 78%, labelled as "from project report").
- `requirements.txt` with pinned versions. **MediaPipe caution:** use a Python 3.10/3.11 venv and pin a MediaPipe version where the API you use actually works; verify by running an import + a quick test, don't assume.
- `test_pose_utils.py`: pytest for `calculate_angle` (90°, 180°, 45° cases).
- `README.md`: venv setup for my OS, how to run the script and the notebook, troubleshooting (webcam index, lighting, full body in frame).

---

## 9. Testing

- JUnit + Mockito: AuthService (duplicate email rejected, wrong password rejected), NutritionService (BMR/TDEE/target-calorie math with known values, fallback used when there's no key), SessionService (booking clash rejected, booking outside availability rejected).
- MockMvc: register/login endpoints, `GET /api/trainers`, protected endpoint returns 401 without a token.
- Tests use H2.
- `docs/FitFlex.postman_collection.json` with every endpoint, example bodies, and a `{{token}}` variable auto-set by the login request's test script.
- Run all tests at the end. All must pass.

---

## 10. Documentation (very important for me)

- **README.md**: overview, features, tech stack, architecture diagram (Mermaid), prerequisites, step-by-step setup for my OS (MySQL or H2, `.env`, run backend, run frontend with Live Server, run pose detection), demo credentials, how to get a free Spoonacular key and (optional) Google Maps key, troubleshooting, screenshot placeholders.
- **docs/LEARNING_GUIDE.md**: written for me in simple English (light Hinglish is fine):
  1. Big-picture architecture (Mermaid diagram) and how the frontend, backend, DB, external APIs and the Python module connect
  2. Folder-by-folder and file-by-file: what each one does
  3. Trace 4 flows end-to-end with exact file and method names: (a) user register → login → JWT → calling a protected API, (b) booking a session → trainer confirms → Jitsi link, (c) generating a diet plan (BMR → Spoonacular → fallback), (d) pose detection angle → rep counting → upload to progress
  4. Key concepts explained simply: REST, layered architecture, DTO vs Entity, the JPA relationships used here, the Spring Security filter chain, JWT, BCrypt, CORS, fetch + async/await, localStorage, Mifflin-St Jeor, angle math with atan2
  5. Suggested order to read the code
  6. 30 likely viva/interview questions with short answers
  7. Future scope: built-in WebRTC video, real-time chat, browser pose detection with TensorFlow.js/MediaPipe Web, mobile app, cloud deployment
- **docs/API.md**: endpoint table (method, path, role, request, response).

---

## 11. Phases (do them in this order)

0. **Environment check + scaffold**: detect OS, check tools, create the folder structure, `.gitignore`, `git init`, README skeleton, `docs/PROGRESS.md`.
1. **Backend foundation**: generate the Spring Boot project (start.spring.io via curl, or manually with the Maven wrapper), entities, enums, repositories, properties, H2 profile. App starts.
2. **Security + auth**: JWT utils/filter, SecurityConfig, CORS, user and trainer register/login, GlobalExceptionHandler, DataSeeder. Verify with curl.
3. **Core features**: trainers, availability, sessions (book/group/join/status), workout plans, resource upload/download, progress, contact, dashboards.
4. **External APIs**: NutritionService (BMR/TDEE + Spoonacular + local fallback), GymService (Google Places + Overpass fallback).
5. **Frontend**: shared CSS/JS first, then every page in Section 7, wired to the API. Check each page for console errors.
6. **Pose detection**: utils, detector script, notebook, requirements, upload integration, pytest.
7. **Tests + Postman collection**: write and run all tests, fix failures.
8. **Docs + final check**: README, LEARNING_GUIDE, API.md. Do a full end-to-end run-through and give me a final list of anything I must do manually (install MySQL, add API keys, etc.).
9. **(Optional, only if I ask later)**: browser-based pose detection on `pose.html` using MediaPipe Tasks for Web.

---

## 12. Definition of done

- `mvnw spring-boot:run` starts with no errors on the `h2` profile, and also on MySQL once I've configured it.
- From the frontend: demo user and demo trainer can log in; the full booking flow works (book → confirm → join link); a diet plan generates (fallback is fine without a key); the gym map shows results (OSM fallback is fine); progress charts render; trainer upload/download works.
- The pose script runs with my webcam, counts squats and bicep curls, shows correct/wrong posture feedback, and can upload a session to progress.
- All JUnit and pytest tests pass.
- No secrets committed. `.env` and `config.js` are gitignored, and example files are provided.
