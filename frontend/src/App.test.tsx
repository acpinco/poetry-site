// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";

type Route = { status: number; body?: unknown };

/** Serves canned API responses by path; anything else is a 404. */
function stubApi(routes: Record<string, Route>) {
  vi.stubGlobal("fetch", async (input: RequestInfo | URL) => {
    const path = new URL(String(input), "http://localhost").pathname;
    const route = routes[path] ?? { status: 404 };
    return route.body === undefined
      ? new Response(null, { status: route.status })
      : Response.json(route.body, { status: route.status });
  });
}

function openApp(path: string) {
  window.history.pushState({}, "", path);
  render(<App />);
}

describe("account pages", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("sends a signed-out visitor from a poet-only page to sign-in", async () => {
    stubApi({ "/api/auth/me": { status: 401 } });
    openApp("/contact");
    expect(
      await screen.findByRole("heading", { name: "Find your voice" }),
    ).toBeTruthy();
    expect(window.location.pathname).toBe("/sign-in");
  });

  it("asks someone signed in without a profile to create one first", async () => {
    stubApi({
      "/api/auth/me": {
        status: 200,
        body: { email: "new@example.test", poetId: null, admin: false },
      },
    });
    openApp("/my-poems/new");
    expect(await screen.findByText("new@example.test")).toBeTruthy();
    expect(window.location.pathname).toBe("/account/setup");
  });

  it("switches between signing in and creating an account", async () => {
    stubApi({
      "/api/auth/me": { status: 401 },
      "/api/auth/sign-up/config": {
        status: 200,
        body: { turnstileSiteKey: null },
      },
    });
    openApp("/sign-in");
    fireEvent.click(
      await screen.findByRole("button", { name: "Create an account" }),
    );
    expect(
      screen.getByRole("heading", { name: "Join the poets" }),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Sign in instead" }));
    expect(
      screen.getByRole("heading", { name: "Find your voice" }),
    ).toBeTruthy();
  });

  it("explains when sign-in is rate limited", async () => {
    stubApi({
      "/api/auth/me": { status: 401 },
      "/api/auth/magic-links": { status: 429 },
    });
    openApp("/sign-in");
    fireEvent.change(await screen.findByLabelText(/Email Address/), {
      target: { value: "poet@example.test" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Take Flight" }));
    expect(
      await screen.findByText(
        "Too many sign-in requests. Please wait a while and try again.",
      ),
    ).toBeTruthy();
  });

  it("shows the expired-link message from a failed magic link", async () => {
    stubApi({ "/api/auth/me": { status: 401 } });
    openApp("/sign-in?error=magic-link");
    expect(
      await screen.findByText(/That sign-in link is invalid or has expired/),
    ).toBeTruthy();
  });
});
