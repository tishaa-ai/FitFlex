"""
pose_utils.py - small, pure helper functions used by the pose detector and the notebook.

Nothing in this file needs a webcam or MediaPipe, so every function can be unit tested
(see test_pose_utils.py).
"""

from collections import deque
import math

import numpy as np


def calculate_angle(a, b, c):
    """
    Angle ABC in degrees (the angle *at point b*), always between 0 and 180.

    a, b, c are (x, y) points, e.g. hip, knee, ankle -> the knee angle.

    How it works:
      atan2(dy, dx) gives the direction of a line in radians.
      direction(b -> c) - direction(b -> a) is the angle between the two lines.
      We convert to degrees and fold it into 0..180 because a joint angle of 200 deg
      is really the same joint bent to 160 deg measured from the other side.

    Worked example: a=(0,1), b=(0,0), c=(1,0)
      direction(b->c) = atan2(0, 1) = 0 deg
      direction(b->a) = atan2(1, 0) = 90 deg
      |0 - 90| = 90 deg  -> a right angle.
    """
    a, b, c = np.asarray(a, dtype=float), np.asarray(b, dtype=float), np.asarray(c, dtype=float)
    radians = math.atan2(c[1] - b[1], c[0] - b[0]) - math.atan2(a[1] - b[1], a[0] - b[0])
    angle = abs(math.degrees(radians))
    if angle > 180.0:
        angle = 360.0 - angle
    return angle


def landmark_to_pixel(landmark, frame_width, frame_height):
    """
    MediaPipe gives x and y as fractions of the image size (0.0 - 1.0).
    Multiply by the frame size to get pixel coordinates for drawing and angle maths.
    Using pixels (not fractions) matters for angles: a 640x480 frame is not square.
    """
    return int(landmark.x * frame_width), int(landmark.y * frame_height)


def is_visible(landmark, threshold=0.5):
    """MediaPipe's `visibility` (0..1) says how sure it is that the point is in view and not hidden."""
    return getattr(landmark, "visibility", 1.0) >= threshold


def all_visible(landmarks, indices, threshold=0.5):
    """True if every landmark in `indices` is visible enough."""
    return all(is_visible(landmarks[i], threshold) for i in indices)


def average_visibility(landmarks, indices):
    """Mean visibility of a group of landmarks; used to pick the side of the body facing the camera."""
    return sum(getattr(landmarks[i], "visibility", 1.0) for i in indices) / len(indices)


def line_y_at(x, line_start, line_end):
    """
    The y value of the straight line start->end at horizontal position x.
    The plank check compares the hip's y with the shoulder-ankle line at the hip's x:
    image y grows DOWNWARD, so hip_y > line_y means the hips sag below the line.
    """
    (x1, y1), (x2, y2) = line_start, line_end
    if x2 == x1:  # vertical line: no single y, fall back to the midpoint
        return (y1 + y2) / 2
    return y1 + (y2 - y1) * (x - x1) / (x2 - x1)


class MovingAverage:
    """
    Smooths a noisy number (like an angle that jitters by a few degrees every frame) by
    averaging the last `size` values. Small windows react fast; big windows are smoother.
    """

    def __init__(self, size=5):
        if size < 1:
            raise ValueError("size must be at least 1")
        self.values = deque(maxlen=size)

    def update(self, value):
        self.values.append(float(value))
        return self.value

    @property
    def value(self):
        return sum(self.values) / len(self.values) if self.values else None

    def reset(self):
        self.values.clear()
