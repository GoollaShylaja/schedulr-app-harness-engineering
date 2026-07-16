import { useEffect, useState, type FormEvent } from "react";
import { Button } from "../../components/ui/button";
import { Field } from "../../components/ui/input";
import { listContacts } from "../../lib/api/contacts";
import { ApiError } from "../../lib/api-client";
import type { ContactResponse, MeetingCreateRequest, MeetingResponse } from "../../lib/types";

interface MeetingFormProps {
  initial?: MeetingResponse;
  onSubmit: (body: MeetingCreateRequest) => Promise<void>;
  onCancel: () => void;
}

function toLocalInputValue(iso: string | undefined): string {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function MeetingForm({ initial, onSubmit, onCancel }: MeetingFormProps) {
  const [title, setTitle] = useState(initial?.title ?? "");
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");
  const [meetingTimezone, setMeetingTimezone] = useState(initial?.timezone ?? "UTC");
  const [notes, setNotes] = useState(initial?.notes ?? "");
  const [contacts, setContacts] = useState<ContactResponse[]>([]);
  const [inviteeContactIds, setInviteeContactIds] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    listContacts({ size: 200 })
      .then((page) => setContacts(page.content))
      .catch(() => setContacts([]));
  }, []);

  function toggleInvitee(id: string) {
    setInviteeContactIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    );
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!startTime || !endTime) {
      setError("Start and end time are required");
      return;
    }
    setIsSubmitting(true);
    try {
      await onSubmit({
        title,
        startTime: new Date(startTime).toISOString(),
        endTime: new Date(endTime).toISOString(),
        meetingTimezone,
        notes,
        inviteeContactIds,
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save meeting");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <Field label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required />
      <Field
        label="Start time"
        type="datetime-local"
        value={startTime || toLocalInputValue(initial?.start)}
        onChange={(e) => setStartTime(e.target.value)}
        required
      />
      <Field
        label="End time"
        type="datetime-local"
        value={endTime || toLocalInputValue(initial?.end)}
        onChange={(e) => setEndTime(e.target.value)}
        required
      />
      <Field
        label="Timezone"
        value={meetingTimezone}
        onChange={(e) => setMeetingTimezone(e.target.value)}
        placeholder="e.g. America/Chicago"
      />
      <Field label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
      <div className="field">
        <label>Invitees</label>
        <div style={{ maxHeight: 160, overflowY: "auto", border: "1px solid var(--color-border)", borderRadius: 6, padding: "0.5rem" }}>
          {contacts.map((contact) => (
            <label key={contact.id} style={{ display: "block", fontWeight: 400 }}>
              <input
                type="checkbox"
                checked={inviteeContactIds.includes(contact.id)}
                onChange={() => toggleInvitee(contact.id)}
              />{" "}
              {contact.name} ({contact.email})
            </label>
          ))}
        </div>
      </div>
      {error && <p className="form-error">{error}</p>}
      <div style={{ display: "flex", gap: "0.5rem" }}>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Saving…" : "Save"}
        </Button>
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
