export const normalizedName = value => String(value || "").trim().replace(/\s+/g, " ").toLowerCase();

export function absoluteAssetUrl(origin, value) {
  const path = String(value || "").trim();
  if (!path) return null;
  if (/^https?:\/\//i.test(path)) return path;
  return new URL(path.startsWith("/") ? path : `/${path}`, origin).toString();
}

export function shortName(value) {
  const words = String(value || "").trim().split(/\s+/).filter(Boolean);
  return (words.length > 1 ? words.map(word => word[0]).join("") : words[0] || "TEAM").toUpperCase().slice(0, 12);
}
