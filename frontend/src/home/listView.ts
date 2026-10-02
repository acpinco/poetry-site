/** Which list the reading room shows in its sidebar. */
export type ListKind = "mine" | "poet" | "all";

/**
 * What the reading room should load for a URL:
 * - a list to show,
 * - "resolve-poem" for a bare ?poem= link, whose poet is not known until the poem loads,
 * - "waiting" until the session is known, since the default depends on who is reading.
 */
export type ListView = ListKind | "resolve-poem" | "waiting";

export function resolveListView(
  params: URLSearchParams,
  session: { loading: boolean; poetId: string | null },
): ListView {
  if (session.loading) return "waiting";
  // ?mine=1 is the older spelling of ?view=mine.
  const view =
    params.get("view") ?? (params.get("mine") === "1" ? "mine" : null);
  if (view === "mine" && session.poetId) return "mine";
  if (view === "all") return "all";
  if (params.get("poet")) return "poet";
  if (params.get("poem")) return "resolve-poem";
  // Poets start in their own collection; everyone else browses the newest poems.
  return session.poetId ? "mine" : "all";
}
