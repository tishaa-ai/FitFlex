/*
 * api.js: the only place that talks to the backend.
 * Every request goes through apiRequest(), which
 *   1. adds "Authorization: Bearer <token>" when the user is logged in,
 *   2. turns JSON responses into JavaScript objects,
 *   3. turns error responses ({status, message, errors}) into an ApiError you can catch,
 *   4. logs the user out and sends them to the login page on 401 (expired or invalid token).
 */
const API_BASE_URL =
  (window.FITFLEX_CONFIG && window.FITFLEX_CONFIG.API_BASE_URL) || "http://localhost:8080/api";

/** Error thrown for any failed request. `errors` holds per-field validation messages. */
class ApiError extends Error {
  constructor(status, message, errors) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.errors = errors || {};
  }
}

async function apiRequest(path, options = {}) {
  const { method = "GET", body, isForm = false, raw = false } = options;
  const headers = {};
  const token = localStorage.getItem("fitflex_token");
  if (token) headers["Authorization"] = "Bearer " + token;

  // For FormData (file uploads) the browser sets the multipart Content-Type itself.
  if (body !== undefined && !isForm) headers["Content-Type"] = "application/json";

  let response;
  try {
    response = await fetch(API_BASE_URL + path, {
      method,
      headers,
      body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    });
  } catch (networkError) {
    // fetch only rejects when the server cannot be reached at all.
    throw new ApiError(0, `Cannot reach the FitFlex server at ${API_BASE_URL}. Is the backend running?`);
  }

  if (response.status === 401 && token) {
    // Our token is no longer accepted: clear it and ask the user to log in again.
    auth.handleExpiredSession();
    throw new ApiError(401, "Your session has expired. Please log in again.");
  }

  if (!response.ok) {
    let data = {};
    try {
      data = await response.json();
    } catch (e) {
      /* body was not JSON */
    }
    throw new ApiError(response.status, data.message || `Request failed (${response.status})`, data.errors);
  }

  if (raw) return response;                 // caller wants the Response (e.g. file download)
  if (response.status === 204) return null; // No Content
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

/** Short helpers so pages can write api.get("/trainers") etc. */
const api = {
  get: (path) => apiRequest(path),
  post: (path, body) => apiRequest(path, { method: "POST", body: body === undefined ? {} : body }),
  put: (path, body) => apiRequest(path, { method: "PUT", body }),
  del: (path) => apiRequest(path, { method: "DELETE" }),
  upload: (path, formData) => apiRequest(path, { method: "POST", body: formData, isForm: true }),

  /** Builds "?a=1&b=2" from an object, skipping empty values. */
  query(params) {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== null && value !== undefined && String(value).trim() !== "") search.append(key, value);
    });
    const text = search.toString();
    return text ? "?" + text : "";
  },

  /**
   * Downloads a protected file. A normal <a href> link cannot send the JWT header, so we
   * fetch the file as a Blob and then "click" a temporary link to save it.
   */
  async download(path, fallbackName = "download") {
    const response = await apiRequest(path, { raw: true });
    const disposition = response.headers.get("Content-Disposition") || "";
    const match = /filename\*=UTF-8''([^;]+)|filename="([^"]+)"/i.exec(disposition);
    const fileName = match ? decodeURIComponent(match[1] || match[2]) : fallbackName;

    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
};
