/*
 * FitFlex frontend configuration.
 * Copy this file to js/config.js (which is gitignored) and edit the values there.
 *   PowerShell:  Copy-Item frontend\js\config.example.js frontend\js\config.js
 */
window.FITFLEX_CONFIG = {
  // Where the Spring Boot backend is running.
  API_BASE_URL: "http://localhost:8080/api",

  // Optional. A Google Maps *browser* key (restricted to your site's URL) for gyms.html.
  // Leave empty to use free OpenStreetMap tiles with Leaflet instead.
  GOOGLE_MAPS_BROWSER_KEY: "",
};
