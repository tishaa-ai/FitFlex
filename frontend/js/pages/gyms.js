/*
 * Gyms page (public). Gets a location (browser geolocation, a city, or typed lat/lng),
 * calls GET /api/gym/nearby and shows the results on a map plus a side list.
 * Map: Google Maps if GOOGLE_MAPS_BROWSER_KEY is set in config.js, otherwise Leaflet with
 * free OpenStreetMap tiles.
 */
const DEFAULT_LOCATION = { lat: 21.1458, lng: 79.0882 }; // Nagpur
const googleKey = (window.FITFLEX_CONFIG && window.FITFLEX_CONFIG.GOOGLE_MAPS_BROWSER_KEY) || "";

/** A tiny common interface so the rest of the code does not care which map library is used. */
let mapAdapter = null;

document.addEventListener("DOMContentLoaded", async () => {
  const form = document.getElementById("location-form");

  try {
    mapAdapter = googleKey ? await createGoogleMap() : createLeafletMap();
  } catch (error) {
    console.warn("Map could not load:", error);
    document.getElementById("map").innerHTML = ui.emptyHtml("fa-map", "The map could not be loaded, but the list still works.");
    mapAdapter = { setCenter() {}, showGyms() {}, focus() {} };
  }

  form.city.addEventListener("change", () => {
    if (!form.city.value) return;
    const [lat, lng] = form.city.value.split(",");
    form.lat.value = lat;
    form.lng.value = lng;
    search();
  });
  form.lat.addEventListener("input", () => (form.city.value = ""));
  form.lng.addEventListener("input", () => (form.city.value = ""));
  form.radius.addEventListener("change", search);
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    search();
  });
  document.getElementById("locate-btn").addEventListener("click", useMyLocation);

  search();
});

function useMyLocation() {
  const button = document.getElementById("locate-btn");
  if (!("geolocation" in navigator)) {
    ui.toast("Your browser does not support location. Pick a city instead.", "warning");
    return;
  }
  ui.setLoading(button, true, "Locating...");
  navigator.geolocation.getCurrentPosition(
    (position) => {
      const form = document.getElementById("location-form");
      form.lat.value = position.coords.latitude.toFixed(5);
      form.lng.value = position.coords.longitude.toFixed(5);
      form.city.value = "";
      ui.setLoading(button, false);
      search();
    },
    (error) => {
      ui.setLoading(button, false);
      ui.toast(error.code === 1
        ? "Location permission denied. Pick a city or type coordinates instead."
        : "Could not get your location. Pick a city instead.", "warning", 6000);
    },
    { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 });
}

async function search() {
  const form = document.getElementById("location-form");
  if (!ui.validateForm(form)) return;
  const lat = Number(form.lat.value) || DEFAULT_LOCATION.lat;
  const lng = Number(form.lng.value) || DEFAULT_LOCATION.lng;
  const radius = Number(form.radius.value);

  const list = document.getElementById("gym-list");
  const count = document.getElementById("gym-count");
  const note = document.getElementById("source-note");
  count.textContent = "Gyms";
  ui.showLoader(list, "Searching nearby gyms (this can take a few seconds)...");
  mapAdapter.setCenter(lat, lng, radius);

  try {
    const data = await api.get("/gym/nearby" + api.query({ lat, lng, radius }));
    const sourceLabel = { GOOGLE_PLACES: "Google Places", OPENSTREETMAP: "OpenStreetMap", NONE: "no provider" }[data.source] || data.source;
    note.textContent = `Results from ${sourceLabel} within ${radius / 1000} km.`;
    count.textContent = `${data.gyms.length} gym${data.gyms.length === 1 ? "" : "s"} found`;

    const message = data.message ? `<div class="alert alert-warning"><i class="fa-solid fa-circle-info"></i><div>${ui.escape(data.message)}</div></div>` : "";
    if (!data.gyms.length) {
      list.innerHTML = message + ui.emptyHtml("fa-dumbbell", "No gyms found here. Try a bigger radius or another city.");
      mapAdapter.showGyms([]);
      return;
    }
    list.innerHTML = message + data.gyms.map((g, i) => `
      <div class="gym-item" data-index="${i}" tabindex="0">
        <h4>${i + 1}. ${ui.escape(g.name)}</h4>
        <div class="muted small">${ui.escape(g.address)}</div>
        <div class="row-between mt-1">
          <span class="small"><i class="fa-solid fa-route accent"></i> ${g.distanceKm} km
            ${g.rating ? ` &middot; ${ui.ratingHtml(g.rating)}` : ""}</span>
          <a href="${ui.escape(g.directionsUrl)}" target="_blank" rel="noopener" class="btn btn-ghost btn-sm"><i class="fa-solid fa-diamond-turn-right"></i> Get Directions</a>
        </div>
      </div>`).join("");

    mapAdapter.showGyms(data.gyms);
    list.querySelectorAll(".gym-item").forEach((item) => {
      const select = () => {
        list.querySelectorAll(".gym-item").forEach((el) => el.classList.toggle("selected", el === item));
        mapAdapter.focus(Number(item.dataset.index));
      };
      item.addEventListener("click", (event) => {
        if (!event.target.closest("a")) select();
      });
      item.addEventListener("keydown", (event) => {
        if (event.key === "Enter") select();
      });
    });
  } catch (error) {
    count.textContent = "Gyms";
    list.innerHTML = ui.errorHtml(error.message);
  }
}

function popupHtml(g) {
  return `<strong>${ui.escape(g.name)}</strong><br>${ui.escape(g.address)}<br>${g.distanceKm} km away<br>
    <a href="${ui.escape(g.directionsUrl)}" target="_blank" rel="noopener">Get Directions</a>`;
}

// ===================== Leaflet + OpenStreetMap (default, no key) =====================

function createLeafletMap() {
  if (typeof L === "undefined") throw new Error("Leaflet did not load");
  const map = L.map("map").setView([DEFAULT_LOCATION.lat, DEFAULT_LOCATION.lng], 13);
  // OpenStreetMap's tile usage policy requires this attribution.
  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  }).addTo(map);

  const layer = L.layerGroup().addTo(map);
  let centerLayer = null;
  let markers = [];

  return {
    setCenter(lat, lng, radius) {
      if (centerLayer) centerLayer.remove();
      centerLayer = L.layerGroup([
        L.circle([lat, lng], { radius, color: "#b5f23d", weight: 1, fillOpacity: 0.06 }),
        L.circleMarker([lat, lng], { radius: 7, color: "#121417", weight: 2, fillColor: "#4fb3ff", fillOpacity: 1 })
          .bindPopup("Search location"),
      ]).addTo(map);
      map.fitBounds(L.latLng(lat, lng).toBounds(radius * 2));
    },
    showGyms(gyms) {
      layer.clearLayers();
      markers = gyms.map((g, i) => L.marker([g.lat, g.lng], { title: `${i + 1}. ${g.name}` }).bindPopup(popupHtml(g)).addTo(layer));
    },
    focus(index) {
      const marker = markers[index];
      if (!marker) return;
      map.setView(marker.getLatLng(), Math.max(map.getZoom(), 15));
      marker.openPopup();
    },
  };
}

// ===================== Google Maps (only when a browser key is configured) =====================

function loadGoogleMapsScript() {
  return new Promise((resolve, reject) => {
    if (window.google && window.google.maps) return resolve();
    window.__fitflexMapsReady = resolve;
    const script = document.createElement("script");
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(googleKey)}&callback=__fitflexMapsReady&loading=async`;
    script.async = true;
    script.onerror = () => reject(new Error("Google Maps failed to load"));
    document.head.appendChild(script);
  });
}

async function createGoogleMap() {
  await loadGoogleMapsScript();
  const map = new google.maps.Map(document.getElementById("map"), {
    center: DEFAULT_LOCATION,
    zoom: 13,
    colorScheme: "DARK",
  });
  const info = new google.maps.InfoWindow();
  let markers = [];
  let circle = null;

  return {
    setCenter(lat, lng, radius) {
      if (circle) circle.setMap(null);
      circle = new google.maps.Circle({ map, center: { lat, lng }, radius, strokeColor: "#b5f23d", strokeWeight: 1, fillOpacity: 0.06 });
      map.fitBounds(circle.getBounds());
    },
    showGyms(gyms) {
      markers.forEach((m) => m.setMap(null));
      markers = gyms.map((g, i) => {
        const marker = new google.maps.Marker({ map, position: { lat: g.lat, lng: g.lng }, title: g.name, label: String(i + 1) });
        marker.addListener("click", () => {
          info.setContent(popupHtml(g));
          info.open({ map, anchor: marker });
        });
        return marker;
      });
    },
    focus(index) {
      const marker = markers[index];
      if (!marker) return;
      map.panTo(marker.getPosition());
      map.setZoom(Math.max(map.getZoom(), 15));
      google.maps.event.trigger(marker, "click");
    },
  };
}
