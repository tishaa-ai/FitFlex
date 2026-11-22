/* Contact page: validates the form and posts it to the public /api/contact endpoint. */
document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("contact-form");

  // Logged-in visitors get their name and email filled in.
  const user = auth.getUser();
  if (user) {
    form.name.value = user.name || "";
    form.email.value = user.email || "";
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!ui.validateForm(form)) return;
    if (form.message.value.trim().length < 10) {
      ui.setFieldError(form.message, "Please write at least 10 characters");
      return;
    }

    const button = form.querySelector("button[type=submit]");
    ui.setLoading(button, true, "Sending...");
    try {
      const response = await api.post("/contact", ui.formData(form));
      ui.toast(response.message, "success", 6000);
      form.reset();
      if (user) {
        form.name.value = user.name || "";
        form.email.value = user.email || "";
      }
    } catch (error) {
      ui.showServerErrors(form, error);
      ui.toastError(error);
    } finally {
      ui.setLoading(button, false);
    }
  });
});
