/*
 * auth.js: login state, route guards and the shared navbar/footer.
 *
 * After login the backend returns {token, role, name, id, trainerId, email}. We keep the token
 * and a small "user" object in localStorage, so the user stays logged in across pages and
 * browser restarts until the token expires (24 h by default).
 */
const auth = {
  TOKEN_KEY: "fitflex_token",
  USER_KEY: "fitflex_user",

  saveSession(authResponse) {
    localStorage.setItem(auth.TOKEN_KEY, authResponse.token);
    localStorage.setItem(auth.USER_KEY, JSON.stringify({
      id: authResponse.id,
      trainerId: authResponse.trainerId,
      name: authResponse.name,
      email: authResponse.email,
      role: authResponse.role,
    }));
  },

  getToken() {
    return localStorage.getItem(auth.TOKEN_KEY);
  },

  /** The logged-in user ({id, name, role ...}) or null. Also drops expired tokens. */
  getUser() {
    const token = auth.getToken();
    const userJson = localStorage.getItem(auth.USER_KEY);
    if (!token || !userJson) return null;
    if (auth.isTokenExpired(token)) {
      auth.clearSession();
      return null;
    }
    try {
      return JSON.parse(userJson);
    } catch (e) {
      auth.clearSession();
      return null;
    }
  },

  /**
   * A JWT is "header.payload.signature"; the payload is Base64URL-encoded JSON containing
   * "exp" (expiry, in seconds). Reading it here only avoids pointless requests: the real
   * check always happens on the server, which verifies the signature.
   */
  isTokenExpired(token) {
    try {
      const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
      return payload.exp * 1000 < Date.now();
    } catch (e) {
      return true;
    }
  },

  isLoggedIn() {
    return auth.getUser() !== null;
  },

  clearSession() {
    localStorage.removeItem(auth.TOKEN_KEY);
    localStorage.removeItem(auth.USER_KEY);
  },

  logout() {
    const wasTrainer = (auth.getUser() || {}).role === "TRAINER";
    auth.clearSession();
    window.location.href = wasTrainer ? "trainer-login.html" : "index.html";
  },

  /** Called by api.js when the server answers 401 to a request that had a token. */
  handleExpiredSession() {
    const wasTrainer = (auth.getUser() || {}).role === "TRAINER";
    auth.clearSession();
    sessionStorage.setItem("fitflex_flash", "Your session has expired. Please log in again.");
    window.location.href = (wasTrainer ? "trainer-login.html" : "login.html") + "?redirect=" + encodeURIComponent(auth.currentPage());
  },

  currentPage() {
    const page = window.location.pathname.split("/").pop() || "index.html";
    return page + window.location.search;
  },

  /**
   * Route guard. Call at the top of a page script:
   *   const user = auth.requireRole("USER");      // only normal users
   *   const user = auth.requireRole("TRAINER");   // only trainers
   *   const user = auth.requireRole();            // any logged-in account
   * Redirects and returns null when the visitor may not see the page.
   */
  requireRole(role) {
    const user = auth.getUser();
    if (!user) {
      sessionStorage.setItem("fitflex_flash", "Please log in to view that page.");
      const loginPage = role === "TRAINER" ? "trainer-login.html" : "login.html";
      window.location.replace(loginPage + "?redirect=" + encodeURIComponent(auth.currentPage()));
      return null;
    }
    if (role && user.role !== role) {
      sessionStorage.setItem("fitflex_flash", "That page is only for " + (role === "TRAINER" ? "trainers" : "users") + ".");
      window.location.replace(auth.dashboardUrl(user));
      return null;
    }
    return user;
  },

  /** Login/register pages: an already logged-in visitor goes straight to their dashboard. */
  redirectIfLoggedIn() {
    const user = auth.getUser();
    if (user) {
      window.location.replace(auth.dashboardUrl(user));
      return true;
    }
    return false;
  },

  dashboardUrl(user) {
    return user && user.role === "TRAINER" ? "trainer-dashboard.html" : "user-dashboard.html";
  },

  /** After login: go back to the page that sent us to login (only same-site pages), else the dashboard. */
  redirectAfterLogin(user) {
    const target = ui.queryParam("redirect");
    const safe = target && /^[a-z-]+\.html(\?.*)?$/i.test(target) && !/login|register/.test(target);
    window.location.href = safe ? target : auth.dashboardUrl(user);
  },

  // ---------- Navbar and footer (rendered into <header id="navbar"> and <footer id="footer">) ----------

  renderNavbar() {
    const header = document.getElementById("navbar");
    if (!header) return;
    const user = auth.getUser();
    const page = (window.location.pathname.split("/").pop() || "index.html").toLowerCase();

    let links;
    if (!user) {
      links = [["index.html", "Home"], ["trainers.html", "Trainers"], ["gyms.html", "Gyms"],
        ["pose.html", "Pose Detection"], ["about.html", "About"], ["contact.html", "Contact"]];
    } else if (user.role === "TRAINER") {
      links = [["trainer-dashboard.html", "Dashboard"], ["sessions.html", "Sessions"],
        ["trainers.html", "Trainers"], ["gyms.html", "Gyms"], ["pose.html", "Pose Detection"]];
    } else {
      links = [["user-dashboard.html", "Dashboard"], ["trainers.html", "Trainers"], ["sessions.html", "Sessions"],
        ["nutrition.html", "Nutrition"], ["progress.html", "Progress"], ["gyms.html", "Gyms"], ["pose.html", "Pose"]];
    }

    const linkHtml = links.map(([href, label]) =>
      `<li><a href="${href}" class="${page === href ? "active" : ""}">${label}</a></li>`).join("");

    const actions = user
      ? `<span class="nav-user"><i class="fa-solid fa-circle-user"></i> ${ui.escape(user.name.split(" ")[0])}
           ${user.role === "TRAINER" ? '<span class="badge badge-accent">Trainer</span>' : ""}</span>
         <button class="btn btn-outline btn-sm" id="logout-btn"><i class="fa-solid fa-right-from-bracket"></i> Logout</button>`
      : `<a href="login.html" class="btn btn-ghost btn-sm">Login</a>
         <a href="register.html" class="btn btn-primary btn-sm">Join Now</a>`;

    header.className = "navbar";
    header.innerHTML = `
      <div class="container">
        <a href="${user ? auth.dashboardUrl(user) : "index.html"}" class="brand">
          <span class="brand-icon"><i class="fa-solid fa-dumbbell"></i></span><span>Fit<span class="accent">Flex</span></span>
        </a>
        <button class="nav-toggle" id="nav-toggle" aria-label="Open menu" aria-expanded="false">
          <i class="fa-solid fa-bars"></i>
        </button>
        <nav class="nav-menu" id="nav-menu">
          <ul class="nav-links">${linkHtml}</ul>
          <div class="nav-actions">${actions}</div>
        </nav>
      </div>`;

    const toggle = document.getElementById("nav-toggle");
    const menu = document.getElementById("nav-menu");
    toggle.addEventListener("click", () => {
      const open = menu.classList.toggle("open");
      toggle.setAttribute("aria-expanded", String(open));
      toggle.innerHTML = `<i class="fa-solid ${open ? "fa-xmark" : "fa-bars"}"></i>`;
    });

    const logoutBtn = document.getElementById("logout-btn");
    if (logoutBtn) logoutBtn.addEventListener("click", auth.logout);
  },

  renderFooter() {
    const footer = document.getElementById("footer");
    if (!footer) return;
    footer.className = "footer";
    footer.innerHTML = `
      <div class="container">
        <div class="footer-grid">
          <div>
            <a href="index.html" class="brand"><span class="brand-icon"><i class="fa-solid fa-dumbbell"></i></span><span>Fit<span class="accent">Flex</span></span></a>
            <p class="mt-2">Online training with certified trainers, personalised diet plans, gym finder,
              progress tracking and AI pose detection, all in one place.</p>
          </div>
          <div>
            <h4>Explore</h4>
            <ul>
              <li><a href="trainers.html">Find a Trainer</a></li>
              <li><a href="gyms.html">Nearby Gyms</a></li>
              <li><a href="pose.html">Pose Detection</a></li>
              <li><a href="nutrition.html">Nutrition Plans</a></li>
            </ul>
          </div>
          <div>
            <h4>Portals</h4>
            <ul>
              <li><a href="login.html">User Login</a></li>
              <li><a href="register.html">User Sign up</a></li>
              <li><a href="trainer-login.html">Trainer Login</a></li>
              <li><a href="trainer-register.html">Become a Trainer</a></li>
            </ul>
          </div>
          <div>
            <h4>FitFlex</h4>
            <ul>
              <li><a href="about.html">About</a></li>
              <li><a href="contact.html">Contact</a></li>
            </ul>
          </div>
        </div>
        <div class="footer-bottom">
          <span>&copy; ${new Date().getFullYear()} FitFlex. B.Tech major project.</span>
          <span>Made with <i class="fa-solid fa-heart accent"></i> for healthier lives</span>
        </div>
      </div>`;
  },

  /** Shows a one-time message left by a redirect (e.g. "Please log in"). */
  showFlash() {
    const message = sessionStorage.getItem("fitflex_flash");
    if (message) {
      sessionStorage.removeItem("fitflex_flash");
      ui.toast(message, "warning", 5000);
    }
  },
};

// Runs on every page once the HTML is parsed (page scripts add their own listeners after this one).
document.addEventListener("DOMContentLoaded", () => {
  auth.renderNavbar();
  auth.renderFooter();
  auth.showFlash();
  ui.initReveal();
});
