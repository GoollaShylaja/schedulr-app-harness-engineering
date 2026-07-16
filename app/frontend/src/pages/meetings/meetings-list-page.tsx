import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "../../components/ui/button";
import { Modal } from "../../components/ui/modal";
import { Pagination } from "../../components/ui/pagination";
import { SelectField } from "../../components/ui/select";
import { Field } from "../../components/ui/input";
import * as meetingsApi from "../../lib/api/meetings";
import { ApiError } from "../../lib/api-client";
import type { MeetingListParams, MeetingResponse } from "../../lib/types";
import { MeetingForm } from "./meeting-form";

const STATUS_OPTIONS = [
  { value: "", label: "All statuses" },
  { value: "scheduled", label: "Scheduled" },
  { value: "completed", label: "Completed" },
  { value: "cancelled", label: "Cancelled" },
];

export function MeetingsListPage() {
  const [meetings, setMeetings] = useState<MeetingResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [isCreating, setIsCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  async function load() {
    setIsLoading(true);
    setError(null);
    const params: MeetingListParams = { page, size: 20 };
    if (search) params.search = search;
    if (status) params.status = status;
    try {
      const result = await meetingsApi.listMeetings(params);
      setMeetings(result.content);
      setTotalPages(result.totalPages);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not load meetings");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, search, status]);

  async function handleCreate(body: Parameters<typeof meetingsApi.createMeeting>[0]) {
    await meetingsApi.createMeeting(body);
    setIsCreating(false);
    setPage(0);
    await load();
  }

  async function handleExport(format: "csv" | "pdf") {
    try {
      await meetingsApi.exportMeetings(format);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Export failed");
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1>Meetings</h1>
        <div style={{ display: "flex", gap: "0.5rem" }}>
          <Button variant="secondary" onClick={() => handleExport("csv")}>
            Export CSV
          </Button>
          <Button variant="secondary" onClick={() => handleExport("pdf")}>
            Export PDF
          </Button>
          <Button onClick={() => setIsCreating(true)}>New Meeting</Button>
        </div>
      </div>

      <div className="filters">
        <Field label="Search" value={search} onChange={(e) => setSearch(e.target.value)} />
        <SelectField
          label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          options={STATUS_OPTIONS}
        />
      </div>

      {error && <p className="form-error">{error}</p>}

      {isLoading ? (
        <div className="page-loading">Loading…</div>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Title</th>
              <th>Host</th>
              <th>Start</th>
              <th>End</th>
              <th>Status</th>
              <th>Invitees</th>
            </tr>
          </thead>
          <tbody>
            {meetings.map((meeting) => (
              <tr key={meeting.id}>
                <td>
                  <Link to={`/meetings/${meeting.id}`}>{meeting.title}</Link>
                </td>
                <td>{meeting.host}</td>
                <td>{meeting.start}</td>
                <td>{meeting.end}</td>
                <td>
                  <span className="badge">{meeting.status}</span>
                </td>
                <td>{meeting.inviteeCount}</td>
              </tr>
            ))}
            {meetings.length === 0 && (
              <tr>
                <td colSpan={6}>No meetings found.</td>
              </tr>
            )}
          </tbody>
        </table>
      )}

      <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />

      {isCreating && (
        <Modal title="New Meeting" onClose={() => setIsCreating(false)}>
          <MeetingForm onSubmit={handleCreate} onCancel={() => setIsCreating(false)} />
        </Modal>
      )}
    </div>
  );
}
