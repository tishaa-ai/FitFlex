"""
pose_detector.py - real-time posture feedback with OpenCV + MediaPipe Pose.

Counts squats and bicep curls, times planks, and says whether your posture is correct.
When you quit it prints a summary and (optionally) uploads it to your FitFlex progress.

Run (inside the venv, see README.md):
    python pose_detector.py                          # webcam 0, starts with squats
    python pose_detector.py --exercise bicep         # start with bicep curls
    python pose_detector.py --source workout.mp4     # analyse a video file
    python pose_detector.py --upload --email user@fitflex.com --password User@1234

Keys:  s = squat   b = bicep curl   p = plank   r = reset counters   q = quit
"""

import argparse
import sys
import time

import cv2

from pose_utils import MovingAverage, all_visible, average_visibility, calculate_angle, line_y_at

# =====================================================================================
# CONFIG - every threshold in one place. Angles are in degrees.
# =====================================================================================
CONFIG = {
    # --- MediaPipe ---
    "model_complexity": 1,          # 0 = fastest, 1 = balanced (model ships with the package), 2 = most accurate
    "min_detection_confidence": 0.5,
    "min_tracking_confidence": 0.5,
    "visibility_threshold": 0.5,    # ignore a side of the body if its landmarks are less visible than this
    "smoothing_window": 5,          # frames averaged to remove angle jitter

    # --- Squat (knee angle = hip-knee-ankle, torso angle = shoulder-hip-knee) ---
    "squat_down_angle": 90,         # knee must bend below this to count as a full squat
    "squat_up_angle": 160,          # knee must straighten above this to finish the rep
    "squat_descent_angle": 140,     # below this we consider the user to be squatting
    "squat_min_torso_angle": 50,    # torso angle below this while squatting = leaning too far forward

    # --- Bicep curl (elbow angle = shoulder-elbow-wrist, arm angle = hip-shoulder-elbow) ---
    "curl_down_angle": 160,         # arm straight
    "curl_up_angle": 40,            # arm fully curled
    "curl_max_upper_arm_angle": 35, # upper arm swinging away from the torso by more than this = bad
    "curl_partial_extension": 90,   # re-curling after only opening past this (not 160) = "Extend arm fully"

    # --- Plank (body angle = shoulder-hip-ankle) ---
    "plank_min_body_angle": 165,    # 165-180 = straight body line
    "plank_max_vertical_ratio": 0.5,  # body must be roughly horizontal to count as a plank

    # --- Misc ---
    "feedback_seconds": 1.5,        # how long a one-time message like "Good rep!" stays on screen
    "default_api": "http://localhost:8080/api",
}

# MediaPipe Pose landmark numbers (33 points in total). Left/right are the PERSON's left/right.
LANDMARKS = {
    "LEFT": {"SHOULDER": 11, "ELBOW": 13, "WRIST": 15, "HIP": 23, "KNEE": 25, "ANKLE": 27},
    "RIGHT": {"SHOULDER": 12, "ELBOW": 14, "WRIST": 16, "HIP": 24, "KNEE": 26, "ANKLE": 28},
}

# Which joints each exercise needs to see.
REQUIRED_JOINTS = {
    "SQUAT": ["SHOULDER", "HIP", "KNEE", "ANKLE"],
    "BICEP_CURL": ["SHOULDER", "ELBOW", "WRIST", "HIP"],
    "PLANK": ["SHOULDER", "HIP", "ANKLE"],
}

GREEN = (80, 220, 100)
RED = (60, 60, 240)
WHITE = (240, 240, 240)
LIME = (61, 242, 181)  # BGR of the FitFlex accent colour
GREY = (170, 170, 170)


# =====================================================================================
# Exercise trackers - pure logic, no OpenCV/MediaPipe, so they are easy to test.
# Each update() receives the chosen side's joints as {"KNEE": (x, y), ...} in pixels.
# =====================================================================================
class ExerciseTracker:
    """Shared bookkeeping: reps, frames with correct/incorrect form, timing and messages."""

    name = "EXERCISE"
    label = "Exercise"

    def __init__(self, config=CONFIG):
        self.config = config
        self.reset()

    def reset(self):
        self.reps = 0
        self.tracked_frames = 0       # frames where the body was visible
        self.correct_frames = 0       # ...and the posture was correct
        self.angles = {}              # latest smoothed angles, for the overlay
        self.issue = None             # current posture problem (makes the frame "wrong")
        self.message = None           # short one-time message ("Good rep!", "Go lower")
        self.message_until = 0.0
        self.first_seen = None
        self.last_seen = None

    def say(self, text, now):
        self.message = text
        self.message_until = now + self.config["feedback_seconds"]

    def current_feedback(self, now):
        if self.issue:
            return self.issue
        if self.message and now < self.message_until:
            return self.message
        return None

    def record_frame(self, correct, now):
        self.tracked_frames += 1
        if correct:
            self.correct_frames += 1
        if self.first_seen is None:
            self.first_seen = now
        self.last_seen = now

    @property
    def correct_form_percent(self):
        if self.tracked_frames == 0:
            return 0.0
        return round(100.0 * self.correct_frames / self.tracked_frames, 1)

    @property
    def duration_seconds(self):
        if self.first_seen is None:
            return 0
        return int(round(self.last_seen - self.first_seen))

    def summary(self):
        return {
            "exercise": self.name,
            "reps": self.reps,
            "correctFormPercent": self.correct_form_percent,
            "durationSeconds": self.duration_seconds,
        }

    def update(self, points, now):
        raise NotImplementedError


class SquatTracker(ExerciseTracker):
    """
    Rep = knee angle drops below squat_down_angle (deep enough) and then rises above squat_up_angle.
    Coming back up without reaching depth is a partial rep: it is not counted and says "Go lower".
    """

    name = "SQUAT"
    label = "Squats"

    def reset(self):
        super().reset()
        self.knee = MovingAverage(self.config["smoothing_window"])
        self.torso = MovingAverage(self.config["smoothing_window"])
        self.stage = "up"
        self.squatting = False         # currently below the descent angle
        self.rep_correct_frames = 0    # correct frames inside the current rep attempt

    def update(self, points, now):
        c = self.config
        knee = self.knee.update(calculate_angle(points["HIP"], points["KNEE"], points["ANKLE"]))
        torso = self.torso.update(calculate_angle(points["SHOULDER"], points["HIP"], points["KNEE"]))
        self.angles = {"Knee": knee, "Torso": torso}

        if knee < c["squat_descent_angle"]:
            self.squatting = True
        if knee < c["squat_down_angle"]:
            self.stage = "down"

        # Form check while squatting: chest should stay up.
        self.issue = "Keep chest up" if self.squatting and torso < c["squat_min_torso_angle"] else None

        correct = self.issue is None
        self.record_frame(correct, now)
        if self.squatting and correct:
            self.rep_correct_frames += 1

        if knee > c["squat_up_angle"]:
            if self.stage == "down":
                self.reps += 1
                self.say("Good rep!", now)
            elif self.squatting:
                # Came back up without reaching depth: those frames were not correct form after all.
                self.correct_frames -= self.rep_correct_frames
                self.say("Go lower", now)
            self.stage = "up"
            self.squatting = False
            self.rep_correct_frames = 0
        return correct


class BicepCurlTracker(ExerciseTracker):
    """
    Rep = elbow straight (> curl_down_angle) -> fully curled (< curl_up_angle) -> straight again.
    Checks: upper arm must stay close to the body, and the arm must open fully between reps.
    """

    name = "BICEP_CURL"
    label = "Bicep Reps"

    def reset(self):
        super().reset()
        self.elbow = MovingAverage(self.config["smoothing_window"])
        self.upper_arm = MovingAverage(self.config["smoothing_window"])
        self.stage = None          # None until we first see a straight arm
        self.max_since_curl = 0.0

    def update(self, points, now):
        c = self.config
        elbow = self.elbow.update(calculate_angle(points["SHOULDER"], points["ELBOW"], points["WRIST"]))
        upper_arm = self.upper_arm.update(calculate_angle(points["HIP"], points["SHOULDER"], points["ELBOW"]))
        self.angles = {"Elbow": elbow, "Arm": upper_arm}

        self.issue = "Keep elbow close" if upper_arm > c["curl_max_upper_arm_angle"] else None

        # State machine:  None --straight--> "down" --curled--> "up" --straight--> "down" (+1 rep)
        if elbow > c["curl_down_angle"]:
            if self.stage == "up":
                self.reps += 1
                self.say("Good rep!", now)
            self.stage = "down"
        elif elbow < c["curl_up_angle"]:
            if self.stage == "down":
                self.stage = "up"
                self.max_since_curl = elbow
            elif self.stage == "up" and self.max_since_curl > c["curl_partial_extension"]:
                # The arm opened part-way and curled again without straightening: not a rep.
                self.say("Extend arm fully", now)
                self.issue = self.issue or "Extend arm fully"
                self.max_since_curl = elbow
        if self.stage == "up":
            # Remember how far the arm opened since the last full curl.
            self.max_since_curl = max(self.max_since_curl, elbow)

        correct = self.issue is None
        self.record_frame(correct, now)
        return correct


class PlankTracker(ExerciseTracker):
    """
    Holds a timer while the shoulder-hip-ankle line is straight (165-180 deg).
    If the hip is below that line: "Hips too low"; above it: "Hips too high".
    """

    name = "PLANK"
    label = "Plank hold"

    def reset(self):
        super().reset()
        self.body = MovingAverage(self.config["smoothing_window"])
        self.hold_seconds = 0.0
        self.last_time = None

    def update(self, points, now):
        c = self.config
        shoulder, hip, ankle = points["SHOULDER"], points["HIP"], points["ANKLE"]
        body = self.body.update(calculate_angle(shoulder, hip, ankle))
        self.angles = {"Body": body}

        dt = 0.0 if self.last_time is None else min(now - self.last_time, 1.0)
        self.last_time = now

        # A plank is roughly horizontal: the vertical gap between shoulder and ankle is small
        # compared to the horizontal length of the body.
        length = abs(ankle[0] - shoulder[0]) or 1
        if abs(ankle[1] - shoulder[1]) / length > c["plank_max_vertical_ratio"]:
            self.issue = "Get into plank position"
            self.record_frame(False, now)
            return False

        if body >= c["plank_min_body_angle"]:
            self.issue = None
            self.hold_seconds += dt
        elif hip[1] > line_y_at(hip[0], shoulder, ankle):   # image y grows downward
            self.issue = "Hips too low"
        else:
            self.issue = "Hips too high"

        correct = self.issue is None
        self.record_frame(correct, now)
        return correct

    def summary(self):
        data = super().summary()
        data["durationSeconds"] = int(round(self.hold_seconds))  # time actually held in good form
        return data


TRACKERS = {"SQUAT": SquatTracker, "BICEP_CURL": BicepCurlTracker, "PLANK": PlankTracker}
EXERCISE_ALIASES = {"squat": "SQUAT", "bicep": "BICEP_CURL", "curl": "BICEP_CURL", "plank": "PLANK"}
KEY_TO_EXERCISE = {ord("s"): "SQUAT", ord("b"): "BICEP_CURL", ord("p"): "PLANK"}


# =====================================================================================
# Landmarks -> points
# =====================================================================================
def pick_side_points(landmarks, exercise, frame_width, frame_height, config=CONFIG):
    """
    Returns {"KNEE": (x, y), ...} in pixels for the side of the body the camera sees best,
    or None if neither side is visible enough (e.g. the person is out of frame).
    """
    joints = REQUIRED_JOINTS[exercise]
    best_side, best_score = None, -1.0
    for side, ids in LANDMARKS.items():
        indices = [ids[j] for j in joints]
        if all_visible(landmarks, indices, config["visibility_threshold"]):
            score = average_visibility(landmarks, indices)
            if score > best_score:
                best_side, best_score = side, score
    if best_side is None:
        return None
    ids = LANDMARKS[best_side]
    return {j: (landmarks[ids[j]].x * frame_width, landmarks[ids[j]].y * frame_height) for j in joints}


# =====================================================================================
# Drawing
# =====================================================================================
def draw_panel(frame, tracker, feedback, correct, fps, visible):
    """Semi-transparent info panel in the top-left corner (exercise, reps, angles, status, FPS)."""
    lines = [(f"Exercise: {tracker.label}", WHITE, 0.6)]
    if tracker.name == "PLANK":
        lines.append((f"Hold: {int(tracker.hold_seconds)} s", LIME, 0.9))
    else:
        lines.append((f"{tracker.label}: {tracker.reps}", LIME, 0.9))
    # cv2.putText cannot draw the degree symbol (it shows "??"), so we write "deg".
    for name, value in tracker.angles.items():
        lines.append((f"{name}: {int(round(value))} deg", WHITE, 0.6))
    if not visible:
        lines.append(("Step back: full body not visible", GREY, 0.6))
    elif correct:
        lines.append(("Correct posture", GREEN, 0.7))
    else:
        lines.append(("Wrong posture!!", RED, 0.7))
    if feedback and visible:
        lines.append((feedback, GREEN if feedback == "Good rep!" else RED, 0.7))
    lines.append((f"FPS: {fps:.0f}", GREY, 0.5))

    height = 20 + 32 * len(lines)
    overlay = frame.copy()
    cv2.rectangle(overlay, (10, 10), (340, 10 + height), (20, 20, 20), -1)
    cv2.addWeighted(overlay, 0.65, frame, 0.35, 0, frame)  # blend = transparency
    y = 42
    for text, colour, scale in lines:
        cv2.putText(frame, text, (24, y), cv2.FONT_HERSHEY_SIMPLEX, scale, colour, 2, cv2.LINE_AA)
        y += 32

    help_text = "s: squat  b: bicep curl  p: plank  r: reset  q: quit"
    cv2.putText(frame, help_text, (12, frame.shape[0] - 14), cv2.FONT_HERSHEY_SIMPLEX, 0.5, WHITE, 1, cv2.LINE_AA)


def draw_joint_angles(frame, points, tracker):
    """Writes the main angle next to the joint it belongs to."""
    joint_for = {"Knee": "KNEE", "Torso": "HIP", "Elbow": "ELBOW", "Arm": "SHOULDER", "Body": "HIP"}
    for name, value in tracker.angles.items():
        joint = joint_for.get(name)
        if joint and joint in points:
            x, y = points[joint]
            cv2.putText(frame, f"{int(round(value))}", (int(x) + 10, int(y) - 10),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.6, LIME, 2, cv2.LINE_AA)


# =====================================================================================
# Summary + upload to the FitFlex backend
# =====================================================================================
def print_summary(summaries):
    print("\n========== FitFlex workout summary ==========")
    if not summaries:
        print("No exercise was tracked (was your full body in the frame?).")
    for s in summaries:
        amount = f"{s['durationSeconds']} s held" if s["exercise"] == "PLANK" else f"{s['reps']} reps"
        print(f"{s['exercise']:<11} {amount:<12} correct form {s['correctFormPercent']:5.1f}%   "
              f"duration {s['durationSeconds']} s")
    print("=============================================\n")


def login(api_url, email, password):
    """POST /api/user/login and return the JWT."""
    import requests  # imported here so the detector still runs if requests is missing and --upload is unused

    response = requests.post(f"{api_url}/user/login", json={"email": email, "password": password}, timeout=10)
    if response.status_code != 200:
        message = response.json().get("message", response.text) if response.content else response.status_code
        raise RuntimeError(f"Login failed: {message}")
    return response.json()["token"]


def upload_summaries(api_url, token, summaries):
    """POST each exercise summary to /api/progress/pose (source = POSE_DETECTION on the server)."""
    import requests

    headers = {"Authorization": f"Bearer {token}"}
    for s in summaries:
        body = dict(s, notes="Uploaded by pose_detector.py")
        response = requests.post(f"{api_url}/progress/pose", json=body, headers=headers, timeout=10)
        if response.status_code == 201:
            print(f"Uploaded {s['exercise']} to FitFlex progress.")
        else:
            detail = response.json().get("message") if response.content else ""
            print(f"Upload of {s['exercise']} failed ({response.status_code}): {detail}")


# =====================================================================================
# Main loop
# =====================================================================================
def parse_args(argv=None):
    parser = argparse.ArgumentParser(description="FitFlex real-time pose detection (squats, bicep curls, plank).")
    parser.add_argument("--source", default="0", help="webcam index (0, 1, ...) or path to a video/image file")
    parser.add_argument("--exercise", default="squat", choices=sorted(EXERCISE_ALIASES), help="exercise to start with")
    parser.add_argument("--no-mirror", action="store_true", help="do not flip the webcam image like a mirror")
    parser.add_argument("--no-display", action="store_true", help="process without opening a window (testing)")
    parser.add_argument("--max-frames", type=int, default=0, help="stop after this many frames (0 = no limit)")
    parser.add_argument("--upload", action="store_true", help="send the summary to FitFlex when you quit")
    parser.add_argument("--api", default=CONFIG["default_api"], help="FitFlex API base URL")
    parser.add_argument("--email", help="FitFlex user email (with --upload)")
    parser.add_argument("--password", help="FitFlex password (with --upload)")
    parser.add_argument("--token", help="an existing FitFlex JWT instead of email/password")
    args = parser.parse_args(argv)
    if args.upload and not args.token and not (args.email and args.password):
        parser.error("--upload needs --token, or --email and --password")
    return args


def open_source(source):
    """Webcam if the source is a number, otherwise a file path."""
    if source.isdigit():
        # CAP_DSHOW opens Windows webcams much faster than the default backend.
        backend = cv2.CAP_DSHOW if sys.platform.startswith("win") else cv2.CAP_ANY
        return cv2.VideoCapture(int(source), backend), True
    return cv2.VideoCapture(source), False


def run(args):
    import mediapipe as mp  # heavy import, only needed when actually running

    capture, is_webcam = open_source(args.source)
    if not capture.isOpened():
        print(f"Could not open video source '{args.source}'. Try --source 1 for another webcam, "
              "or close other apps using the camera.")
        return []

    print("Tip: stand SIDEWAYS to the camera, 2-3 m away, with your whole body in the frame.\n"
          "     Joint angles are measured in 2D, so a leg or arm pointing at the camera looks straighter than it is.")
    print("Keys: s = squat, b = bicep curl, p = plank, r = reset, q = quit")

    trackers = {}
    exercise = EXERCISE_ALIASES[args.exercise]
    mp_pose, mp_drawing = mp.solutions.pose, mp.solutions.drawing_utils
    last_frame_time, fps, frames = time.monotonic(), 0.0, 0

    with mp_pose.Pose(model_complexity=CONFIG["model_complexity"],
                      min_detection_confidence=CONFIG["min_detection_confidence"],
                      min_tracking_confidence=CONFIG["min_tracking_confidence"]) as pose:
        while True:
            ok, frame = capture.read()
            if not ok:
                break  # end of the video file, or the camera disconnected
            if is_webcam and not args.no_mirror:
                frame = cv2.flip(frame, 1)

            now = time.monotonic()
            fps = 0.9 * fps + 0.1 * (1.0 / max(now - last_frame_time, 1e-6))  # smoothed FPS
            last_frame_time = now
            frames += 1

            tracker = trackers.setdefault(exercise, TRACKERS[exercise]())
            # MediaPipe expects RGB images; OpenCV gives BGR.
            results = pose.process(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))

            points, correct, visible = None, False, False
            if results.pose_landmarks:
                mp_drawing.draw_landmarks(
                    frame, results.pose_landmarks, mp_pose.POSE_CONNECTIONS,
                    mp_drawing.DrawingSpec(color=LIME, thickness=2, circle_radius=3),
                    mp_drawing.DrawingSpec(color=WHITE, thickness=2))
                h, w = frame.shape[:2]
                points = pick_side_points(results.pose_landmarks.landmark, exercise, w, h)
            if points:
                visible = True
                correct = tracker.update(points, now)
                draw_joint_angles(frame, points, tracker)

            draw_panel(frame, tracker, tracker.current_feedback(now), correct, fps, visible)

            if args.no_display:
                if frames % 30 == 0:
                    print(f"frame {frames}: {tracker.label} {tracker.reps}, angles "
                          + ", ".join(f"{k} {v:.0f}" for k, v in tracker.angles.items()))
            else:
                cv2.imshow("FitFlex Pose Detection", frame)
                key = cv2.waitKey(1) & 0xFF
                if key == ord("q"):
                    break
                if key in KEY_TO_EXERCISE:
                    exercise = KEY_TO_EXERCISE[key]
                elif key == ord("r"):
                    trackers.get(exercise, tracker).reset()
            if args.max_frames and frames >= args.max_frames:
                break

    capture.release()
    if not args.no_display:
        cv2.destroyAllWindows()

    # Only report exercises that actually saw the body.
    return [t.summary() for t in trackers.values() if t.tracked_frames > 0]


def main(argv=None):
    args = parse_args(argv)
    summaries = run(args)
    print_summary(summaries)

    if args.upload and summaries:
        try:
            token = args.token or login(args.api, args.email, args.password)
            upload_summaries(args.api, token, summaries)
        except Exception as error:  # network down, wrong password ... never crash at the very end
            print(f"Could not upload to FitFlex: {error}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
