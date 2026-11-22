"""
Tests for the rep counting and form checks in pose_detector.py, using synthetic poses built
at exact joint angles (no webcam or MediaPipe needed). Run:  python -m pytest -v
"""

import math

import pytest

from pose_detector import BicepCurlTracker, PlankTracker, SquatTracker, pick_side_points

# Smoothing off (window 1) so each frame's angle is exactly the one we construct.
from pose_detector import CONFIG

TEST_CONFIG = dict(CONFIG, smoothing_window=1)


def rotate(vector, degrees):
    rad = math.radians(degrees)
    x, y = vector
    return (x * math.cos(rad) - y * math.sin(rad), x * math.sin(rad) + y * math.cos(rad))


def point_at_angle(vertex, other, angle, length=100):
    """A point P such that angle(P, vertex, other) == angle."""
    dx, dy = other[0] - vertex[0], other[1] - vertex[1]
    norm = math.hypot(dx, dy)
    rx, ry = rotate((dx / norm, dy / norm), angle)
    return (vertex[0] + rx * length, vertex[1] + ry * length)


def squat_pose(knee_angle, torso_angle=170):
    ankle, knee = (300, 450), (300, 350)
    hip = point_at_angle(knee, ankle, knee_angle)
    shoulder = point_at_angle(hip, knee, torso_angle)
    return {"ANKLE": ankle, "KNEE": knee, "HIP": hip, "SHOULDER": shoulder}


def curl_pose(elbow_angle, upper_arm_angle=10):
    hip, shoulder = (300, 400), (300, 250)
    elbow = point_at_angle(shoulder, hip, upper_arm_angle)
    wrist = point_at_angle(elbow, shoulder, elbow_angle)
    return {"HIP": hip, "SHOULDER": shoulder, "ELBOW": elbow, "WRIST": wrist}


def feed(tracker, angles, pose_fn, **kwargs):
    t = 0.0
    for angle in angles:
        tracker.update(pose_fn(angle, **kwargs), t)
        t += 1 / 30
    return tracker


def test_synthetic_pose_helper_builds_exact_angles():
    from pose_utils import calculate_angle
    p = squat_pose(85, torso_angle=60)
    assert calculate_angle(p["HIP"], p["KNEE"], p["ANKLE"]) == pytest.approx(85)
    assert calculate_angle(p["SHOULDER"], p["HIP"], p["KNEE"]) == pytest.approx(60)


# ---------------- squat ----------------

def test_squat_counts_full_reps():
    down_up = [175, 150, 120, 95, 80, 95, 130, 165, 175]
    tracker = feed(SquatTracker(TEST_CONFIG), down_up * 3, squat_pose)
    assert tracker.reps == 3
    assert tracker.correct_form_percent == 100.0


def test_shallow_squat_is_not_counted_and_says_go_lower():
    tracker = feed(SquatTracker(TEST_CONFIG), [175, 150, 120, 110, 130, 170], squat_pose)
    assert tracker.reps == 0
    assert tracker.current_feedback(0.2) == "Go lower"
    assert tracker.correct_form_percent < 100.0


def test_leaning_forward_is_wrong_posture():
    tracker = SquatTracker(TEST_CONFIG)
    correct = tracker.update(squat_pose(85, torso_angle=35), 0.0)
    assert correct is False
    assert tracker.issue == "Keep chest up"


def test_standing_upright_is_correct():
    tracker = SquatTracker(TEST_CONFIG)
    assert tracker.update(squat_pose(178, torso_angle=177), 0.0) is True


# ---------------- bicep curl ----------------

def test_curl_counts_straight_curl_straight():
    cycle = [170, 120, 60, 30, 60, 120, 170]
    tracker = feed(BicepCurlTracker(TEST_CONFIG), cycle * 4, curl_pose)
    assert tracker.reps == 4


def test_curl_needs_full_curl():
    tracker = feed(BicepCurlTracker(TEST_CONFIG), [170, 120, 70, 120, 170], curl_pose)
    assert tracker.reps == 0


def test_curl_partial_extension_warns_and_does_not_count():
    tracker = feed(BicepCurlTracker(TEST_CONFIG), [170, 90, 30, 80, 120, 80, 30], curl_pose)
    assert tracker.reps == 0
    assert tracker.current_feedback(0.2) == "Extend arm fully"


def test_elbow_drifting_forward_is_flagged():
    tracker = BicepCurlTracker(TEST_CONFIG)
    assert tracker.update(curl_pose(90, upper_arm_angle=60), 0.0) is False
    assert tracker.issue == "Keep elbow close"


# ---------------- plank ----------------

def plank_pose(hip_offset):
    """Horizontal body; positive hip_offset moves the hip DOWN in the image (sagging)."""
    return {"SHOULDER": (100, 300), "HIP": (300, 300 + hip_offset), "ANKLE": (500, 300)}


def test_plank_hold_timer_runs_only_with_good_form():
    tracker = PlankTracker(TEST_CONFIG)
    for i in range(31):  # one second of good plank at 30 fps
        tracker.update(plank_pose(0), i / 30)
    assert tracker.hold_seconds == pytest.approx(1.0, abs=0.05)
    assert tracker.issue is None


def test_plank_hips_too_low_and_too_high():
    low = PlankTracker(TEST_CONFIG)
    low.update(plank_pose(60), 0.0)
    assert low.issue == "Hips too low"
    high = PlankTracker(TEST_CONFIG)
    high.update(plank_pose(-60), 0.0)
    assert high.issue == "Hips too high"


def test_standing_is_not_a_plank():
    tracker = PlankTracker(TEST_CONFIG)
    tracker.update({"SHOULDER": (300, 100), "HIP": (300, 300), "ANKLE": (300, 500)}, 0.0)
    assert tracker.issue == "Get into plank position"


# ---------------- side selection ----------------

class Mark:
    def __init__(self, x, y, visibility):
        self.x, self.y, self.visibility = x, y, visibility


def test_pick_side_uses_the_more_visible_side():
    marks = [Mark(0.5, 0.5, 0.0) for _ in range(33)]
    for i in (11, 23, 25, 27):          # left side clearly visible
        marks[i] = Mark(0.4, 0.1 * (i - 10) / 2, 0.95)
    for i in (12, 24, 26, 28):          # right side half hidden
        marks[i] = Mark(0.6, 0.5, 0.55)
    points = pick_side_points(marks, "SQUAT", 640, 480)
    assert points["KNEE"][0] == pytest.approx(0.4 * 640)


def test_pick_side_returns_none_when_body_not_visible():
    marks = [Mark(0.5, 0.5, 0.1) for _ in range(33)]
    assert pick_side_points(marks, "SQUAT", 640, 480) is None
