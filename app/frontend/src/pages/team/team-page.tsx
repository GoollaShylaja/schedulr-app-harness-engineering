import { useEffect, useState, type FormEvent } from "react";
import { Button } from "../../components/ui/button";
import { Field } from "../../components/ui/input";
import { Modal } from "../../components/ui/modal";
import { SelectField } from "../../components/ui/select";
import * as teamsApi from "../../lib/api/teams";
import { ApiError } from "../../lib/api-client";
import { useAuth } from "../../lib/auth-context";
import type { TeamResponse } from "../../lib/types";

const ROLE_OPTIONS = [
  { value: "member", label: "Member" },
  { value: "admin", label: "Admin" },
];

export function TeamPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === "admin";

  const [team, setTeam] = useState<TeamResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isInviting, setIsInviting] = useState(false);
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteName, setInviteName] = useState("");
  const [inviteRole, setInviteRole] = useState("member");
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function load() {
    try {
      setTeam(await teamsApi.getMyTeam());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not load team");
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function handleInvite(e: FormEvent) {
    e.preventDefault();
    setInviteError(null);
    setIsSubmitting(true);
    try {
      await teamsApi.inviteMember({ email: inviteEmail, fullName: inviteName, role: inviteRole });
      setIsInviting(false);
      setInviteEmail("");
      setInviteName("");
      setInviteRole("member");
      await load();
    } catch (err) {
      setInviteError(err instanceof ApiError ? err.message : "Could not invite member");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleRoleChange(userId: string, role: string) {
    setError(null);
    try {
      await teamsApi.updateMemberRole(userId, { role });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update role");
    }
  }

  async function handleRemove(userId: string) {
    setError(null);
    try {
      await teamsApi.removeMember(userId);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not remove member");
    }
  }

  if (!team) return <div className="page-loading">Loading…</div>;

  return (
    <div className="page">
      <div className="page-header">
        <h1>{team.name}</h1>
        {isAdmin && <Button onClick={() => setIsInviting(true)}>Invite Member</Button>}
      </div>

      {error && <p className="form-error">{error}</p>}

      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Email</th>
            <th>Timezone</th>
            <th>Role</th>
            {isAdmin && <th></th>}
          </tr>
        </thead>
        <tbody>
          {team.members.map((member) => (
            <tr key={member.id}>
              <td>{member.name}</td>
              <td>{member.email}</td>
              <td>{member.timezone}</td>
              <td>
                {isAdmin ? (
                  <SelectField
                    label=""
                    id={`role-${member.id}`}
                    value={member.role}
                    onChange={(e) => handleRoleChange(member.id, e.target.value)}
                    options={ROLE_OPTIONS}
                  />
                ) : (
                  <span className="badge">{member.role}</span>
                )}
              </td>
              {isAdmin && (
                <td>
                  <Button variant="danger" onClick={() => handleRemove(member.id)}>
                    Remove
                  </Button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>

      {isInviting && (
        <Modal title="Invite Member" onClose={() => setIsInviting(false)}>
          <form onSubmit={handleInvite}>
            <Field
              label="Full name"
              value={inviteName}
              onChange={(e) => setInviteName(e.target.value)}
              required
            />
            <Field
              label="Email"
              type="email"
              value={inviteEmail}
              onChange={(e) => setInviteEmail(e.target.value)}
              required
            />
            <SelectField
              label="Role"
              value={inviteRole}
              onChange={(e) => setInviteRole(e.target.value)}
              options={ROLE_OPTIONS}
            />
            {inviteError && <p className="form-error">{inviteError}</p>}
            <div style={{ display: "flex", gap: "0.5rem" }}>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? "Inviting…" : "Invite"}
              </Button>
              <Button type="button" variant="secondary" onClick={() => setIsInviting(false)}>
                Cancel
              </Button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}
