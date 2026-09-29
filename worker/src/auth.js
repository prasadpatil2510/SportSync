export const ROLES = Object.freeze({ PLAYER: "PLAYER", SCORER: "SCORER", TOURNAMENT_ADMIN: "TOURNAMENT_ADMIN" });
export const normalizeRole = value => Object.values(ROLES).includes(String(value || "").toUpperCase()) ? String(value).toUpperCase() : ROLES.PLAYER;
export const validPin = value => /^\d{4,8}$/.test(String(value || ""));
export const validEmail = value => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value || "").trim());
export const canAccess = (role, allowed) => allowed.includes(normalizeRole(role));
export const testingAccessAllowed = (environment, testingEnabled) => environment === "testing" && testingEnabled === "true";

const bytesToHex = bytes => [...bytes].map(value => value.toString(16).padStart(2, "0")).join("");
const hexToBytes = hex => new Uint8Array((hex.match(/.{1,2}/g) || []).map(value => Number.parseInt(value, 16)));
// Cloudflare Workers currently cap Web Crypto PBKDF2 at 100,000 iterations.
// Keep this value explicit so local tests cannot silently select a higher,
// incompatible runtime default.
export const PBKDF2_ITERATIONS = 100_000;

export function randomHex(length = 32) {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return bytesToHex(bytes);
}

export async function hashValue(value, saltHex = "") {
  const material = await crypto.subtle.importKey("raw", new TextEncoder().encode(String(value)), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt: hexToBytes(saltHex), iterations: PBKDF2_ITERATIONS }, material, 256);
  return bytesToHex(new Uint8Array(bits));
}

export async function tokenHash(token) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token));
  return bytesToHex(new Uint8Array(digest));
}
