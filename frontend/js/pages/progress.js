/*
 * Progress page (USER): log form, summary cards, three Chart.js charts
 * (weight over time, workout minutes per week, pose form accuracy) and a table of recent entries.
 */
const charts = {};

document.addEventListener("DOMContentLoaded", () => {
  const user = auth.requireRole("USER");
  if (!user) return;

  const form = document.getElementById("progress-form");
  form.date.value = ui.todayIso();
  form.date.max = ui.todayIso();

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!ui.validateForm(form)) return;
    if (!form.weightKg.value && !form.workoutMinutes.value) {
      ui.setFieldError(form.weightKg, "Enter weight or workout minutes");
      ui.setFieldError(form.workoutMinutes, "Enter weight or workout minutes");
      return;
    }
    const data = ui.formData(form);
    ["weightKg", "workoutMinutes", "caloriesBurned"].forEach((k) => {
      if (data[k] !== null) data[k] = Number(data[k]);
    });

    const button = form.querySelector("button[type=submit]");
    ui.setLoading(button, true, "Saving...");
    try {
      await api.post("/progress", data);
      ui.toast("Entry saved. Great work!", "success");
      form.reset();
      form.date.value = ui.todayIso();
      loadAll();
    } catch (error) {
      ui.showServerErrors(form, error);
      ui.toastError(error);
    } finally {
      ui.setLoading(button, false);
    }
  });

  loadAll();
});

async function loadAll() {
  ui.showLoader(document.getElementById("summary"));
  ui.showLoader(document.getElementById("entries"));
  try {
    const [summary, entries] = await Promise.all([api.get("/progress/summary"), api.get("/progress/my")]);
    renderSummary(summary);
    renderCharts(entries);
    renderTable(entries);
  } catch (error) {
    document.getElementById("summary").innerHTML = `<div style="grid-column: 1 / -1">${ui.errorHtml(error.message)}</div>`;
    document.getElementById("entries").innerHTML = "";
  }
}

function renderSummary(s) {
  const card = (icon, value, label) => `<div class="card stat-card"><div class="card-icon"><i class="fa-solid ${icon}"></i></div>
    <div><div class="stat-value">${value}</div><div class="stat-label">${label}</div></div></div>`;
  const weight = s.weightChangeKg === null ? "-" : `${s.weightChangeKg > 0 ? "+" : ""}${s.weightChangeKg} kg`;
  document.getElementById("summary").innerHTML =
    card("fa-fire", `${s.currentStreak} days`, "Current streak") +
    card("fa-dumbbell", s.totalWorkouts, `Workouts &middot; ${s.totalWorkoutMinutes} min total`) +
    card("fa-weight-scale", weight, s.currentWeightKg ? `Now ${s.currentWeightKg} kg` : "Weight change") +
    card("fa-person-rays", s.averageFormAccuracy === null ? "-" : s.averageFormAccuracy + "%",
      `Form accuracy &middot; ${s.poseSessions} pose sessions`);
}

// ===================== charts =====================

const GRID = "rgba(255,255,255,0.06)";
const TEXT = "#9aa2ad";

function baseOptions(yLabel) {
  return {
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      x: { grid: { color: GRID }, ticks: { color: TEXT, maxRotation: 0, autoSkip: true, maxTicksLimit: 8 } },
      y: { grid: { color: GRID }, ticks: { color: TEXT }, title: { display: true, text: yLabel, color: TEXT } },
    },
  };
}

function drawChart(id, config) {
  if (charts[id]) charts[id].destroy();
  charts[id] = new Chart(document.getElementById(id), config);
}

/** Monday of the week a date belongs to, as YYYY-MM-DD (used to group minutes per week). */
function weekStart(isoDate) {
  const d = new Date(isoDate + "T00:00:00");
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().substring(0, 10);
}

function renderCharts(entries) {
  if (typeof Chart === "undefined") return; // CDN blocked: the table still shows the data

  // 1. Weight over time: the last weight logged on each day.
  const weightByDay = new Map();
  entries.filter((e) => e.weightKg !== null).forEach((e) => weightByDay.set(e.date, e.weightKg));
  drawChart("weight-chart", {
    type: "line",
    data: {
      labels: [...weightByDay.keys()].map(ui.formatDate),
      datasets: [{ data: [...weightByDay.values()], borderColor: "#b5f23d", backgroundColor: "rgba(181,242,61,0.12)", fill: true, tension: 0.3, pointRadius: 3 }],
    },
    options: baseOptions("kg"),
  });

  // 2. Workout minutes summed per week (Monday start).
  const minutesByWeek = new Map();
  entries.forEach((e) => {
    if (!e.workoutMinutes) return;
    const week = weekStart(e.date);
    minutesByWeek.set(week, (minutesByWeek.get(week) || 0) + e.workoutMinutes);
  });
  const weeks = [...minutesByWeek.keys()].sort();
  drawChart("minutes-chart", {
    type: "bar",
    data: {
      labels: weeks.map((w) => "Week of " + ui.formatDate(w)),
      datasets: [{ data: weeks.map((w) => minutesByWeek.get(w)), backgroundColor: "#4fb3ff", borderRadius: 6, maxBarThickness: 48 }],
    },
    options: baseOptions("minutes"),
  });

  // 3. Pose detection form accuracy per session.
  const pose = entries.filter((e) => e.source === "POSE_DETECTION" && e.correctFormPercent !== null);
  const formOptions = baseOptions("% correct form");
  formOptions.scales.y.min = 0;
  formOptions.scales.y.max = 100;
  formOptions.plugins.tooltip = { callbacks: { afterLabel: (c) => `${ui.formatEnum(pose[c.dataIndex].exercise)}, ${pose[c.dataIndex].reps} reps` } };
  drawChart("form-chart", {
    type: "line",
    data: {
      labels: pose.map((e) => ui.formatDate(e.date)),
      datasets: [{ data: pose.map((e) => e.correctFormPercent), borderColor: "#ffb547", backgroundColor: "rgba(255,181,71,0.12)", fill: true, tension: 0.3, pointRadius: 4 }],
    },
    options: formOptions,
  });
}

// ===================== table =====================

function renderTable(entries) {
  const el = document.getElementById("entries");
  if (!entries.length) {
    el.innerHTML = ui.emptyHtml("fa-chart-line", "No entries yet. Log your first workout above!");
    return;
  }
  const recent = [...entries].reverse().slice(0, 20); // newest first
  el.innerHTML = `<table>
    <thead><tr><th>Date</th><th>Source</th><th>Weight</th><th>Minutes</th><th>Calories</th><th>Exercise</th><th>Reps</th><th>Form</th><th>Notes</th></tr></thead>
    <tbody>${recent.map((e) => `<tr>
      <td class="nowrap">${ui.formatDate(e.date)}</td>
      <td>${e.source === "POSE_DETECTION" ? '<span class="badge badge-accent"><i class="fa-solid fa-person-rays"></i> Pose</span>' : '<span class="badge badge-neutral">Manual</span>'}</td>
      <td>${e.weightKg !== null ? e.weightKg + " kg" : "-"}</td>
      <td>${e.workoutMinutes ?? "-"}</td>
      <td>${e.caloriesBurned ?? "-"}</td>
      <td>${e.exercise ? ui.formatEnum(e.exercise) : "-"}</td>
      <td>${e.reps ?? "-"}</td>
      <td>${e.correctFormPercent !== null ? e.correctFormPercent + "%" : "-"}</td>
      <td class="muted">${ui.escape(e.notes || "")}</td></tr>`).join("")}</tbody></table>`;
}
