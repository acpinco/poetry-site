import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router";
import { ApiError, getJson, sendJson } from "../api";
import { useSession } from "../session";
import AuthCard, {
  AuthDivider,
  AuthLoading,
  AuthSubmitButton,
  authInputClass,
  authLabelClass,
} from "./AuthCard";

type Profile = {
  firstName: string;
  lastName: string;
  fullName: string;
  penName: string;
  bio: string | null;
};

function failureMessage(reason: unknown) {
  if (reason instanceof ApiError && reason.status === 409)
    return "That pen name is already taken. Please choose another.";
  if (
    reason instanceof ApiError &&
    (reason.status === 401 || reason.status === 403)
  )
    return "Please sign in with a magic link before creating your profile.";
  return "We could not save your profile. Please try again.";
}

/** Creates a new poet profile, or edits the signed-in poet's existing one. */
export default function ProfilePage() {
  const { session, refresh } = useSession();
  const navigate = useNavigate();
  const signedIn = session.status === "signed-in" ? session : null;
  const existingProfile = Boolean(signedIn?.poetId);
  const [loaded, setLoaded] = useState(!existingProfile);
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [penName, setPenName] = useState("");
  const [bio, setBio] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const derivedPenName =
    penName.trim() ||
    (firstName.trim() && lastName.trim()
      ? `${firstName.trim()} ${lastName.trim()}`
      : "");

  useEffect(() => {
    if (!existingProfile) return;
    let cancelled = false;
    getJson<Profile>("/api/poets/me")
      .then((profile) => {
        if (cancelled) return;
        setFirstName(profile.firstName ?? "");
        setLastName(profile.lastName ?? "");
        // The API reports the full name as the pen name when none was chosen.
        setPenName(
          profile.penName === profile.fullName ? "" : (profile.penName ?? ""),
        );
        setBio(profile.bio ?? "");
      })
      .catch(() => {
        if (!cancelled) setError("Your profile could not be loaded.");
      })
      .finally(() => {
        if (!cancelled) setLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, [existingProfile]);

  if (!loaded) return <AuthLoading />;

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      await sendJson(
        existingProfile ? "/api/poets/me" : "/api/poets",
        existingProfile ? "PUT" : "POST",
        {
          firstName,
          lastName,
          penName: penName.trim() || null,
          bio: bio.trim() || null,
        },
      );
      // A new profile attaches to the session, so re-read it before leaving.
      if (!existingProfile) await refresh();
      navigate(existingProfile ? "/home?view=mine" : "/home");
    } catch (reason) {
      setError(failureMessage(reason));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard backTo={existingProfile ? "/home?view=mine" : undefined}>
      <form onSubmit={save} className="space-y-5">
        <div>
          <p className={authLabelClass}>Account Email</p>
          <p className="break-all rounded border border-line bg-inset px-4 py-3 text-sm text-parchment">
            {signedIn?.email}
          </p>
          <p className="text-xs mt-1.5 italic text-faint">
            Sign-in links for this profile are sent here.
          </p>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Field
            label="First Name"
            id="firstName"
            value={firstName}
            onChange={setFirstName}
            placeholder="Eleanor"
          />
          <Field
            label="Last Name"
            id="lastName"
            value={lastName}
            onChange={setLastName}
            placeholder="Voss"
          />
        </div>
        <div>
          <label htmlFor="penName" className={authLabelClass}>
            Pen Name <span className="text-faint italic">— optional</span>
          </label>
          <input
            id="penName"
            value={penName}
            onChange={(event) => setPenName(event.target.value)}
            maxLength={100}
            placeholder={derivedPenName || "Your name for the world to know"}
            className={authInputClass}
          />
          {derivedPenName && !penName && (
            <p className="text-xs mt-1.5 italic text-faint">
              Will appear as{" "}
              <span className="text-gold/53">{derivedPenName}</span>
            </p>
          )}
        </div>
        <div>
          <label htmlFor="bio" className={authLabelClass}>
            A Few Words About You{" "}
            <span className="text-faint italic">— optional</span>
          </label>
          <textarea
            id="bio"
            rows={4}
            value={bio}
            onChange={(event) => setBio(event.target.value)}
            maxLength={500}
            placeholder="I write at the edge of night, where the words I cannot speak find their shape..."
            className={`${authInputClass} resize-none leading-relaxed`}
          />
          <p className="text-xs mt-1.5 text-right text-faint">
            {bio.length} / 500
          </p>
        </div>
        {error && (
          <p role="alert" className="text-sm text-red-300">
            {error}
          </p>
        )}
        <AuthDivider />
        <AuthSubmitButton busy={submitting} busyLabel="Saving…" />
      </form>
    </AuthCard>
  );
}

function Field({
  label,
  id,
  value,
  onChange,
  placeholder,
}: {
  label: string;
  id: string;
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
}) {
  return (
    <div>
      <label htmlFor={id} className={authLabelClass}>
        {label} <span className="text-gold">*</span>
      </label>
      <input
        id={id}
        required
        value={value}
        onChange={(event) => onChange(event.target.value)}
        maxLength={100}
        placeholder={placeholder}
        className={authInputClass}
      />
    </div>
  );
}
