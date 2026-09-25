export function slugify(value: string) {
  const slug = value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
  return slug || "poem";
}

export function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(value));
}

export function bioSnippet(value: string, maximumLength = 180) {
  const compact = value.replace(/\s+/g, " ").trim();
  return compact.length <= maximumLength
    ? compact
    : `${compact.slice(0, maximumLength - 3)}…`;
}
