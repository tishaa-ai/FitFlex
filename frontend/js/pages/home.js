/* Home page: loads the three best-rated trainers into the "Featured trainers" section. */
document.addEventListener("DOMContentLoaded", async () => {
  const container = document.getElementById("featured-trainers");
  ui.showLoader(container, "Loading trainers...");
  try {
    const trainers = await api.get("/trainers"); // already sorted by rating, best first
    if (trainers.length === 0) {
      container.innerHTML = ui.emptyHtml("fa-user-slash", "No trainers yet. Be the first to join!");
      return;
    }
    container.innerHTML = trainers.slice(0, 3).map(ui.trainerCard).join("");
    ui.initReveal();
  } catch (error) {
    container.innerHTML = ui.errorHtml(error.message);
  }
});
