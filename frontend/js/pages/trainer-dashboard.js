/*
 * Trainer dashboard. Tabs: overview (booking requests + upcoming), clients, availability,
 * group session form, workout plan builder (dynamic exercise rows), resources upload and profile.
 */
const DAY_ORDER = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
let clients = [];

document.addEventListener("DOMContentLoaded", () => {
  const user = auth.requireRole("TRAINER");
  if (!user) return;

  ui.initTabs(document.getElementById("dashboard-tabs"));
  loadDashboard();
  loadAvailability();
  loadPlans();
  loadResources();
  setupAvailabilityForm();
  setupGroupForm();
  setupPlanForm();
  setupUploadForm();
  setupProfileForm();
});

// ===================== overview =====================

async function loadDashboard() {
  ["stats", "pending-requests", "upcoming-sessions", "clients"].forEach((id) => ui.showLoader(document.getElementById(id)));
  try {
    const data = await api.get("/dashboard/trainer");
    const p = data.profile;
    document.getElementById("welcome").textContent = `Hello, ${p.fullName.split(" ")[0]}!`;
    document.getElementById("welcome-sub").innerHTML =
      `${ui.escape(p.specialization)} coach &middot; ${ui.formatMoney(p.pricePerSession)} per session &middot; ${ui.ratingHtml(p.rating)}`;

    const stat = (icon, value, label) => `<div class="card stat-card"><div class="card-icon"><i class="fa-solid ${icon}"></i></div>
      <div><div class="stat-value">${value}</div><div class="stat-label">${label}</div></div></div>`;
    document.getElementById("stats").innerHTML =
      stat("fa-bell", data.pendingRequestCount, "Booking requests") +
      stat("fa-video", data.upcomingSessionCount, "Upcoming sessions") +
      stat("fa-users", data.clientCount, "Clients") +
      stat("fa-folder-open", data.resourceCount, "Shared files");

    renderSessionList("pending-requests", data.pendingRequests, true);
    renderSessionList("upcoming-sessions", data.upcomingSessions, false);
    renderClients(data.clients);
    fillProfileForm(p);
  } catch (error) {
    document.getElementById("stats").innerHTML = `<div style="grid-column: 1 / -1">${ui.errorHtml(error.message)}</div>`;
    ["pending-requests", "upcoming-sessions", "clients"].forEach((id) => (document.getElementById(id).innerHTML = ""));
  }
}

function renderSessionList(elementId, sessions, isRequestList) {
  const el = document.getElementById(elementId);
  if (!sessions.length) {
    el.innerHTML = ui.emptyHtml(isRequestList ? "fa-inbox" : "fa-calendar-xmark",
      isRequestList ? "No pending booking requests." : "No confirmed upcoming sessions.");
    return;
  }
  el.innerHTML = `<ul class="list">${sessions.map((s) => `
    <li class="list-item">
      <div>
        <div class="title">${ui.escape(s.title)} ${s.sessionType === "GROUP" ? `<span class="badge badge-neutral">Group ${s.participantCount}/${s.maxParticipants}</span>` : ""}</div>
        <div class="meta">${ui.sessionWhen(s)}${s.userName ? ` &middot; ${ui.escape(s.userName)}` : ""}</div>
        ${s.description ? `<div class="meta"><i class="fa-regular fa-comment"></i> ${ui.escape(s.description)}</div>` : ""}
      </div>
      <div class="btn-group">
        ${isRequestList
          ? `<button class="btn btn-success btn-sm" data-status="CONFIRMED" data-id="${s.id}"><i class="fa-solid fa-check"></i> Confirm</button>
             <button class="btn btn-danger btn-sm" data-status="CANCELLED" data-id="${s.id}"><i class="fa-solid fa-xmark"></i> Decline</button>`
          : `${s.meetingLink ? `<a class="btn btn-success btn-sm" href="${ui.escape(s.meetingLink)}" target="_blank" rel="noopener"><i class="fa-solid fa-video"></i> Start</a>` : ""}
             <button class="btn btn-danger btn-sm" data-status="CANCELLED" data-id="${s.id}">Cancel</button>`}
      </div>
    </li>`).join("")}</ul>`;

  el.querySelectorAll("[data-status]").forEach((button) => {
    button.addEventListener("click", () => changeStatus(button));
  });
}

async function changeStatus(button) {
  const status = button.dataset.status;
  if (status === "CANCELLED" && !confirm("Cancel this session? The user will see it as cancelled.")) return;
  ui.setLoading(button, true, "...");
  try {
    await api.put(`/sessions/${button.dataset.id}/status`, { status });
    ui.toast(status === "CONFIRMED" ? "Session confirmed. A Jitsi link was created." : "Session cancelled.", "success");
    loadDashboard();
  } catch (error) {
    ui.toastError(error);
    ui.setLoading(button, false);
  }
}

// ===================== clients =====================

function renderClients(list) {
  clients = list;
  const el = document.getElementById("clients");
  el.innerHTML = list.length
    ? `<div class="table-wrap"><table>
        <thead><tr><th>Name</th><th>Email</th><th>Goal</th><th>Level</th><th>Age</th><th>Height / Weight</th></tr></thead>
        <tbody>${list.map((c) => `<tr>
          <td><div class="row">${ui.avatarHtml(c.fullName, null, "avatar-sm")}<strong>${ui.escape(c.fullName)}</strong></div></td>
          <td><a href="mailto:${ui.escape(c.email)}">${ui.escape(c.email)}</a></td>
          <td>${ui.formatEnum(c.fitnessGoal)}</td><td>${ui.formatEnum(c.fitnessLevel)}</td><td>${c.age ?? "-"}</td>
          <td class="nowrap">${c.heightCm ?? "-"} cm / ${c.weightKg ?? "-"} kg</td></tr>`).join("")}</tbody></table></div>`
    : ui.emptyHtml("fa-users", "No clients yet. Users become your clients when they book you or join your group sessions.");

  // The plan builder and the upload form both pick a client from this list.
  const options = list.map((c) => `<option value="${c.id}">${ui.escape(c.fullName)} (${ui.escape(c.email)})</option>`).join("");
  document.getElementById("p-client").innerHTML = `<option value="">Select a client...</option>${options}`;
  document.getElementById("r-user").innerHTML = `<option value="">All my clients</option>${options}`;
}

// ===================== availability =====================

async function loadAvailability() {
  const el = document.getElementById("availability-list");
  ui.showLoader(el);
  try {
    const slots = await api.get("/trainer/availability");
    if (!slots.length) {
      el.innerHTML = ui.emptyHtml("fa-clock", "No time slots yet. Add your first one.");
      return;
    }
    el.innerHTML = `<div class="table-wrap"><table><thead><tr><th>Day</th><th>From</th><th>To</th><th></th></tr></thead><tbody>
      ${slots.map((s) => `<tr><td><strong>${ui.formatEnum(s.dayOfWeek)}</strong></td><td>${ui.formatTime(s.startTime)}</td><td>${ui.formatTime(s.endTime)}</td>
        <td class="text-center"><button class="btn btn-danger btn-sm" data-slot="${s.id}" aria-label="Delete slot"><i class="fa-solid fa-trash"></i></button></td></tr>`).join("")}
      </tbody></table></div>`;
    el.querySelectorAll("[data-slot]").forEach((button) => button.addEventListener("click", async () => {
      if (!confirm("Delete this time slot?")) return;
      try {
        await api.del(`/trainer/availability/${button.dataset.slot}`);
        ui.toast("Slot removed", "success");
        loadAvailability();
      } catch (error) {
        ui.toastError(error);
      }
    }));
  } catch (error) {
    el.innerHTML = ui.errorHtml(error.message);
  }
}

function setupAvailabilityForm() {
  const form = document.getElementById("availability-form");
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!ui.validateForm(form)) return;
    if (form.endTime.value <= form.startTime.value) {
      ui.setFieldError(form.endTime, "End time must be after start time");
      return;
    }
    const button = form.querySelector("button[type=submit]");
    ui.setLoading(button, true, "Adding...");
    try {
      await api.post("/trainer/availability", ui.formData(form));
      ui.toast("Time slot added", "success");
      loadAvailability();
    } catch (error) {
      ui.showServerErrors(form, error);
      ui.toastError(error);
    } finally {
      ui.setLoading(button, false);
    }
  });
}

// ===================== group session =====================

function setupGroupForm() {
  const form = document.getElementById("group-form");
  // Earliest choice: one hour from now, in the local time zone format datetime-local expects.
  const soon = new Date(Date.now() + 60 * 60 * 1000);
  soon.setMinutes(soon.getMinutes() - soon.getTimezoneOffset());
  form.scheduledAt.min = soon.toISOString().slice(0, 16);

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!ui.validateForm(form)) return;
    if (new Date(form.scheduledAt.value) <= new Date()) {
      ui.setFieldError(form.scheduledAt, "Pick a time in the future");
      return;
    }
    const data = ui.formData(form);
    data.durationMinutes = Number(data.durationMinutes);
    data.maxParticipants = Number(data.maxParticipants);

    const button = form.querySelector("button[type=submit]");
    ui.setLoading(button, true, "Creating...");
    try {
      await api.post("/sessions/group", data);
      ui.toast("Group session created. Users can now join it.", "success");
      form.reset();
      loadDashboard();
    } catch (error) {
      ui.showServerErrors(form, error);
      ui.toastError(error);
    } finally {
      ui.setLoading(button, false);
    }
  });
}

// ===================== workout plans =====================

function exerciseRowHtml() {
  return `
    <div class="exercise-row">
      <div><input name="ex-name" placeholder="e.g. Squats" required maxlength="100" aria-label="Exercise name"></div>
      <div><input name="ex-sets" type="number" min="1" max="20" value="3" required aria-label="Sets"></div>
      <div><input name="ex-reps" type="number" min="1" max="200" value="12" required aria-label="Reps"></div>
      <div><input name="ex-rest" type="number" min="0" max="600" value="60" aria-label="Rest seconds"></div>
      <div><input name="ex-day" type="number" min="1" max="7" value="1" required aria-label="Day number"></div>
      <div><input name="ex-notes" maxlength="500" placeholder="Optional" aria-label="Notes"></div>
      <div><button type="button" class="btn btn-danger btn-sm remove-row" aria-label="Remove exercise"><i class="fa-solid fa-xmark"></i></button></div>
    </div>`;
}

function setupPlanForm() {
  const form = document.getElementById("plan-form");
  const rows = document.getElementById("exercise-rows");
  const addRow = () => rows.insertAdjacentHTML("beforeend", exerciseRowHtml());
  addRow();
  addRow();

  document.getElementById("add-exercise").addEventListener("click", addRow);
  rows.addEventListener("click", (event) => {
    const remove = event.target.closest(".remove-row");
    if (!remove) return;
    if (rows.children.length === 1) {
      ui.toast("A plan needs at least one exercise", "warning");
      return;
    }
    remove.closest(".exercise-row").remove();
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!ui.validateForm(form)) return;

    const exercises = [...rows.querySelectorAll(".exercise-row")].map((row) => ({
      name: row.querySelector("[name=ex-name]").value.trim(),
      sets: Number(row.querySelector("[name=ex-sets]").value),
      reps: Number(row.querySelector("[name=ex-reps]").value),
      restSeconds: Number(row.querySelector("[name=ex-rest]").value || 0),
      dayNumber: Number(row.querySelector("[name=ex-day]").value),
      notes: row.querySelector("[name=ex-notes]").value.trim() || null,
    }));
    const body = {
      userId: Number(form.userId.value),
      title: form.title.value.trim(),
      goal: form.goal.value.trim() || null,
      durationWeeks: Number(form.durationWeeks.value),
      exercises,
    };

    const button = form.querySelector("button[type=submit]");
    ui.setLoading(button, true, "Saving...");
    try {
      await api.post("/workout-plans", body);
      ui.toast("Workout plan saved and shared with your client", "success");
      form.reset();
      rows.innerHTML = "";
      addRow();
      loadPlans();
    } catch (error) {
      ui.showServerErrors(form, error);
      ui.toastError(error);
    } finally {
      ui.setLoading(button, false);
    }
  });
}

async function loadPlans() {
  const el = document.getElementById("plans-list");
  ui.showLoader(el);
  try {
    const plans = await api.get("/workout-plans/my");
    el.innerHTML = plans.length
      ? plans.map((p) => ui.workoutPlanHtml(p, { showClient: true })).join("")
      : ui.emptyHtml("fa-dumbbell", "You have not created any plans yet.");
  } catch (error) {
    el.innerHTML = ui.errorHtml(error.message);
  }
}

// ===================== resources =====================

async function loadResources() {
  const el = document.getElementById("resources-list");
  ui.showLoader(el);
  try {
    const resources = await api.get("/resources/my");
    if (!resources.length) {
      el.innerHTML = ui.emptyHtml("fa-folder-open", "No files uploaded yet.");
      return;
    }
    el.innerHTML = `<ul class="list">${resources.map((r) => `
      <li class="list-item">
        <div><div class="title">${ui.escape(r.title)} <span class="badge badge-neutral">${ui.formatEnum(r.type)}</span></div>
          <div class="meta">${ui.escape(r.originalFileName)} &middot; ${ui.formatBytes(r.sizeBytes)} &middot; ${ui.formatDate(r.uploadedAt)}
            &middot; ${r.sharedWithUserName ? "for " + ui.escape(r.sharedWithUserName) : "all clients"}</div></div>
        <div class="btn-group">
          <button class="btn btn-ghost btn-sm" data-download="${r.id}" data-name="${ui.escape(r.originalFileName)}"><i class="fa-solid fa-download"></i></button>
          <button class="btn btn-danger btn-sm" data-delete="${r.id}" aria-label="Delete file"><i class="fa-solid fa-trash"></i></button>
        </div>
      </li>`).join("")}</ul>`;

    el.querySelectorAll("[data-download]").forEach((button) => button.addEventListener("click", async () => {
      try {
        await api.download(`/resources/${button.dataset.download}/download`, button.dataset.name);
      } catch (error) {
        ui.toastError(error);
      }
    }));
    el.querySelectorAll("[data-delete]").forEach((button) => button.addEventListener("click", async () => {
      if (!confirm("Delete this file for everyone?")) return;
      try {
        await api.del(`/resources/${button.dataset.delete}`);
        ui.toast("File deleted", "success");
        loadResources();
      } catch (error) {
        ui.toastError(error);
      }
    }));
  } catch (error) {
    el.innerHTML = ui.errorHtml(error.message);
  }
}

function setupUploadForm() {
  const form = document.getElementById("upload-form");
  const MAX_BYTES = 50 * 1024 * 1024;
  const ALLOWED = ["mp4", "pdf", "docx", "jpg", "jpeg", "png"];

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!ui.validateForm(form)) return;
    const file = form.file.files[0];
    if (!file) {
      ui.setFieldError(form.file, "Choose a file");
      return;
    }
    const extension = file.name.split(".").pop().toLowerCase();
    if (!ALLOWED.includes(extension)) {
      ui.setFieldError(form.file, "Allowed: mp4, pdf, docx, jpg, png");
      return;
    }
    if (file.size > MAX_BYTES) {
      ui.setFieldError(form.file, "File is larger than 50 MB");
      return;
    }

    const formData = new FormData();
    formData.append("file", file);
    formData.append("title", form.title.value.trim());
    formData.append("type", form.type.value);
    if (form.userId.value) formData.append("userId", form.userId.value);

    const button = form.querySelector("button[type=submit]");
    ui.setLoading(button, true, "Uploading...");
    try {
      await api.upload("/resources", formData);
      ui.toast("File uploaded", "success");
      form.reset();
      loadResources();
    } catch (error) {
      ui.toastError(error);
    } finally {
      ui.setLoading(button, false);
    }
  });
}

// ===================== profile =====================

function fillProfileForm(p) {
  const form = document.getElementById("profile-form");
  form.fullName.value = p.fullName || "";
  form.phone.value = p.phone || "";
  // Add the trainer's specialization to the dropdown if it is not one of the defaults.
  if (![...form.specialization.options].some((o) => o.value === p.specialization)) {
    form.specialization.insertAdjacentHTML("beforeend", `<option value="${ui.escape(p.specialization)}">${ui.escape(p.specialization)}</option>`);
  }
  form.specialization.value = p.specialization;
  form.experienceYears.value = p.experienceYears ?? 0;
  form.pricePerSession.value = p.pricePerSession ?? "";
  form.certifications.value = p.certifications || "";
  form.profileImageUrl.value = p.profileImageUrl || "";
  form.bio.value = p.bio || "";
}

function setupProfileForm() {
  const form = document.getElementById("profile-form");
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!ui.validateForm(form)) return;
    const data = ui.formData(form);
    data.experienceYears = Number(data.experienceYears);
    data.pricePerSession = Number(data.pricePerSession);

    const button = form.querySelector("button[type=submit]");
    ui.setLoading(button, true, "Saving...");
    try {
      const updated = await api.put("/trainer/profile", data);
      // Keep the name in the navbar in sync.
      const stored = auth.getUser();
      stored.name = updated.fullName;
      localStorage.setItem(auth.USER_KEY, JSON.stringify(stored));
      auth.renderNavbar();
      ui.toast("Profile updated", "success");
      loadDashboard();
    } catch (error) {
      ui.showServerErrors(form, error);
      ui.toastError(error);
    } finally {
      ui.setLoading(button, false);
    }
  });
}
