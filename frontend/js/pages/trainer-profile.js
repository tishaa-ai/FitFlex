/*
 * Trainer profile (trainer-profile.html?id=1): details, weekly availability and the booking form.
 * Booking is only possible for logged-in USER accounts.
 */
const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

document.addEventListener("DOMContentLoaded", async () => {
  const container = document.getElementById("profile");
  const id = ui.queryParam("id");
  if (!id) {
    container.innerHTML = ui.emptyHtml("fa-user-slash", "No trainer selected.", '<a class="btn btn-primary" href="trainers.html">Browse trainers</a>');
    return;
  }

  ui.showLoader(container, "Loading profile...");
  let trainer;
  try {
    trainer = await api.get("/trainers/" + encodeURIComponent(id));
  } catch (error) {
    container.innerHTML = ui.errorHtml(error.status === 404 ? "This trainer does not exist." : error.message);
    return;
  }

  document.title = `${trainer.fullName} | FitFlex`;
  container.innerHTML = renderProfile(trainer);
  setupBooking(trainer);
});

function renderProfile(t) {
  const slotsByDay = DAYS.map((day) => ({ day, slots: t.availability.filter((s) => s.dayOfWeek === day) }));
  const availabilityRows = slotsByDay.map(({ day, slots }) => `
    <tr><td><strong>${ui.formatEnum(day)}</strong></td>
      <td>${slots.length ? slots.map((s) => `<span class="chip">${ui.formatTime(s.startTime)} - ${ui.formatTime(s.endTime)}</span>`).join("") : '<span class="muted">Not available</span>'}</td></tr>`).join("");

  const certifications = (t.certifications || "").split(",").map((c) => c.trim()).filter(Boolean);

  return `
    <div class="layout-sidebar">
      <div class="stack">
        <div class="card">
          <div class="row" style="gap: 20px;">
            ${ui.avatarHtml(t.fullName, t.profileImageUrl, "avatar-lg")}
            <div>
              <h1 style="font-size: 1.9rem; margin-bottom: 6px;">${ui.escape(t.fullName)}
                ${t.verified ? '<i class="fa-solid fa-circle-check accent" title="Verified trainer"></i>' : ""}</h1>
              <div class="row">
                <span class="badge badge-accent">${ui.escape(t.specialization)}</span>
                ${ui.ratingHtml(t.rating)}
                <span class="muted"><i class="fa-solid fa-medal accent"></i> ${t.experienceYears ?? 0} years experience</span>
              </div>
            </div>
          </div>
          <h3 class="mt-3">About</h3>
          <p class="muted">${ui.escape(t.bio || "This trainer has not written a bio yet.")}</p>
          ${certifications.length ? `<h3>Certifications</h3><div>${certifications.map((c) => `<span class="chip"><i class="fa-solid fa-certificate accent"></i> ${ui.escape(c)}</span>`).join("")}</div>` : ""}
        </div>
        <div class="card">
          <div class="card-header"><h2>Weekly availability</h2></div>
          <div class="table-wrap"><table><tbody>${availabilityRows}</tbody></table></div>
        </div>
      </div>
      <aside class="card" id="booking-card">
        <div class="row-between"><h3 class="mb-0">Book a session</h3><span class="price">${ui.formatMoney(t.pricePerSession)} <small>/ session</small></span></div>
        <div id="booking-body" class="mt-2"></div>
      </aside>
    </div>`;
}

function setupBooking(trainer) {
  const body = document.getElementById("booking-body");
  const user = auth.getUser();

  if (!user) {
    body.innerHTML = `<p class="muted">Log in as a member to book a one-on-one session with ${ui.escape(trainer.fullName.split(" ")[0])}.</p>
      <a class="btn btn-primary btn-block" href="login.html?redirect=${encodeURIComponent("trainer-profile.html?id=" + trainer.id)}">Log in to book</a>
      <p class="form-footer">No account? <a href="register.html">Sign up free</a></p>`;
    return;
  }
  if (user.role !== "USER") {
    body.innerHTML = `<div class="alert alert-info"><i class="fa-solid fa-circle-info"></i><div>Trainer accounts cannot book sessions. Log in with a member account to book.</div></div>`;
    return;
  }
  if (trainer.availability.length === 0) {
    body.innerHTML = ui.emptyHtml("fa-calendar-xmark", "This trainer has not added any available time slots yet.");
    return;
  }

  body.innerHTML = `
    <form id="booking-form" novalidate>
      <div class="form-group"><label for="b-date">Date <span class="req">*</span></label><input type="date" id="b-date" name="date" required min="${ui.todayIso()}"></div>
      <div id="day-slots" class="form-hint mb-2">Pick a date to see the trainer's hours for that day.</div>
      <div class="form-row">
        <div class="form-group"><label for="b-time">Start time <span class="req">*</span></label><input type="time" id="b-time" name="time" required step="900"></div>
        <div class="form-group"><label for="b-duration">Duration</label>
          <select id="b-duration" name="durationMinutes"><option value="30">30 min</option><option value="45">45 min</option><option value="60" selected>60 min</option><option value="90">90 min</option></select></div>
      </div>
      <div class="form-group"><label for="b-title">Focus (optional)</label><input id="b-title" name="title" maxlength="150" placeholder="e.g. Squat technique"></div>
      <div class="form-group"><label for="b-notes">Notes for the trainer</label><textarea id="b-notes" name="description" maxlength="1000" placeholder="Injuries, goals, equipment you have..."></textarea></div>
      <button type="submit" class="btn btn-primary btn-block"><i class="fa-solid fa-calendar-check"></i> Request booking</button>
      <p class="form-hint mt-1">The trainer confirms your request, then a video link appears in My Sessions.</p>
    </form>`;

  const form = document.getElementById("booking-form");
  const daySlots = document.getElementById("day-slots");

  // When a date is picked, show that weekday's slots and pre-fill the first start time.
  form.date.addEventListener("change", () => {
    if (!form.date.value) return;
    const weekday = DAYS[(new Date(form.date.value + "T00:00:00").getDay() + 6) % 7]; // JS: Sunday = 0
    const slots = trainer.availability.filter((s) => s.dayOfWeek === weekday);
    if (slots.length === 0) {
      daySlots.innerHTML = `<span style="color: var(--danger)"><i class="fa-solid fa-circle-xmark"></i> Not available on ${ui.formatEnum(weekday)}s. Please pick another day.</span>`;
    } else {
      daySlots.innerHTML = `<i class="fa-solid fa-clock accent"></i> ${ui.formatEnum(weekday)}: ` +
        slots.map((s) => `<button type="button" class="chip slot-chip" data-time="${ui.formatTime(s.startTime)}">${ui.formatTime(s.startTime)} - ${ui.formatTime(s.endTime)}</button>`).join("");
      if (!form.time.value) form.time.value = ui.formatTime(slots[0].startTime);
      daySlots.querySelectorAll(".slot-chip").forEach((chip) => chip.addEventListener("click", () => (form.time.value = chip.dataset.time)));
    }
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!ui.validateForm(form)) return;

    const scheduledAt = `${form.date.value}T${form.time.value}`;
    if (new Date(scheduledAt) <= new Date()) {
      ui.setFieldError(form.time, "Pick a time in the future");
      return;
    }

    const button = form.querySelector("button[type=submit]");
    ui.setLoading(button, true, "Booking...");
    try {
      await api.post("/sessions/book", {
        trainerId: trainer.id,
        scheduledAt,
        durationMinutes: Number(form.durationMinutes.value),
        title: form.title.value.trim() || null,
        description: form.description.value.trim() || null,
      });
      body.innerHTML = `<div class="alert alert-success"><i class="fa-solid fa-circle-check"></i><div><strong>Request sent!</strong><br>
        ${ui.escape(trainer.fullName.split(" ")[0])} will confirm it soon. You can follow it in My Sessions.</div></div>
        <a href="sessions.html" class="btn btn-primary btn-block">Go to My Sessions</a>`;
      ui.toast("Booking requested", "success");
    } catch (error) {
      ui.showServerErrors(form, error);
      ui.toastError(error);
      ui.setLoading(button, false);
    }
  });
}
