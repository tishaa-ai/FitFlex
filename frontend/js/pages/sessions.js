/*
 * My Sessions page (both roles).
 * Upcoming / Past tabs come from GET /api/sessions/my?filter=..., and the actions depend on role:
 *   USER:    Join (when CONFIRMED and a link exists), Cancel, plus the Group classes tab.
 *   TRAINER: Confirm / Decline requests, Cancel, Mark completed (after the start time), Start video.
 */
let currentUser;

document.addEventListener("DOMContentLoaded", () => {
  currentUser = auth.requireRole();
  if (!currentUser) return;

  if (currentUser.role === "TRAINER") {
    document.getElementById("sessions-sub").textContent = "Confirm requests, start video calls and mark sessions completed.";
    document.getElementById("book-btn").href = "trainer-dashboard.html";
    document.getElementById("book-btn").innerHTML = '<i class="fa-solid fa-people-group"></i> Create group session';
    document.getElementById("group-tab-btn").classList.add("hidden");
  }

  ui.initTabs(document.getElementById("session-tabs"));
  loadAll();
});

function loadAll() {
  loadList("upcoming", "upcoming-list");
  loadList("past", "past-list");
  if (currentUser.role === "USER") loadGroupSessions();
}

async function loadList(filter, elementId) {
  const el = document.getElementById(elementId);
  ui.showLoader(el);
  try {
    const sessions = await api.get("/sessions/my?filter=" + filter);
    el.innerHTML = sessions.length
      ? sessions.map(sessionCard).join("")
      : `<div style="grid-column: 1 / -1">${ui.emptyHtml("fa-calendar-xmark",
          filter === "upcoming" ? "No upcoming sessions." : "No past sessions yet.",
          filter === "upcoming" && currentUser.role === "USER" ? '<a href="trainers.html" class="btn btn-primary btn-sm">Book a trainer</a>' : "")}</div>`;
    bindActions(el);
  } catch (error) {
    el.innerHTML = `<div style="grid-column: 1 / -1">${ui.errorHtml(error.message)}</div>`;
  }
}

function sessionCard(s) {
  const isTrainer = currentUser.role === "TRAINER";
  const started = new Date(s.scheduledAt) <= new Date();
  const active = s.status === "REQUESTED" || s.status === "CONFIRMED";
  const actions = [];

  if (s.meetingLink && s.status === "CONFIRMED") {
    actions.push(`<a href="${ui.escape(s.meetingLink)}" target="_blank" rel="noopener" class="btn btn-success btn-sm">
      <i class="fa-solid fa-video"></i> ${isTrainer ? "Start session" : "Join Session"}</a>`);
  }
  if (isTrainer) {
    if (s.status === "REQUESTED") actions.push(actionButton(s.id, "CONFIRMED", "Confirm", "btn-primary", "fa-check"));
    if (s.status === "CONFIRMED" && started) actions.push(actionButton(s.id, "COMPLETED", "Mark completed", "btn-ghost", "fa-flag-checkered"));
    if (active) actions.push(actionButton(s.id, "CANCELLED", s.status === "REQUESTED" ? "Decline" : "Cancel", "btn-danger", "fa-xmark"));
  } else if (active) {
    actions.push(actionButton(s.id, "CANCELLED", s.sessionType === "GROUP" ? "Leave class" : "Cancel", "btn-danger", "fa-xmark"));
  }

  const other = isTrainer
    ? (s.sessionType === "GROUP" ? `${s.participantCount}/${s.maxParticipants} participants` : `with ${ui.escape(s.userName)}`)
    : `with ${ui.escape(s.trainerName)} &middot; ${ui.escape(s.trainerSpecialization)}`;

  return `
    <article class="card session-card">
      <div class="row-between">${ui.statusBadge(s.status)}
        <span class="badge badge-neutral">${s.sessionType === "GROUP" ? '<i class="fa-solid fa-people-group"></i> Group' : '<i class="fa-solid fa-user"></i> One-on-one'}</span></div>
      <h3 class="mb-0">${ui.escape(s.title)}</h3>
      <div class="when">${ui.sessionWhen(s)}</div>
      <div class="when"><i class="fa-solid fa-user-group"></i> ${other}</div>
      ${s.description ? `<p class="muted small mb-0">${ui.escape(s.description)}</p>` : ""}
      ${s.status === "REQUESTED" && !isTrainer ? '<p class="muted small mb-0"><i class="fa-solid fa-hourglass-half"></i> Waiting for the trainer to confirm. The video link appears after confirmation.</p>' : ""}
      ${actions.length ? `<div class="btn-group mt-1">${actions.join("")}</div>` : ""}
    </article>`;
}

function actionButton(id, status, label, style, icon) {
  return `<button class="btn ${style} btn-sm" data-id="${id}" data-status="${status}"><i class="fa-solid ${icon}"></i> ${label}</button>`;
}

function bindActions(container) {
  container.querySelectorAll("[data-status]").forEach((button) => {
    button.addEventListener("click", async () => {
      const status = button.dataset.status;
      if (status === "CANCELLED" && !confirm("Are you sure?")) return;
      ui.setLoading(button, true, "...");
      try {
        await api.put(`/sessions/${button.dataset.id}/status`, { status });
        const messages = { CONFIRMED: "Session confirmed. Jitsi link created.", COMPLETED: "Marked as completed.", CANCELLED: "Done." };
        ui.toast(messages[status], "success");
        loadAll();
      } catch (error) {
        ui.toastError(error);
        ui.setLoading(button, false);
      }
    });
  });
}

async function loadGroupSessions() {
  const el = document.getElementById("group-list");
  ui.showLoader(el);
  try {
    const sessions = await api.get("/sessions/group");
    if (!sessions.length) {
      el.innerHTML = `<div style="grid-column: 1 / -1">${ui.emptyHtml("fa-people-group", "No group classes scheduled right now.")}</div>`;
      return;
    }
    el.innerHTML = sessions.map((s) => {
      const full = s.maxParticipants && s.participantCount >= s.maxParticipants;
      const action = s.joined
        ? `<span class="badge badge-confirmed"><i class="fa-solid fa-check"></i> Joined</span>
           ${s.meetingLink ? `<a href="${ui.escape(s.meetingLink)}" target="_blank" rel="noopener" class="btn btn-success btn-sm"><i class="fa-solid fa-video"></i> Join Session</a>` : ""}`
        : full
          ? '<span class="badge badge-cancelled">Full</span>'
          : `<button class="btn btn-primary btn-sm" data-join="${s.id}"><i class="fa-solid fa-user-plus"></i> Join class</button>`;
      return `
        <article class="card session-card">
          <div class="row-between"><span class="badge badge-accent">${ui.escape(s.trainerSpecialization)}</span>
            <span class="muted small"><i class="fa-solid fa-users"></i> ${s.participantCount}/${s.maxParticipants}</span></div>
          <h3 class="mb-0">${ui.escape(s.title)}</h3>
          <div class="when">${ui.sessionWhen(s)}</div>
          <div class="when"><i class="fa-solid fa-user-tie"></i> ${ui.escape(s.trainerName)}</div>
          ${s.description ? `<p class="muted small mb-0">${ui.escape(s.description)}</p>` : ""}
          <div class="btn-group mt-1">${action}</div>
        </article>`;
    }).join("");

    el.querySelectorAll("[data-join]").forEach((button) => button.addEventListener("click", async () => {
      ui.setLoading(button, true, "Joining...");
      try {
        await api.post(`/sessions/${button.dataset.join}/join`);
        ui.toast("You joined the class! The video link is now available.", "success");
        loadAll();
      } catch (error) {
        ui.toastError(error);
        ui.setLoading(button, false);
      }
    }));
  } catch (error) {
    el.innerHTML = `<div style="grid-column: 1 / -1">${ui.errorHtml(error.message)}</div>`;
  }
}
