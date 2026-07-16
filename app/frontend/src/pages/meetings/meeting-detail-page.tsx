import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Button } from "../../components/ui/button";
import { Modal } from "../../components/ui/modal";
import { SelectField } from "../../components/ui/select";
import * as meetingsApi from "../../lib/api/meetings";
import { ApiError } from "../../lib/api-client";
import type { MeetingResponse } from "../../lib/types";
import { MeetingForm } from "./meeting-form";

const RSVP_OPTIONS = [
  { value: "pending", label: "Pending" },
  { value: "accepted", label: "Accepted" },
  { value: "declined", label: "Declined" },
];

export function MeetingDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [meeting, setMeeting] = useState<MeetingResponse | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    if (!id) return;
    try {
      setMeeting(await meetingsApi.getMeeting(id));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not load meeting");
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function handleUpdate(body: Parameters<typeof meetingsApi.createMeeting>[0]) {
    if (!id) return;
    await meetingsApi.updateMeeting(id, {
      title: body.title,
      notes: body.notes,
      startTime: body.startTime,
      endTime: body.endTime,
      meetingTimezone: body.meetingTimezone,
    });
    setIsEditing(false);
    await load();
  }

  async function handleDelete() {
    if (!id) return;
    await meetingsApi.deleteMeeting(id);
    navigate("/meetings");
  }

  async function handleRsvp(inviteeId: string, response: string) {
    if (!id) return;
    try {
      const updated = await meetingsApi.updateRsvp(id, inviteeId, { response });
      setMeeting(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update RSVP");
    }
  }

  if (error) return <div className="page">{error}</div>;
  if (!meeting) return <div className="page-loading">Loading…</div>;

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <Link to="/meetings">&larr; Back to meetings</Link>
          <h1>{meeting.title}</h1>
        </div>
        <div style={{ display: "flex", gap: "0.5rem" }}>
          <Button variant="secondary" onClick={() => setIsEditing(true)}>
            Edit
          </Button>
          <Button variant="danger" onClick={handleDelete}>
            Delete
          </Button>
        </div>
      </div>

      <p>
        <strong>Host:</strong> {meeting.host}
      </p>
      <p>
        <strong>When:</strong> {meeting.start} &ndash; {meeting.end} ({meeting.timezone})
      </p>
      <p>
        <strong>Status:</strong> <span className="badge">{meeting.status}</span>
      </p>
      {meeting.notes && (
        <p>
          <strong>Notes:</strong> {meeting.notes}
        </p>
      )}

      <h2>Invitees</h2>
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Email</th>
            <th>RSVP</th>
          </tr>
        </thead>
        <tbody>
          {meeting.invitees.map((invitee) => (
            <tr key={invitee.id}>
              <td>{invitee.contactName}</td>
              <td>{invitee.contactEmail}</td>
              <td>
                <SelectField
                  label=""
                  id={`rsvp-${invitee.id}`}
                  value={invitee.response}
                  onChange={(e) => handleRsvp(invitee.id, e.target.value)}
                  options={RSVP_OPTIONS}
                />
              </td>
            </tr>
          ))}
          {meeting.invitees.length === 0 && (
            <tr>
              <td colSpan={3}>No invitees.</td>
            </tr>
          )}
        </tbody>
      </table>

      {isEditing && (
        <Modal title="Edit Meeting" onClose={() => setIsEditing(false)}>
          <MeetingForm initial={meeting} onSubmit={handleUpdate} onCancel={() => setIsEditing(false)} />
        </Modal>
      )}
    </div>
  );
}
