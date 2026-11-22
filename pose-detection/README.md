# FitFlex Pose Detection

Real-time posture feedback with **Python + OpenCV + MediaPipe Pose**. It counts **squats** and
**bicep curls**, times **planks**, shows "Correct posture" / "Wrong posture!!" with a reason, and can
upload each workout to your FitFlex progress page.

```
pose-detection/
├── pose_detector.py        the webcam/video program (all thresholds in the CONFIG dict at the top)
├── pose_utils.py           calculate_angle (atan2), landmark helpers, moving-average smoothing
├── pose_detection.ipynb    step-by-step teaching notebook (already run, outputs included)
├── test_pose_utils.py      pytest: angle maths (90, 180, 45 deg ...) and helpers
├── test_pose_detector.py   pytest: rep counting and form checks on synthetic poses
├── requirements.txt        pinned versions that are verified to work together
└── assets/                 sample photos used by the notebook (credits below)
```

---

## 1. Setup (Windows)

MediaPipe does **not** support Python 3.13/3.14 yet, so the module gets its own virtual environment
with **Python 3.11** (3.10 and 3.12 also work). Run these in **PowerShell** from the project root.

### Option A: with `uv` (already installed on this PC)

```powershell
cd pose-detection
uv python install 3.11                      # one time only
uv venv --python 3.11 .venv
uv pip install --python .venv -r requirements.txt
.venv\Scripts\activate
```

> A venv made by `uv` has no `pip` inside it, so always install packages with `uv pip install --python .venv ...`.

### Option B: with the python.org installer

1. Install **Python 3.11** from <https://www.python.org/downloads/windows/> (tick "Add python.exe to PATH" is optional).
2. Then:

```powershell
cd pose-detection
py -3.11 -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

If PowerShell refuses to run `activate`, run this once:
`Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`

### Check it worked

```powershell
python -c "import mediapipe as mp, cv2; print(mp.__version__, cv2.__version__)"   # 0.10.21 4.11.0
python -m pytest -q                                                                # 26 passed
```

### Why MediaPipe is pinned to 0.10.21

`pose_detector.py` uses the classic `mp.solutions.pose` API. **MediaPipe 1.x removed `mp.solutions`**
(only the newer "Tasks" API is left), and 0.10.30+ has no Windows wheels for it. 0.10.21 is the last
version where this API works on Windows, and its pose model is built in, so nothing else needs downloading.
It needs `numpy < 2`, which `requirements.txt` takes care of.

---

## 2. Run the detector

```powershell
python pose_detector.py                       # webcam 0, starts with squats
python pose_detector.py --exercise bicep      # start with bicep curls (or: plank)
python pose_detector.py --source 1            # use the second camera
python pose_detector.py --source my_workout.mp4
```

| Key | Action |
|-----|--------|
| `s` | squat mode |
| `b` | bicep curl mode |
| `p` | plank mode |
| `r` | reset the current counter |
| `q` | quit and print the summary |

The panel shows the exercise, reps (or plank hold time), the key angles, **Correct posture** (green)
or **Wrong posture!!** (red) with the reason, and the FPS. Angles are written as `deg` because
OpenCV's `putText` cannot draw the `°` symbol (it shows `??`).

### What is checked

| Exercise | Angle used | Rep / timer rule | Feedback |
|----------|-----------|------------------|----------|
| Squat | knee = hip-knee-ankle, torso = shoulder-hip-knee | knee < 90 deg then > 160 deg | "Keep chest up" (torso < 50 deg), "Go lower" (came up before 90 deg) |
| Bicep curl | elbow = shoulder-elbow-wrist, arm = hip-shoulder-elbow | elbow > 160 → < 40 → > 160 | "Keep elbow close" (arm > 35 deg), "Extend arm fully" |
| Plank | body = shoulder-hip-ankle | timer runs while 165-180 deg | "Hips too low", "Hips too high" |

All numbers live in the `CONFIG` dictionary at the top of `pose_detector.py`.

### Upload the workout to FitFlex

Start the backend first (see the main README). Then:

```powershell
python pose_detector.py --upload --email user@fitflex.com --password User@1234
# or, with a token copied from the browser (localStorage "fitflex_token"):
python pose_detector.py --upload --token eyJhbGciOi...
# a backend on another address:
python pose_detector.py --upload --api http://192.168.1.10:8080/api --email ... --password ...
```

When you press `q`, the summary (exercise, reps, correct-form %, duration) is printed and sent to
`POST /api/progress/pose` for every exercise you did. It then appears on **Progress** (form accuracy
chart) and **Pose Detection → My pose sessions** in the website.

---

## 3. The notebook

```powershell
.venv\Scripts\activate
jupyter notebook pose_detection.ipynb
```

It walks through: imports → landmarks on a sample photo → the angle function with a worked example →
squat logic → bicep logic → live webcam (set `RUN_WEBCAM = True`) → the algorithm comparison table from
the project report. It already contains its outputs, so you can read it on GitHub without running it.

In VS Code: open the notebook and pick the kernel `.venv\Scripts\python.exe`.

---

## 4. Tests

```powershell
python -m pytest -v
```

* `test_pose_utils.py`: `calculate_angle` for 90, 180 and 45 deg, plus folding of reflex angles,
  pixel coordinates, visibility and the moving average.
* `test_pose_detector.py`: builds fake skeletons at exact angles and checks rep counting, "Go lower",
  "Keep chest up", "Extend arm fully", "Keep elbow close", plank timer and hip checks, and side selection.

---

## 5. Troubleshooting

| Problem | Fix |
|---------|-----|
| `Could not open video source '0'` | Another app (Zoom, Teams, browser) is using the camera; close it. Or try `--source 1`. Check *Settings → Privacy → Camera → Let desktop apps access your camera*. |
| "Step back: full body not visible" | The joints for the exercise are not all visible. Move back so head to feet are in the frame. |
| Reps not counted / angles look wrong | Stand **sideways** to the camera. Angles are measured in 2D, so a leg or arm pointing *at* the camera looks straighter than it is. |
| Jumpy skeleton | Improve the **lighting** (light in front of you, not behind), wear clothes that contrast with the background, keep the camera still. |
| Low FPS | Set `"model_complexity": 0` in `CONFIG`, close other programs, or use a lower camera resolution. |
| `AttributeError: module 'mediapipe' has no attribute 'solutions'` | A newer MediaPipe got installed. Run `uv pip install --python .venv -r requirements.txt` (or `pip install -r requirements.txt`) again. |
| `No suitable Python runtime found` for `py -3.11` | Python 3.11 is not installed from python.org; use Option A (`uv`) or install it. |
| Upload: `Login failed` / connection refused | Check the email/password, and that the backend is running on `http://localhost:8080`. |

---

## Credits

Sample photos in `assets/` are by **PTPioneer**, licensed **CC BY 2.0**, via Wikimedia Commons:
[Personal training kettle bell squat](https://commons.wikimedia.org/wiki/File:Personal_training_kettle_bell_squat.jpg) (`sample_squat.jpg`) and
[TRX exercise Squat 2](https://commons.wikimedia.org/wiki/File:TRX_exercise_Squat_2.jpg) (`sample_trx_squat.jpg`), resized to 960 px.
