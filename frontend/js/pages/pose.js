/*
 * Pose detection page: the explanation is static HTML. This script fills in
 * "My pose sessions" (POSE_DETECTION entries from GET /api/progress/my) for logged-in users.
 */
document.addEventListener("DOMContentLoaded", async () => {
  const el = document.getElementById("pose-history");
  const user = auth.getUser();

  if (!user) {
    el.innerHTML = ui.emptyHtml("fa-lock", "Log in to see the pose sessions you uploaded.",
      '<a href="login.html?redirect=pose.html" class="btn btn-primary btn-sm">Log in</a>');
    return;
  }
  if (user.role !== "USER") {
    el.innerHTML = ui.emptyHtml("fa-circle-info", "Pose session history is shown for member accounts.");
    return;
  }

  ui.showLoader(el);
  try {
    const entries = await api.get("/progress/my");
    const sessions = entries.filter((e) => e.source === "POSE_DETECTION").reverse(); // newest first
    if (!sessions.length) {
      el.innerHTML = ui.emptyHtml("fa-person-rays", "No pose sessions yet. Run the Python detector with --upload to record one.");
      return;
    }
    el.innerHTML = `<table>
      <thead><tr><th>Date</th><th>Exercise</th><th>Reps</th><th>Correct form</th><th>Duration</th><th>Calories</th></tr></thead>
      <tbody>${sessions.map((e) => `<tr>
        <td class="nowrap">${ui.formatDate(e.date)}</td>
        <td><strong>${ui.formatEnum(e.exercise)}</strong></td>
        <td>${e.reps ?? "-"}</td>
        <td><span class="badge ${e.correctFormPercent >= 80 ? "badge-confirmed" : e.correctFormPercent >= 60 ? "badge-requested" : "badge-cancelled"}">${e.correctFormPercent ?? "-"}%</span></td>
        <td>${e.workoutMinutes ?? 0} min</td>
        <td>${e.caloriesBurned ?? "-"}</td></tr>`).join("")}</tbody></table>`;
  } catch (error) {
    el.innerHTML = ui.errorHtml(error.message);
  }
});
