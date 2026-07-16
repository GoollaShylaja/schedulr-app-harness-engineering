import { useState, type FormEvent } from "react";
import { Button } from "../../components/ui/button";
import { Field } from "../../components/ui/input";
import * as authApi from "../../lib/api/auth";
import { ApiError } from "../../lib/api-client";
import { useAuth } from "../../lib/auth-context";

export function SettingsPage() {
  const { user, refreshUser } = useAuth();

  const [fullName, setFullName] = useState(user?.fullName ?? "");
  const [timezone, setTimezone] = useState(user?.timezone ?? "UTC");
  const [profileError, setProfileError] = useState<string | null>(null);
  const [profileSuccess, setProfileSuccess] = useState(false);
  const [isSavingProfile, setIsSavingProfile] = useState(false);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordSuccess, setPasswordSuccess] = useState(false);
  const [isSavingPassword, setIsSavingPassword] = useState(false);

  async function handleProfileSubmit(e: FormEvent) {
    e.preventDefault();
    setProfileError(null);
    setProfileSuccess(false);
    setIsSavingProfile(true);
    try {
      await authApi.updateProfile({ fullName, timezone });
      await refreshUser();
      setProfileSuccess(true);
    } catch (err) {
      setProfileError(err instanceof ApiError ? err.message : "Could not update profile");
    } finally {
      setIsSavingProfile(false);
    }
  }

  async function handlePasswordSubmit(e: FormEvent) {
    e.preventDefault();
    setPasswordError(null);
    setPasswordSuccess(false);
    setIsSavingPassword(true);
    try {
      await authApi.changePassword({ currentPassword, newPassword });
      setCurrentPassword("");
      setNewPassword("");
      setPasswordSuccess(true);
    } catch (err) {
      setPasswordError(err instanceof ApiError ? err.message : "Could not change password");
    } finally {
      setIsSavingPassword(false);
    }
  }

  return (
    <div className="page">
      <h1>Settings</h1>

      <h2>Profile</h2>
      <form onSubmit={handleProfileSubmit}>
        <Field label="Full name" value={fullName} onChange={(e) => setFullName(e.target.value)} />
        <Field
          label="Timezone"
          value={timezone}
          onChange={(e) => setTimezone(e.target.value)}
          placeholder="e.g. America/Chicago"
        />
        {profileError && <p className="form-error">{profileError}</p>}
        {profileSuccess && <p>Profile updated.</p>}
        <Button type="submit" disabled={isSavingProfile}>
          {isSavingProfile ? "Saving…" : "Save profile"}
        </Button>
      </form>

      <h2 style={{ marginTop: "2rem" }}>Change password</h2>
      <form onSubmit={handlePasswordSubmit}>
        <Field
          label="Current password"
          type="password"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          required
        />
        <Field
          label="New password"
          type="password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          minLength={8}
          required
        />
        {passwordError && <p className="form-error">{passwordError}</p>}
        {passwordSuccess && <p>Password changed.</p>}
        <Button type="submit" disabled={isSavingPassword}>
          {isSavingPassword ? "Saving…" : "Change password"}
        </Button>
      </form>
    </div>
  );
}
