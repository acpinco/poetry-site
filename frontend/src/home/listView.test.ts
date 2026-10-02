import { describe, expect, it } from "vitest";
import { resolveListView } from "./listView";

type Session = { loading: boolean; poetId: string | null };
const poet: Session = { loading: false, poetId: "poet-1" };
const reader: Session = { loading: false, poetId: null };
const view = (query: string, session: Session = reader) =>
  resolveListView(new URLSearchParams(query), session);

describe("reading room URLs", () => {
  it("waits for the session before choosing a default", () => {
    expect(view("", { loading: true, poetId: null })).toBe("waiting");
  });

  it("opens a poet's own collection and everyone else's newest poems by default", () => {
    expect(view("", poet)).toBe("mine");
    expect(view("", reader)).toBe("all");
  });

  it("honours an explicit list", () => {
    expect(view("view=all", poet)).toBe("all");
    expect(view("poet=abc", poet)).toBe("poet");
    expect(view("view=mine&poem=p1", poet)).toBe("mine");
  });

  it("still understands the older ?mine=1 links", () => {
    expect(view("mine=1&poem=p1", poet)).toBe("mine");
  });

  it("looks up the poet for a bare ?poem= link, such as from a bio page", () => {
    expect(view("poem=p1", poet)).toBe("resolve-poem");
    expect(view("poem=p1", reader)).toBe("resolve-poem");
  });

  it("does not show My Poems to someone without a profile", () => {
    expect(view("view=mine", reader)).toBe("all");
  });
});
