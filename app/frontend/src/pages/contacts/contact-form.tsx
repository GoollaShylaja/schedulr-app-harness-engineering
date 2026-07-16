import { useState, type FormEvent } from "react";
import { Button } from "../../components/ui/button";
import { Field } from "../../components/ui/input";
import { SelectField } from "../../components/ui/select";
import { ApiError } from "../../lib/api-client";
import type { ContactCreateRequest, ContactResponse } from "../../lib/types";

const STAGE_OPTIONS = [
  { value: "lead", label: "Lead" },
  { value: "qualified", label: "Qualified" },
  { value: "customer", label: "Customer" },
  { value: "churned", label: "Churned" },
];

interface ContactFormProps {
  initial?: ContactResponse;
  onSubmit: (body: ContactCreateRequest) => Promise<void>;
  onCancel: () => void;
}

export function ContactForm({ initial, onSubmit, onCancel }: ContactFormProps) {
  const [name, setName] = useState(initial?.name ?? "");
  const [email, setEmail] = useState(initial?.email ?? "");
  const [company, setCompany] = useState(initial?.company ?? "");
  const [phone, setPhone] = useState(initial?.phone ?? "");
  const [title, setTitle] = useState(initial?.title ?? "");
  const [notes, setNotes] = useState(initial?.notes ?? "");
  const [stage, setStage] = useState(initial?.stage ?? "lead");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      await onSubmit({ name, email, company, phone, title, notes, stage });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save contact");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <Field label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
      <Field
        label="Email"
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        required
      />
      <Field label="Company" value={company} onChange={(e) => setCompany(e.target.value)} />
      <Field label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} />
      <Field label="Title" value={title} onChange={(e) => setTitle(e.target.value)} />
      <Field label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
      <SelectField
        label="Stage"
        value={stage}
        onChange={(e) => setStage(e.target.value)}
        options={STAGE_OPTIONS}
      />
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
