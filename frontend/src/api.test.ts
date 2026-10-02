import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, getJson } from "./api";

describe("getJson", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("returns the parsed body for a successful response", async () => {
    vi.stubGlobal("fetch", async () => Response.json({ title: "Hope" }));
    await expect(getJson("/api/poem")).resolves.toEqual({ title: "Hope" });
  });

  it("throws a readable ApiError instead of parsing an error page", async () => {
    vi.stubGlobal("fetch", async () => new Response("busy", { status: 429 }));
    const failure = getJson("/api/poem");
    await expect(failure).rejects.toBeInstanceOf(ApiError);
    await expect(failure).rejects.toMatchObject({
      status: 429,
      message:
        "The site is busy right now. Please wait a moment and try again.",
    });
  });
});
