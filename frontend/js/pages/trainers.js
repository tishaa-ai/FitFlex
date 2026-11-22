/* Trainers list: filters call GET /api/trainers?... and re-render the cards. */
document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("filter-form");
  const list = document.getElementById("trainer-list");
  const count = document.getElementById("result-count");
  let debounceTimer;

  // Pre-select a specialization from the URL, e.g. trainers.html?specialization=Yoga
  const preset = ui.queryParam("specialization");
  if (preset) form.specialization.value = preset;

  async function load() {
    ui.showLoader(list, "Finding trainers...");
    const params = api.query({
      search: form.search.value,
      specialization: form.specialization.value,
      minExperience: form.minExperience.value,
      maxPrice: form.maxPrice.value,
    });
    try {
      const trainers = await api.get("/trainers" + params);
      count.textContent = `${trainers.length} trainer${trainers.length === 1 ? "" : "s"} found`;
      list.innerHTML = trainers.length
        ? trainers.map(ui.trainerCard).join("")
        : `<div style="grid-column: 1 / -1">${ui.emptyHtml("fa-user-slash", "No trainers match these filters. Try clearing some.")}</div>`;
      ui.initReveal();
    } catch (error) {
      count.textContent = "";
      list.innerHTML = `<div style="grid-column: 1 / -1">${ui.errorHtml(error.message)}</div>`;
    }
  }

  form.addEventListener("change", load);
  // Wait until the user stops typing for 300 ms before searching.
  form.search.addEventListener("input", () => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(load, 300);
  });
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    load();
  });
  form.addEventListener("reset", () => setTimeout(load, 0)); // after the browser clears the fields

  load();
});
