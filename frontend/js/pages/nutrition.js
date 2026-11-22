/*
 * Nutrition page (USER): generate a diet plan, show BMR/TDEE/target, meals grouped by type,
 * daily totals with a macro doughnut chart, food search and plan history.
 */
let macroChart = null;
let planHistory = [];

document.addEventListener("DOMContentLoaded", () => {
  const user = auth.requireRole("USER");
  if (!user) return;

  setupGenerateForm();
  setupFoodSearch();
  setupProfileToggle();
  loadHistory(true);
});

// ===================== generate =====================

function setupGenerateForm() {
  const form = document.getElementById("diet-form");
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!ui.validateForm(form)) return;

    const body = {
      goal: form.goal.value || null,
      dietType: form.dietType.value,
      mealsPerDay: Number(form.mealsPerDay.value),
    };
    const button = form.querySelector("button[type=submit]");
    ui.setLoading(button, true, "Calculating...");
    ui.showLoader(document.getElementById("plan-result"), "Building your plan...");
    try {
      const plan = await api.post("/diet/generate", body);
      renderPlan(plan);
      ui.toast("Your diet plan is ready", "success");
      loadHistory(false);
    } catch (error) {
      document.getElementById("plan-result").innerHTML = ui.errorHtml(error.message) +
        (error.status === 400 && /profile/i.test(error.message) ? '<p class="muted">Update your profile details and try again.</p>' : "");
      ui.toastError(error);
    } finally {
      ui.setLoading(button, false);
    }
  });
}

// ===================== show a plan =====================

const MEAL_ICONS = { BREAKFAST: "fa-mug-hot", LUNCH: "fa-bowl-rice", SNACK: "fa-apple-whole", DINNER: "fa-utensils" };

function renderPlan(plan) {
  const t = plan.totals;
  const groupsHtml = plan.mealGroups.map((group) => `
    <h3 class="meal-type-title"><i class="fa-solid ${MEAL_ICONS[group.mealType] || "fa-utensils"}"></i> ${ui.formatEnum(group.mealType)}</h3>
    <div class="stack">${group.meals.map(mealCard).join("")}</div>`).join("");

  document.getElementById("plan-result").innerHTML = `
    <div class="grid grid-3 mb-2">
      ${numberCard("fa-bed", Math.round(plan.bmr), "BMR (kcal at rest)")}
      ${numberCard("fa-person-walking", Math.round(plan.tdee), "TDEE (kcal per day)")}
      ${numberCard("fa-bullseye", plan.targetCalories, "Target kcal for " + ui.formatEnum(plan.goal).toLowerCase())}
    </div>
    <div class="alert ${plan.source === "SPOONACULAR" ? "alert-success" : "alert-info"}">
      <i class="fa-solid ${plan.source === "SPOONACULAR" ? "fa-plug-circle-check" : "fa-database"}"></i>
      <div>${ui.escape(plan.note)} <span class="muted small">(${ui.escape(plan.dietType)}, ${plan.mealsPerDay} meals, ${ui.formatDateTime(plan.createdAt)})</span></div>
    </div>
    <div class="card">
      ${groupsHtml}
    </div>
    <div class="card mt-3">
      <div class="card-header"><h3>Daily totals</h3><span class="muted small">Target ${plan.targetCalories} kcal</span></div>
      <div class="grid grid-2">
        <div class="stack">
          <div class="row-between"><span>Calories</span><strong class="accent">${t.calories} kcal</strong></div>
          <div class="row-between"><span>Protein</span><strong>${t.protein} g</strong></div>
          <div class="row-between"><span>Carbohydrates</span><strong>${t.carbs} g</strong></div>
          <div class="row-between"><span>Fat</span><strong>${t.fat} g</strong></div>
          <p class="muted small mb-0">Protein and carbs have 4 kcal per gram, fat has 9 kcal per gram.</p>
        </div>
        <div class="chart-box-sm"><canvas id="macro-chart" aria-label="Macronutrient split chart"></canvas></div>
      </div>
    </div>`;

  drawMacroChart(t);
}

function numberCard(icon, value, label) {
  return `<div class="card stat-card"><div class="card-icon"><i class="fa-solid ${icon}"></i></div>
    <div><div class="stat-value">${value}</div><div class="stat-label">${ui.escape(label)}</div></div></div>`;
}

function mealCard(m) {
  const image = m.imageUrl ? `<img src="${ui.escape(m.imageUrl)}" alt="" loading="lazy">` : "";
  const name = m.sourceUrl
    ? `<a href="${ui.escape(m.sourceUrl)}" target="_blank" rel="noopener">${ui.escape(m.name)} <i class="fa-solid fa-arrow-up-right-from-square small"></i></a>`
    : ui.escape(m.name);
  return `
    <div class="meal-card">
      ${image}
      <div>
        <h4>${name}</h4>
        <div class="muted small">${ui.escape(m.servingSize)}${m.readyInMinutes ? ` &middot; ${m.readyInMinutes} min` : ""} &middot; ${ui.escape(m.origin)}</div>
        <div class="macro-row">
          <span class="macro kcal">${m.calories} kcal</span>
          <span class="macro">Protein ${m.protein} g</span>
          <span class="macro">Carbs ${m.carbs} g</span>
          <span class="macro">Fat ${m.fat} g</span>
        </div>
      </div>
    </div>`;
}

/** Doughnut of where the calories come from (grams x kcal-per-gram). */
function drawMacroChart(t) {
  if (typeof Chart === "undefined") return; // CDN blocked: the numbers are still shown
  if (macroChart) macroChart.destroy();
  macroChart = new Chart(document.getElementById("macro-chart"), {
    type: "doughnut",
    data: {
      labels: ["Protein", "Carbs", "Fat"],
      datasets: [{
        data: [Math.round(t.protein * 4), Math.round(t.carbs * 4), Math.round(t.fat * 9)],
        backgroundColor: ["#b5f23d", "#4fb3ff", "#ffb547"],
        borderColor: "#1f2328",
        borderWidth: 3,
      }],
    },
    options: {
      maintainAspectRatio: false,
      cutout: "62%",
      plugins: {
        legend: { position: "bottom", labels: { color: "#e9ebee" } },
        tooltip: { callbacks: { label: (c) => ` ${c.label}: ${c.parsed} kcal` } },
      },
    },
  });
}

// ===================== history =====================

async function loadHistory(showLatest) {
  const el = document.getElementById("history");
  ui.showLoader(el);
  try {
    planHistory = await api.get("/diet/my");
    if (!planHistory.length) {
      el.innerHTML = ui.emptyHtml("fa-clock-rotate-left", "No plans yet.");
      return;
    }
    el.innerHTML = `<ul class="list">${planHistory.slice(0, 10).map((p, i) => `
      <li class="list-item">
        <div><div class="title">${p.targetCalories} kcal &middot; ${ui.escape(p.dietType)}</div>
          <div class="meta">${ui.formatEnum(p.goal)} &middot; ${p.mealsPerDay} meals &middot; ${ui.formatDate(p.createdAt)}</div></div>
        <button class="btn btn-ghost btn-sm" data-index="${i}">View</button>
      </li>`).join("")}</ul>`;
    el.querySelectorAll("[data-index]").forEach((button) => button.addEventListener("click", () => {
      renderPlan(planHistory[Number(button.dataset.index)]);
      window.scrollTo({ top: 0, behavior: "smooth" });
    }));
    if (showLatest) renderPlan(planHistory[0]); // open the page with the most recent plan
  } catch (error) {
    el.innerHTML = ui.errorHtml(error.message);
  }
}

// ===================== food search =====================

function setupFoodSearch() {
  const form = document.getElementById("food-form");
  const results = document.getElementById("food-results");
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const query = form.query.value.trim();
    if (query.length < 2) {
      ui.setFieldError(form.query, "Type at least 2 characters");
      return;
    }
    ui.clearErrors(form);
    ui.showLoader(results, "Searching...");
    try {
      const data = await api.get("/diet/search-food" + api.query({ query }));
      const note = data.message ? `<p class="form-hint">${ui.escape(data.message)}</p>` : "";
      results.innerHTML = note + (data.items.length
        ? `<ul class="list">${data.items.map((f) => `
            <li class="list-item"><div><div class="title">${ui.escape(f.name)}</div>
              <div class="meta">${ui.escape(f.servingSize)} &middot; P ${f.protein} g &middot; C ${f.carbs} g &middot; F ${f.fat} g</div></div>
              <span class="macro kcal">${f.calories} kcal</span></li>`).join("")}</ul>`
        : ui.emptyHtml("fa-magnifying-glass", `No foods found for "${query}".`));
    } catch (error) {
      results.innerHTML = ui.errorHtml(error.message);
    }
  });
}

// ===================== profile details used for the maths =====================

function setupProfileToggle() {
  const link = document.getElementById("show-profile");
  const box = document.getElementById("profile-box");
  link.addEventListener("click", async (event) => {
    event.preventDefault();
    if (!box.classList.contains("hidden")) {
      box.classList.add("hidden");
      return;
    }
    box.classList.remove("hidden");
    ui.showLoader(box);
    try {
      const p = await api.get("/user/profile");
      box.innerHTML = `<ul class="list small">
        <li class="list-item"><span>Age</span><strong>${p.age ?? "-"}</strong></li>
        <li class="list-item"><span>Gender</span><strong>${ui.formatEnum(p.gender) || "-"}</strong></li>
        <li class="list-item"><span>Height</span><strong>${p.heightCm ?? "-"} cm</strong></li>
        <li class="list-item"><span>Weight</span><strong>${p.weightKg ?? "-"} kg</strong></li>
        <li class="list-item"><span>Activity</span><strong>${ui.formatEnum(p.activityLevel) || "-"}</strong></li>
        <li class="list-item"><span>Goal</span><strong>${ui.formatEnum(p.fitnessGoal) || "-"}</strong></li></ul>
        <p class="form-hint">Logging today's weight on the Progress page updates your weight here.</p>`;
    } catch (error) {
      box.innerHTML = ui.errorHtml(error.message);
    }
  });
}
