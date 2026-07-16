import { request } from "../api-client";
import type {
  ContactCreateRequest,
  ContactListParams,
  ContactResponse,
  ContactUpdateRequest,
  PageResponse,
} from "../types";

export function listContacts(params: ContactListParams = {}): Promise<PageResponse<ContactResponse>> {
  return request<PageResponse<ContactResponse>>("/contacts", { query: params });
}

export function getContact(id: string): Promise<ContactResponse> {
  return request<ContactResponse>(`/contacts/${id}`);
}

export function createContact(body: ContactCreateRequest): Promise<ContactResponse> {
  return request<ContactResponse>("/contacts", { method: "POST", body });
}

export function updateContact(id: string, body: ContactUpdateRequest): Promise<ContactResponse> {
  return request<ContactResponse>(`/contacts/${id}`, { method: "PATCH", body });
}

export function deleteContact(id: string): Promise<void> {
  return request<void>(`/contacts/${id}`, { method: "DELETE" });
}
