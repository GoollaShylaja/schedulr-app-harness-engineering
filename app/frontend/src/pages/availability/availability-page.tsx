import { Fragment, useEffect, useState, type FormEvent } from "react";
import { Button } from "../../components/ui/button";
import { Field } from "../../components/ui/input";
import * as availabilityApi from "../../lib/api/availability";
import { ApiError } from "../../lib/api-client";
import type { AvailabilitySlotResponse } from "../../lib/types";

const WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
const TIME_PATTERN = /^\d{2}:\d{2}$/;

export function AvailabilityPage() {
  const [slots, setSlots] = useState<AvailabilitySlotResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [draft, setDraft] = useState<{ weekday: number; start: string; end: string }>({
    weekday: 1,
    start: "09:00",
    end: "17:00",
  });

  async function load() {
    setIsLoading(true);
    setError(null);
    try {
      setSlots(await availabilityApi.listMyAvailability());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not load availability");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function handleAdd(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!TIME_PATTERN.test(draft.start) || !TIME_PATTERN.test(draft.end)) {
      setError("Time must be in HH:mm format");
      return;
    }
    try {
      await availabilityApi.addSlot(draft);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not add slot");
    }
  }

  async function handleDelete(slotId: string) {
    setError(null);
    try {
      await availabilityApi.deleteSlot(slotId);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete slot");
    }
  }

  async function handleClearAll() {
    setError(null);
    try {
      await availabilityApi.setAvailability({ slots: [] });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not clear availability");
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1>Availability</h1>
        <Button variant="secondary" onClick={handleClearAll}>
          Clear all
        </Button>
      </div>

      {error && <p className="form-error">{error}</p>}

      {isLoading ? (
        <div className="page-loading">Loading…</div>
      ) : (
        <div className="availability-grid">
          {WEEKDAYS.map((label, weekday) => (
            <Fragment key={weekday}>
              <div className="availability-day">{label}</div>
              <div style={{ gridColumn: "span 3", display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
                {slots
                  .filter((slot) => slot.weekday === weekday)
                  .map((slot) => (
                    <span key={slot.id} className="badge">
                      {slot.start}–{slot.end}{" "}
                      <button
                        onClick={() => handleDelete(slot.id)}
                        aria-label="Remove slot"
                        style={{ border: "none", background: "none", cursor: "pointer" }}
                      >
                        ×
                      </button>
                    </span>
                  ))}
              </div>
            </Fragment>
          ))}
        </div>
      )}

      <h2 style={{ marginTop: "2rem" }}>Add a slot</h2>
      <form onSubmit={handleAdd} style={{ display: "flex", gap: "1rem", alignItems: "flex-end" }}>
        <div className="field">
          <label htmlFor="weekday">Day</label>
          <select
            id="weekday"
            className="input"
            value={draft.weekday}
            onChange={(e) => setDraft({ ...draft, weekday: Number(e.target.value) })}
          >
            {WEEKDAYS.map((label, value) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <Field
          label="Start"
          value={draft.start}
          onChange={(e) => setDraft({ ...draft, start: e.target.value })}
          placeholder="09:00"
        />
        <Field
          label="End"
          value={draft.end}
          onChange={(e) => setDraft({ ...draft, end: e.target.value })}
          placeholder="17:00"
        />
        <Button type="submit">Add</Button>
      </form>
    </div>
  );
}
