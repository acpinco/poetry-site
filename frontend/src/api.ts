/** A non-OK HTTP response, carrying a message that is safe to show visitors. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

function messageFor(status: number) {
  if (status === 429)
    return "The site is busy right now. Please wait a moment and try again.";
  if (status === 404) return "That page could not be found.";
  if (status === 401 || status === 403) return "Please sign in again.";
  return "Something went wrong. Please try again.";
}

/** Fetches JSON, throwing an {@link ApiError} for any non-OK response. */
export async function getJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok)
    throw new ApiError(response.status, messageFor(response.status));
  return (await response.json()) as T;
}

/**
 * Sends a JSON request (POST, PUT, DELETE), throwing an {@link ApiError} for any
 * non-OK response. Resolves to the parsed body, or undefined for an empty one.
 */
export async function sendJson<T>(
  url: string,
  method: "POST" | "PUT" | "DELETE",
  body?: unknown,
): Promise<T | undefined> {
  const response = await fetch(url, {
    method,
    headers:
      body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok)
    throw new ApiError(response.status, messageFor(response.status));
  const text = await response.text();
  return text ? (JSON.parse(text) as T) : undefined;
}

/** True for a fetch that was cancelled on purpose with an AbortController. */
export function isAbort(reason: unknown) {
  return reason instanceof DOMException && reason.name === "AbortError";
}
