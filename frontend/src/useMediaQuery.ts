import { useSyncExternalStore } from "react";

/** Tailwind's lg breakpoint, where the reading room switches to its desktop layout. */
export const DESKTOP_QUERY = "(min-width: 1024px)";

/** Whether a CSS media query currently matches, updating when it changes. */
export function useMediaQuery(query: string) {
  return useSyncExternalStore(
    (onChange) => {
      const list = window.matchMedia(query);
      list.addEventListener("change", onChange);
      return () => list.removeEventListener("change", onChange);
    },
    () => window.matchMedia(query).matches,
  );
}
