import { describe, expect, it } from "vitest";
import { bioSnippet, formatDate, slugify, timeAgo } from "./poetry";

describe("poetry display helpers", () => {
  it("creates stable URL slugs", () => {
    expect(slugify("A Raven's Flight!")).toBe("a-raven-s-flight");
    expect(slugify("***")).toBe("poem");
  });

  it("formats API timestamps in UTC", () => {
    expect(formatDate("1999-03-12T00:00:00Z")).toBe("Mar 12, 1999");
  });

  it("normalizes and truncates biographies", () => {
    expect(bioSnippet("  A\n poet\t writes. ")).toBe("A poet writes.");
    expect(bioSnippet("abcdef", 5)).toBe("ab…");
  });

  it("describes how long ago a time was", () => {
    const now = new Date("2026-10-02T12:00:00Z");
    expect(timeAgo("2026-10-02T11:59:30Z", now)).toBe("just now");
    expect(timeAgo("2026-10-02T09:00:00Z", now)).toBe("3 hours ago");
    expect(timeAgo("2026-10-01T12:00:00Z", now)).toBe("yesterday");
    expect(timeAgo("2026-07-01T12:00:00Z", now)).toBe("3 months ago");
  });
});
