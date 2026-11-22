/*
 * Shared by login.html (user portal) and trainer-login.html (trainer portal).
 * <body data-portal="user|trainer"> decides which endpoint is called.
 */
document.addEventListener("DOMContentLoaded", () => {
  if (auth.redirectIfLoggedIn()) return;

  const portal = document.body.dataset.portal;
  const endpoint = portal === "trainer" ? "/trainer/login" : "/user/login";
  const form = document.getElementById("login-form");

  // "Fill demo login" button
  document.getElementById("fill-demo").addEventListener("click", (event) => {
    form.email.value = event.currentTarget.dataset.email;
    form.password.value = event.currentTarget.dataset.password;
    ui.clearErrors(form);
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!ui.validateForm(form)) return;

    const button = form.querySelector("button[type=submit]");
    ui.setLoading(button, true, "Logging in...");
    try {
      const response = await api.post(endpoint, {
        email: form.email.value.trim(),
        password: form.password.value,
      });
      auth.saveSession(response);
      ui.toast(`Welcome back, ${response.name.split(" ")[0]}!`, "success");
      auth.redirectAfterLogin(response);
    } catch (error) {
      ui.showServerErrors(form, error);
      ui.toastError(error);
      ui.setLoading(button, false);
    }
  });
});
