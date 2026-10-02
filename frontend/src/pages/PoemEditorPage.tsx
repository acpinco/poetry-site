import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { ApiError, getJson, sendJson } from "../api";
import ravenLogo from "../imports/Raven_Logo.png";

type EditablePoem = { poemId: string; title: string; poem: string };

/** Writes a new poem, or edits one of the signed-in poet's poems. */
export default function PoemEditorPage() {
  const { poemId } = useParams();
  const navigate = useNavigate();
  const [title, setTitle] = useState("");
  const [poem, setPoem] = useState("");
  const [loading, setLoading] = useState(Boolean(poemId));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const returnPath = poemId
    ? `/home?view=mine&poem=${encodeURIComponent(poemId)}`
    : "/home?view=mine";

  useEffect(() => {
    if (!poemId) return;
    let cancelled = false;
    getJson<EditablePoem>(`/api/poems/${poemId}`)
      .then((value) => {
        if (cancelled) return;
        setTitle(value.title);
        setPoem(value.poem);
      })
      .catch(() => {
        if (!cancelled) setError("That poem could not be opened for editing.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [poemId]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSaving(true);
    try {
      const saved = await sendJson<EditablePoem>(
        poemId ? `/api/poems/${poemId}` : "/api/poems",
        poemId ? "PUT" : "POST",
        { title, poem },
      );
      navigate(`/home?view=mine&poem=${encodeURIComponent(saved!.poemId)}`);
    } catch (reason) {
      setError(
        reason instanceof ApiError && reason.status === 429
          ? reason.message
          : "Your poem could not be saved. Please try again.",
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="min-h-screen bg-night px-4 py-10 text-cream sm:py-16">
      <div className="mx-auto max-w-3xl">
        <Link
          to={returnPath}
          className="mb-8 inline-block text-xs uppercase tracking-widest text-gold"
        >
          ← Back to poems
        </Link>
        <section className="border border-line bg-panel p-6 shadow-[0_0_30px_rgba(201,168,76,.08)] sm:p-10">
          <img
            src={ravenLogo}
            alt="Think or Drink Poetry"
            className="mx-auto h-12 max-w-full object-contain"
          />
          <h1 className="mt-8 text-center font-serif text-3xl">
            {poemId ? "Edit your poem" : "Write a new poem"}
          </h1>
          <p className="mt-2 text-center text-sm text-muted">
            Your words are yours. Take your time.
          </p>
          {loading ? (
            <p className="mt-10 text-center text-muted">Opening poem…</p>
          ) : (
            <form onSubmit={save} className="mt-10 space-y-6">
              <div>
                <label
                  htmlFor="poem-title"
                  className="mb-2 block text-xs uppercase tracking-wider text-muted"
                >
                  Title
                </label>
                <input
                  id="poem-title"
                  required
                  maxLength={200}
                  value={title}
                  onChange={(event) => setTitle(event.target.value)}
                  placeholder="Give your poem a title"
                  className="w-full border border-line bg-night px-4 py-3 text-base outline-none focus:border-gold"
                />
              </div>
              <div>
                <label
                  htmlFor="poem-body"
                  className="mb-2 block text-xs uppercase tracking-wider text-muted"
                >
                  Poem
                </label>
                <textarea
                  id="poem-body"
                  required
                  maxLength={100000}
                  value={poem}
                  onChange={(event) => setPoem(event.target.value)}
                  placeholder="Begin writing…"
                  rows={18}
                  className="w-full resize-y border border-line bg-night px-4 py-3 font-serif text-base leading-relaxed outline-none focus:border-gold"
                />
                <p className="mt-1 text-right text-xs text-muted">
                  {poem.length.toLocaleString()} / 100,000
                </p>
              </div>
              {error && (
                <p role="alert" className="text-sm text-red-300">
                  {error}
                </p>
              )}
              <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                <Link
                  to={returnPath}
                  className="border border-line-strong px-5 py-3 text-center text-xs uppercase tracking-widest text-parchment"
                >
                  Cancel
                </Link>
                <button
                  type="submit"
                  disabled={saving}
                  className="bg-gold px-5 py-3 text-xs font-semibold uppercase tracking-widest text-night disabled:opacity-60"
                >
                  {saving
                    ? "Saving…"
                    : poemId
                      ? "Save changes"
                      : "Publish poem"}
                </button>
              </div>
            </form>
          )}
        </section>
      </div>
    </main>
  );
}
