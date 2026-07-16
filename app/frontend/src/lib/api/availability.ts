import { request } from "../api-client";
import type {
  AvailabilityBulkSetRequest,
  AvailabilitySlotCreateRequest,
  AvailabilitySlotResponse,
} from "../types";

export function listMyAvailability(): Promise<AvailabilitySlotResponse[]> {
  return request<AvailabilitySlotResponse[]>("/availability");
}

export function listUserAvailability(userId: string): Promise<AvailabilitySlotResponse[]> {
  return request<AvailabilitySlotResponse[]>(`/availability/user/${userId}`);
}

export function addSlot(body: AvailabilitySlotCreateRequest): Promise<AvailabilitySlotResponse> {
  return request<AvailabilitySlotResponse>("/availability", { method: "POST", body });
}

export function setAvailability(
  body: AvailabilityBulkSetRequest,
): Promise<AvailabilitySlotResponse[]> {
  return request<AvailabilitySlotResponse[]>("/availability", { method: "PUT", body });
}

export function deleteSlot(slotId: string): Promise<void> {
  return request<void>(`/availability/${slotId}`, { method: "DELETE" });
}
