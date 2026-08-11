import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Button } from "../../components/ui/button";
import { Field } from "../../components/ui/input";
import { SelectField } from "../../components/ui/select";
import * as availabilityApi from "../../lib/api/availability";
import * as contactsApi from "../../lib/api/contacts";
import * as meetingsApi from "../../lib/api/meetings";
import { ApiError } from "../../lib/api-client";
import { useAuth } from "../../lib/auth-context";
import type { AvailabilitySlotResponse, ContactResponse } from "../../lib/types";

const DURATION_OPTIONS = [15, 30, 45, 60];
const STEP_MINUTES = 15;

function todayLocalDate(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

function weekdayOf(dateStr: string): number {
  const [year, month, day] = dateStr.split("-").map(Number);
  return new Date(year, month - 1, day).getDay();
}

function toMinutes(hhmm: string): number {
  const [h, m] = hhmm.split(":").map(Number);
  return h * 60 + m;
}

function toHHMM(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

export function SchedulePage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [contacts, setContacts] = useState<ContactResponse[]>([]);
  const [slots, setSlots] = useState<AvailabilitySlotResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [contactId, setContactId] = useState(searchParams.get("contactId") ?? "");
  const [date, setDate] = useState(todayLocalDate());
  const [selectedSlotId, setSelectedSlotId] = useState("");
  const [startTime, setStartTime] = useState("");
  const [duration, setDuration] = useState(30);
  const [title, setTitle] = useState("");
  const [notes, setNotes] = useState("");

  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    Promise.all([contactsApi.listContacts({ size: 200 }), availabilityApi.listMyAvailability()])
      .then(([contactPage, availabilitySlots]) => {
        setContacts(contactPage.content);
        setSlots(availabilitySlots);
      })
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : "Could not load scheduling data"))
      .finally(() => setIsLoading(false));
  }, []);

  const daySlots = useMemo(() => {
    const weekday = weekdayOf(date);
    return slots.filter((slot) => slot.weekday === weekday);
  }, [slots, date]);

  const selectedSlot = daySlots.find((slot) => slot.id === selectedSlotId) ?? null;

  const startOptions = useMemo(() => {
    if (!selectedSlot) return [];
    const slotStart = toMinutes(selectedSlot.start);
    const slotEnd = toMinutes(selectedSlot.end);
    const options: string[] = [];
    for (let t = slotStart; t + duration <= slotEnd; t += STEP_MINUTES) {
      options.push(toHHMM(t));
    }
    return options;
  }, [selectedSlot, duration]);

  useEffect(() => {
    if (!startOptions.includes(startTime)) {
      setStartTime(startOptions[0] ?? "");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [startOptions]);

  useEffect(() => {
    setSelectedSlotId(daySlots[0]?.id ?? "");
  }, [daySlots]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (!contactId) {
      setError("Choose a contact to invite");
      return;
    }
    if (!selectedSlot || !startTime) {
      setError("Choose an available time slot");
      return;
    }

    const endTime = toHHMM(toMinutes(startTime) + duration);
    const startIso = new Date(`${date}T${startTime}:00`).toISOString();
    const endIso = new Date(`${date}T${endTime}:00`).toISOString();

    setIsSubmitting(true);
    try {
      const meeting = await meetingsApi.createMeeting({
        title: title || "Meeting",
        startTime: startIso,
        endTime: endIso,
        meetingTimezone: user?.timezone,
        notes,
        inviteeContactIds: [contactId],
      });
      navigate(`/meetings/${meeting.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not schedule meeting");
    } finally {
      setIsSubmitting(false);
    }
  }

  if (isLoading) {
    return (
      <div className="page">
        <div className="page-loading">Loading…</div>
      </div>
    );
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1>Schedule a meeting</h1>
      </div>

      {loadError && <p className="form-error">{loadError}</p>}

      <form onSubmit={handleSubmit}>
        <SelectField
          label="Contact"
          value={contactId}
          onChange={(e) => setContactId(e.target.value)}
          options={[
            { value: "", label: "Select a contact…" },
            ...contacts.map((contact) => ({
              value: contact.id,
              label: `${contact.name} (${contact.email})`,
            })),
          ]}
          required
        />

        <Field
          label="Date"
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          required
        />

        <div className="field">
          <label>Available slots</label>
          {daySlots.length === 0 ? (
            <p>No availability set for this day. Add slots on the Availability page.</p>
          ) : (
            <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
              {daySlots.map((slot) => (
                <button
                  type="button"
                  key={slot.id}
                  className={`btn ${slot.id === selectedSlotId ? "btn-primary" : "btn-secondary"}`}
                  onClick={() => setSelectedSlotId(slot.id)}
                >
                  {slot.start}–{slot.end}
                </button>
              ))}
            </div>
          )}
        </div>

        {selectedSlot && (
          <>
            <SelectField
              label="Start time"
              value={startTime}
              onChange={(e) => setStartTime(e.target.value)}
              options={startOptions.map((time) => ({ value: time, label: time }))}
              disabled={startOptions.length === 0}
            />
            {startOptions.length === 0 && (
              <p className="form-error">No slot long enough for a {duration}-minute meeting.</p>
            )}
            <SelectField
              label="Duration"
              value={String(duration)}
              onChange={(e) => setDuration(Number(e.target.value))}
              options={DURATION_OPTIONS.map((minutes) => ({
                value: String(minutes),
                label: `${minutes} minutes`,
              }))}
            />
          </>
        )}

        <Field
          label="Title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="Discovery call"
        />
        <Field label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} />

        {error && <p className="form-error">{error}</p>}

        <Button type="submit" disabled={isSubmitting || !selectedSlot}>
          {isSubmitting ? "Scheduling…" : "Schedule meeting"}
        </Button>
      </form>
    </div>
  );
}
