import { describe, expect, it } from "vitest";
import { bioSnippet, formatDate, slugify } from "./poetry";

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
});
