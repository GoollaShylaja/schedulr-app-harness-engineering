import { request, requestBlob } from "../api-client";
import type {
  MeetingCreateRequest,
  MeetingListParams,
  MeetingResponse,
  MeetingUpdateRequest,
  PageResponse,
  RsvpUpdateRequest,
} from "../types";

export function listMeetings(params: MeetingListParams = {}): Promise<PageResponse<MeetingResponse>> {
  return request<PageResponse<MeetingResponse>>("/meetings", { query: params });
}

export function getMeeting(id: string): Promise<MeetingResponse> {
  return request<MeetingResponse>(`/meetings/${id}`);
}

export function createMeeting(body: MeetingCreateRequest): Promise<MeetingResponse> {
  return request<MeetingResponse>("/meetings", { method: "POST", body });
}

export function updateMeeting(id: string, body: MeetingUpdateRequest): Promise<MeetingResponse> {
  return request<MeetingResponse>(`/meetings/${id}`, { method: "PATCH", body });
}

export function deleteMeeting(id: string): Promise<void> {
  return request<void>(`/meetings/${id}`, { method: "DELETE" });
}

export function updateRsvp(
  meetingId: string,
  inviteeId: string,
  body: RsvpUpdateRequest,
): Promise<MeetingResponse> {
  return request<MeetingResponse>(`/meetings/${meetingId}/invitees/${inviteeId}/rsvp`, {
    method: "PATCH",
    body,
  });
}

export async function exportMeetings(format: "csv" | "pdf"): Promise<void> {
  const { blob, filename } = await requestBlob("/meetings/export", { format });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
