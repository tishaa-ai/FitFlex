"""Unit tests for pose_utils.py. Run:  python -m pytest -v"""

from types import SimpleNamespace

import pytest

from pose_utils import MovingAverage, all_visible, calculate_angle, landmark_to_pixel, line_y_at


# ---------- calculate_angle: the cases from the spec ----------

def test_right_angle_is_90():
    assert calculate_angle((0, 1), (0, 0), (1, 0)) == pytest.approx(90.0)


def test_straight_line_is_180():
    # hip, knee, ankle in one vertical line = straight leg
    assert calculate_angle((0, 0), (0, 1), (0, 2)) == pytest.approx(180.0)


def test_45_degrees():
    assert calculate_angle((1, 0), (0, 0), (1, 1)) == pytest.approx(45.0)


# ---------- calculate_angle: extra properties ----------

def test_order_of_outer_points_does_not_matter():
    assert calculate_angle((3, 1), (0, 0), (1, 4)) == pytest.approx(calculate_angle((1, 4), (0, 0), (3, 1)))


def test_reflex_angle_is_folded_into_0_180():
    # The raw atan2 difference here is 270 deg; the joint angle is 90 deg.
    assert calculate_angle((0, -1), (0, 0), (-1, 0)) == pytest.approx(90.0)


def test_works_with_pixel_coordinates_and_y_pointing_down():
    # Knee bent to 135 deg in image coordinates (y grows downward)
    hip, knee, ankle = (300, 200), (400, 300), (400, 441.42)
    assert calculate_angle(hip, knee, ankle) == pytest.approx(135.0, abs=0.1)


def test_zero_angle_when_both_points_on_same_ray():
    assert calculate_angle((2, 0), (0, 0), (5, 0)) == pytest.approx(0.0)


# ---------- other helpers ----------

def test_landmark_to_pixel_scales_fractions_to_frame_size():
    landmark = SimpleNamespace(x=0.5, y=0.25, visibility=0.9)
    assert landmark_to_pixel(landmark, 640, 480) == (320, 120)


def test_all_visible_uses_threshold():
    marks = [SimpleNamespace(visibility=v) for v in (0.9, 0.6, 0.3)]
    assert all_visible(marks, [0, 1], 0.5)
    assert not all_visible(marks, [0, 2], 0.5)


def test_moving_average_smooths_and_forgets_old_values():
    avg = MovingAverage(size=3)
    assert avg.value is None
    assert avg.update(10) == 10
    avg.update(20)
    assert avg.update(30) == pytest.approx(20)
    assert avg.update(40) == pytest.approx(30)  # the 10 dropped out


def test_moving_average_rejects_bad_size():
    with pytest.raises(ValueError):
        MovingAverage(0)


def test_line_y_at_interpolates():
    assert line_y_at(50, (0, 100), (100, 200)) == pytest.approx(150)
