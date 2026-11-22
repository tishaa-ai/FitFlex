/* User dashboard: one call to GET /api/dashboard/user fills every card on the page. */
document.addEventListener("DOMContentLoaded", async () => {
  const user = auth.requireRole("USER");
  if (!user) return;

  const sections = ["stats", "upcoming-sessions", "diet-summary", "workout-plans", "resources"];
  sections.forEach((id) => ui.showLoader(document.getElementById(id)));

  try {
    const data = await api.get("/dashboard/user");
    document.getElementById("welcome").textContent = `Welcome back, ${data.fullName.split(" ")[0]}!`;
    renderStats(data.summary);
    renderSessions(data.upcomingSessions);
    renderDiet(data.latestDietPlan);
    renderPlans(data.workoutPlans);
    renderResources(data.resources);
  } catch (error) {
    sections.forEach((id) => (document.getElementById(id).innerHTML = ""));
    document.getElementById("stats").innerHTML = `<div style="grid-column: 1 / -1">${ui.errorHtml(error.message)}</div>`;
  }
});

function statCard(icon, value, label) {
  return `<div class="card stat-card reveal visible"><div class="card-icon"><i class="fa-solid ${icon}"></i></div>
    <div><div class="stat-value">${value}</div><div class="stat-label">${label}</div></div></div>`;
}

function renderStats(s) {
  const weight = s.weightChangeKg === null ? "-" : `${s.weightChangeKg > 0 ? "+" : ""}${s.weightChangeKg} kg`;
  const form = s.averageFormAccuracy === null ? "-" : `${s.averageFormAccuracy}%`;
  document.getElementById("stats").innerHTML =
    statCard("fa-fire", `${s.currentStreak} day${s.currentStreak === 1 ? "" : "s"}`, "Current streak") +
    statCard("fa-dumbbell", s.totalWorkouts, `Workouts (${s.workoutsThisWeek} this week)`) +
    statCard("fa-weight-scale", weight, "Weight change") +
    statCard("fa-person-rays", form, "Avg. pose form accuracy");
}

function renderSessions(sessions) {
  const el = document.getElementById("upcoming-sessions");
  if (!sessions.length) {
    el.innerHTML = ui.emptyHtml("fa-calendar-xmark", "No upcoming sessions.", '<a href="trainers.html" class="btn btn-primary btn-sm">Book a trainer</a>');
    return;
  }
  el.innerHTML = `<ul class="list">${sessions.map((s) => `
    <li class="list-item">
      <div>
        <div class="title">${ui.escape(s.title)}</div>
        <div class="meta">${ui.sessionWhen(s)} &middot; with ${ui.escape(s.trainerName)}
          ${s.sessionType === "GROUP" ? '<span class="badge badge-neutral">Group</span>' : ""}</div>
      </div>
      <div class="row">${ui.statusBadge(s.status)}
        ${s.meetingLink ? `<a href="${ui.escape(s.meetingLink)}" target="_blank" rel="noopener" class="btn btn-success btn-sm"><i class="fa-solid fa-video"></i> Join</a>` : ""}</div>
    </li>`).join("")}</ul>`;
}

function renderDiet(plan) {
  const el = document.getElementById("diet-summary");
  if (!plan) {
    el.innerHTML = ui.emptyHtml("fa-bowl-food", "You have not generated a diet plan yet.", '<a href="nutrition.html" class="btn btn-primary btn-sm">Create my plan</a>');
    return;
  }
  el.innerHTML = `
    <div class="grid grid-3">
      <div><div class="stat-label">BMR</div><div class="stat-value" style="font-size:1.3rem">${Math.round(plan.bmr)}</div><div class="muted small">kcal/day</div></div>
      <div><div class="stat-label">TDEE</div><div class="stat-value" style="font-size:1.3rem">${Math.round(plan.tdee)}</div><div class="muted small">kcal/day</div></div>
      <div><div class="stat-label">Target</div><div class="stat-value accent" style="font-size:1.3rem">${plan.targetCalories}</div><div class="muted small">kcal/day</div></div>
    </div>
    <div class="row mt-2">
      <span class="chip">${ui.formatEnum(plan.goal)}</span><span class="chip">${ui.escape(plan.dietType)}</span>
      <span class="chip">${plan.mealsPerDay} meals/day</span>
      <span class="chip">${plan.source === "SPOONACULAR" ? "Spoonacular" : "FitFlex dataset"}</span>
    </div>
    <p class="muted small mb-0">Created ${ui.formatDateTime(plan.createdAt)} &middot; <a href="nutrition.html">View meals</a></p>`;
}

function renderPlans(plans) {
  const el = document.getElementById("workout-plans");
  el.innerHTML = plans.length
    ? plans.map((p, i) => ui.workoutPlanHtml(p, { open: i === 0 })).join("")
    : ui.emptyHtml("fa-dumbbell", "No workout plans yet. Your trainer can create one for you after you book a session.");
}

function renderResources(resources) {
  const el = document.getElementById("resources");
  if (!resources.length) {
    el.innerHTML = ui.emptyHtml("fa-folder-open", "No files shared with you yet.");
    return;
  }
  const icons = { VIDEO: "fa-file-video", DOCUMENT: "fa-file-lines", PLAN: "fa-clipboard-list" };
  el.innerHTML = `<ul class="list">${resources.map((r) => `
    <li class="list-item">
      <div class="row"><i class="fa-solid ${icons[r.type] || "fa-file"} accent" style="font-size:1.4rem"></i>
        <div><div class="title">${ui.escape(r.title)}</div>
          <div class="meta">${ui.escape(r.trainerName)} &middot; ${ui.formatBytes(r.sizeBytes)} &middot; ${ui.formatDate(r.uploadedAt)}</div></div></div>
      <button class="btn btn-ghost btn-sm" data-download="${r.id}" data-name="${ui.escape(r.originalFileName)}"><i class="fa-solid fa-download"></i> Download</button>
    </li>`).join("")}</ul>`;

  el.querySelectorAll("[data-download]").forEach((button) => {
    button.addEventListener("click", async () => {
      ui.setLoading(button, true, "...");
      try {
        await api.download(`/resources/${button.dataset.download}/download`, button.dataset.name);
      } catch (error) {
        ui.toastError(error);
      } finally {
        ui.setLoading(button, false);
      }
    });
  });
}
