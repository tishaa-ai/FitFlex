/*
 * ui.js: small reusable UI helpers shared by every page:
 * toasts, loaders, empty states, form validation, formatting and safe HTML escaping.
 * Everything is attached to one global object called `ui`.
 */
const ui = {
  // ---------- Safety ----------

  /**
   * Escapes text before putting it into innerHTML. Data from the API (names, bios ...) is
   * user-typed, so without this a name like "<img onerror=...>" would run as code (XSS).
   */
  escape(value) {
    if (value === null || value === undefined) return "";
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  },

  // ---------- Toast notifications ----------

  toast(message, type = "info", durationMs = 4000) {
    let container = document.getElementById("toast-container");
    if (!container) {
      container = document.createElement("div");
      container.id = "toast-container";
      document.body.appendChild(container);
    }
    const icons = { success: "fa-circle-check", error: "fa-circle-exclamation", warning: "fa-triangle-exclamation", info: "fa-circle-info" };
    const toast = document.createElement("div");
    toast.className = `toast ${type}`;
    toast.setAttribute("role", "status");
    toast.innerHTML = `<i class="fa-solid ${icons[type] || icons.info}"></i><div>${ui.escape(message)}</div>`;
    container.appendChild(toast);
    setTimeout(() => {
      toast.classList.add("hide");
      setTimeout(() => toast.remove(), 300);
    }, durationMs);
  },

  /** Shows an error from api.js nicely (falls back to the message text). */
  toastError(error, fallback = "Something went wrong") {
    ui.toast((error && error.message) || fallback, "error", 6000);
  },

  // ---------- Loading and empty states ----------

  loaderHtml(text = "Loading...") {
    return `<div class="loader"><div class="spinner"></div><span>${ui.escape(text)}</span></div>`;
  },

  showLoader(element, text) {
    if (element) element.innerHTML = ui.loaderHtml(text);
  },

  emptyHtml(icon, message, actionHtml = "") {
    return `<div class="empty-state"><i class="fa-solid ${icon}"></i><p>${ui.escape(message)}</p>${actionHtml}</div>`;
  },

  errorHtml(message) {
    return `<div class="alert alert-danger"><i class="fa-solid fa-circle-exclamation"></i><div>${ui.escape(message)}</div></div>`;
  },

  /** Disables a button and shows a small spinner while a request runs. */
  setLoading(button, loading, loadingText = "Please wait...") {
    if (!button) return;
    if (loading) {
      button.dataset.originalHtml = button.innerHTML;
      button.disabled = true;
      button.innerHTML = `<span class="spinner spinner-sm"></span> ${ui.escape(loadingText)}`;
    } else {
      button.disabled = false;
      if (button.dataset.originalHtml) button.innerHTML = button.dataset.originalHtml;
    }
  },

  // ---------- Form validation ----------

  /**
   * Validates a form using simple attributes on its inputs:
   *   required                    -> must not be empty
   *   type="email"                -> must look like an email
   *   data-validate="password"    -> at least 8 characters including a number
   *   data-validate="positive"    -> number greater than 0
   *   data-match="otherFieldName" -> must equal another field (confirm password)
   *   min / max                   -> numeric range
   * Shows messages under each invalid field and returns true if everything is valid.
   */
  validateForm(form) {
    ui.clearErrors(form);
    let valid = true;
    const fields = form.querySelectorAll("input, select, textarea");

    fields.forEach((field) => {
      if (field.disabled || field.type === "hidden" || field.type === "file") return;
      const value = field.value.trim();
      let message = "";

      if (field.required && !value) {
        message = "This field is required";
      } else if (value && field.type === "email" && !ui.isEmail(value)) {
        message = "Enter a valid email address";
      } else if (value && field.dataset.validate === "password" && !ui.isStrongPassword(value)) {
        message = "At least 8 characters, including a number";
      } else if (value && field.dataset.validate === "positive" && !(Number(value) > 0)) {
        message = "Must be a positive number";
      } else if (value && field.type === "number" && field.min !== "" && Number(value) < Number(field.min)) {
        message = `Must be at least ${field.min}`;
      } else if (value && field.type === "number" && field.max !== "" && Number(value) > Number(field.max)) {
        message = `Must be at most ${field.max}`;
      } else if (field.dataset.match) {
        const other = form.elements[field.dataset.match];
        if (other && other.value !== field.value) message = "Passwords do not match";
      }

      if (message) {
        valid = false;
        ui.setFieldError(field, message);
      }
    });

    if (!valid) {
      const first = form.querySelector(".invalid");
      if (first) first.focus();
    }
    return valid;
  },

  isEmail(value) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value);
  },

  isStrongPassword(value) {
    return value.length >= 8 && /\d/.test(value);
  },

  setFieldError(field, message) {
    field.classList.add("invalid");
    let error = field.parentElement.querySelector(".field-error");
    if (!error) {
      error = document.createElement("div");
      error.className = "field-error";
      field.parentElement.appendChild(error);
    }
    error.textContent = message;
  },

  clearErrors(form) {
    form.querySelectorAll(".invalid").forEach((el) => el.classList.remove("invalid"));
    form.querySelectorAll(".field-error").forEach((el) => (el.textContent = ""));
  },

  /** Puts the backend's {"errors": {"field": "message"}} next to the matching inputs. */
  showServerErrors(form, error) {
    if (!error || !error.errors) return;
    Object.entries(error.errors).forEach(([name, message]) => {
      const field = form.elements[name];
      if (field) ui.setFieldError(field, message);
    });
  },

  /** Reads all named form fields into a plain object; empty strings become null. */
  formData(form) {
    const data = {};
    new FormData(form).forEach((value, key) => {
      data[key] = typeof value === "string" && value.trim() === "" ? null : value;
    });
    return data;
  },

  // ---------- Formatting ----------

  formatDate(isoDate) {
    if (!isoDate) return "";
    const date = new Date(isoDate.length === 10 ? isoDate + "T00:00:00" : isoDate);
    return date.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
  },

  formatDateTime(isoDateTime) {
    if (!isoDateTime) return "";
    return new Date(isoDateTime).toLocaleString("en-IN", {
      weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit",
    });
  },

  formatTime(time) {
    // "07:00:00" -> "07:00"
    return time ? time.substring(0, 5) : "";
  },

  formatMoney(amount) {
    if (amount === null || amount === undefined) return "";
    return "₹" + Number(amount).toLocaleString("en-IN", { maximumFractionDigits: 0 });
  },

  formatEnum(value) {
    // "WEIGHT_LOSS" -> "Weight loss"
    if (!value) return "";
    const text = String(value).replace(/_/g, " ").toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
  },

  formatBytes(bytes) {
    if (!bytes && bytes !== 0) return "";
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
    return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  },

  initials(name) {
    if (!name) return "?";
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join("");
  },

  avatarHtml(name, imageUrl, sizeClass = "") {
    if (imageUrl) {
      return `<img class="avatar ${sizeClass}" src="${ui.escape(imageUrl)}" alt="${ui.escape(name)}">`;
    }
    return `<div class="avatar ${sizeClass}" aria-hidden="true">${ui.escape(ui.initials(name))}</div>`;
  },

  statusBadge(status) {
    const icons = { REQUESTED: "fa-hourglass-half", CONFIRMED: "fa-circle-check", COMPLETED: "fa-flag-checkered", CANCELLED: "fa-ban" };
    return `<span class="badge badge-${String(status).toLowerCase()}"><i class="fa-solid ${icons[status] || "fa-circle"}"></i>${ui.formatEnum(status)}</span>`;
  },

  ratingHtml(rating) {
    if (rating === null || rating === undefined) return `<span class="badge badge-accent">New</span>`;
    return `<span class="rating"><i class="fa-solid fa-star"></i> ${Number(rating).toFixed(1)}</span>`;
  },

  /** Card used on the home page and the trainers list. */
  trainerCard(t) {
    return `
      <article class="card card-hover trainer-card reveal">
        <div class="row">
          ${ui.avatarHtml(t.fullName, t.profileImageUrl)}
          <div>
            <h3>${ui.escape(t.fullName)} ${t.verified ? '<i class="fa-solid fa-circle-check accent" title="Verified trainer"></i>' : ""}</h3>
            <span class="badge badge-accent">${ui.escape(t.specialization)}</span>
          </div>
        </div>
        <p class="bio">${ui.escape((t.bio || "No bio yet.").slice(0, 140))}${t.bio && t.bio.length > 140 ? "..." : ""}</p>
        <div class="trainer-meta">
          <span><i class="fa-solid fa-medal"></i>${t.experienceYears ?? 0} yrs experience</span>
          <span>${ui.ratingHtml(t.rating)}</span>
        </div>
        <div class="row-between">
          <span class="price">${ui.formatMoney(t.pricePerSession)} <small>/ session</small></span>
          <a href="trainer-profile.html?id=${encodeURIComponent(t.id)}" class="btn btn-primary btn-sm">View profile</a>
        </div>
      </article>`;
  },

  /** A workout plan as a collapsible block, exercises grouped by day. */
  workoutPlanHtml(plan, { showClient = false, open = false } = {}) {
    const days = [...new Set(plan.exercises.map((e) => e.dayNumber))].sort((a, b) => a - b);
    const daysHtml = days.map((day) => `
      <div class="plan-day"><h4>Day ${day}</h4>
        <div class="table-wrap"><table>
          <thead><tr><th>Exercise</th><th>Sets x Reps</th><th>Rest</th><th>Notes</th></tr></thead>
          <tbody>${plan.exercises.filter((e) => e.dayNumber === day).map((e) => `
            <tr><td>${ui.escape(e.name)}</td><td class="nowrap">${e.sets} x ${e.reps}</td>
              <td>${e.restSeconds ? e.restSeconds + "s" : "-"}</td><td class="muted">${ui.escape(e.notes || "")}</td></tr>`).join("")}
          </tbody></table></div></div>`).join("");
    const who = showClient ? `for ${ui.escape(plan.userName)}` : `by ${ui.escape(plan.trainerName)}`;
    return `
      <details class="plan" ${open ? "open" : ""}>
        <summary>${ui.escape(plan.title)} <span class="muted small">&middot; ${who} &middot; ${plan.durationWeeks} weeks</span></summary>
        ${plan.goal ? `<p class="muted small mt-1 mb-0">Goal: ${ui.escape(plan.goal)}</p>` : ""}
        ${daysHtml}
      </details>`;
  },

  /** "Mon, 24 Nov, 08:00 · 60 min" */
  sessionWhen(s) {
    return `<i class="fa-regular fa-calendar"></i> ${ui.formatDateTime(s.scheduledAt)} &middot; ${s.durationMinutes} min`;
  },

  /** Today's date as YYYY-MM-DD in local time (for date inputs). */
  todayIso() {
    const d = new Date();
    d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
    return d.toISOString().substring(0, 10);
  },

  // ---------- Page behaviour ----------

  /** Tabs: buttons with data-tab="panelId" switch the matching .tab-panel. */
  initTabs(container, onChange) {
    const tabs = container.querySelectorAll(".tab");
    tabs.forEach((tab) => {
      tab.addEventListener("click", () => {
        tabs.forEach((t) => t.classList.toggle("active", t === tab));
        container.querySelectorAll(".tab-panel").forEach((panel) => {
          panel.classList.toggle("active", panel.id === tab.dataset.tab);
        });
        if (onChange) onChange(tab.dataset.tab);
      });
    });
  },

  /** Fade-in-on-scroll: adds .visible to every .reveal element when it enters the viewport. */
  initReveal() {
    const elements = document.querySelectorAll(".reveal:not(.visible)");
    if (!("IntersectionObserver" in window)) {
      elements.forEach((el) => el.classList.add("visible"));
      return;
    }
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("visible");
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12 });
    elements.forEach((el) => observer.observe(el));
  },

  /** Reads ?name=value from the current URL. */
  queryParam(name) {
    return new URLSearchParams(window.location.search).get(name);
  },
};
