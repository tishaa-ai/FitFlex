# FitFlex: Online Fitness Platform

> Final-year B.Tech major project. FitFlex connects users with certified trainers and adds
> nutrition planning, a nearby gym locator, progress tracking and ML-based pose detection.

**Status:** under construction, being built phase by phase. See [docs/PROGRESS.md](docs/PROGRESS.md).

## Features (planned)

- Separate **User** and **Trainer** portals (JWT login)
- Online training: one-on-one bookings and group sessions with Jitsi Meet video links
- Personalized diet plans (Spoonacular API, with a local fallback dataset)
- Nearby gym locator (Google Places, with an OpenStreetMap fallback)
- Progress tracking with charts
- Pose detection (Python + OpenCV + MediaPipe): squat and bicep-curl rep counting with posture feedback

## Tech stack

| Layer          | Technology                                                       |
| -------------- | ---------------------------------------------------------------- |
| Frontend       | HTML5, CSS3, vanilla JavaScript, Chart.js, Leaflet, Font Awesome |
| Backend        | Java 17, Spring Boot, Spring Security + JWT, Spring Data JPA     |
| Database       | MySQL 8 (H2 in-memory profile for quick runs)                    |
| Pose detection | Python, OpenCV, MediaPipe, NumPy, Jupyter                        |
| Testing        | JUnit 5, Mockito, MockMvc, pytest, Postman                       |

## Folder structure

```
backend/          Spring Boot REST API
frontend/         Static HTML/CSS/JS site (run with VS Code Live Server)
pose-detection/   Python pose detection script + teaching notebook
docs/             Learning guide, API reference, progress log, Postman collection
```

## Setup

_Full step-by-step setup for Windows, demo credentials and troubleshooting will be added in Phase 8._
