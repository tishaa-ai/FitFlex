/*
 * Shared by register.html (users) and trainer-register.html (trainers).
 * Collects the form, converts number fields, and calls the matching register endpoint.
 */
document.addEventListener("DOMContentLoaded", () => {
  if (auth.redirectIfLoggedIn()) return;

  const isTrainer = document.body.dataset.portal === "trainer";
  const endpoint = isTrainer ? "/trainer/add" : "/user/register";
  const numberFields = isTrainer ? ["experienceYears", "pricePerSession"] : ["age", "heightCm", "weightKg"];
  const form = document.getElementById("register-form");

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!ui.validateForm(form)) return;

    const data = ui.formData(form);
    delete data.confirmPassword; // only used for the check in the browser
    data.email = data.email.trim().toLowerCase();
    numberFields.forEach((name) => {
      if (data[name] !== null) data[name] = Number(data[name]);
    });

    const button = form.querySelector("button[type=submit]");
    ui.setLoading(button, true, "Creating account...");
    try {
      const response = await api.post(endpoint, data);
      auth.saveSession(response);
      ui.toast("Account created. Welcome to FitFlex!", "success");
      window.location.href = auth.dashboardUrl(response);
    } catch (error) {
      ui.showServerErrors(form, error);
      ui.toastError(error);
      ui.setLoading(button, false);
    }
  });
});
