import type { ReactNode } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import AdminPage from "./pages/AdminPage";
import { AuthLoading } from "./pages/AuthCard";
import ContactPage from "./pages/ContactPage";
import HomePage from "./pages/HomePage";
import PoemEditorPage from "./pages/PoemEditorPage";
import ProfilePage from "./pages/ProfilePage";
import SignInPage from "./pages/SignInPage";
import { SessionProvider, useSession } from "./session";

/** Pages for anyone who has signed in, with or without a poet profile. */
function RequireSession({ children }: { children: ReactNode }) {
  const { session } = useSession();
  if (session.status === "loading") return <AuthLoading />;
  if (session.status === "signed-out")
    return <Navigate to="/sign-in" replace />;
  return children;
}

/** Pages that act as a poet, so they need a finished profile. */
function RequirePoet({ children }: { children: ReactNode }) {
  const { session } = useSession();
  if (session.status === "loading") return <AuthLoading />;
  if (session.status === "signed-out")
    return <Navigate to="/sign-in" replace />;
  if (!session.poetId) return <Navigate to="/account/setup" replace />;
  return children;
}

// The site root, poem, poet, and Poem of the Day pages are server-rendered by
// the backend; this app owns /home and the account pages below.
export default function App() {
  return (
    <SessionProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/home" element={<HomePage />} />
          <Route path="/sign-in" element={<SignInPage />} />
          <Route
            path="/account/setup"
            element={
              <RequireSession>
                <ProfilePage />
              </RequireSession>
            }
          />
          <Route
            path="/my-poems/new"
            element={
              <RequirePoet>
                <PoemEditorPage />
              </RequirePoet>
            }
          />
          <Route
            path="/my-poems/:poemId/edit"
            element={
              <RequirePoet>
                <PoemEditorPage />
              </RequirePoet>
            }
          />
          <Route
            path="/contact"
            element={
              <RequirePoet>
                <ContactPage />
              </RequirePoet>
            }
          />
          <Route
            path="/admin"
            element={
              <RequirePoet>
                <AdminPage />
              </RequirePoet>
            }
          />
          <Route path="*" element={<Navigate to="/home" replace />} />
        </Routes>
      </BrowserRouter>
    </SessionProvider>
  );
}
