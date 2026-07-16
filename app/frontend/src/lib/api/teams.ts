import { request } from "../api-client";
import type {
  InviteMemberRequest,
  TeamMemberResponse,
  TeamResponse,
  UpdateMemberRoleRequest,
} from "../types";

export function getMyTeam(): Promise<TeamResponse> {
  return request<TeamResponse>("/teams/me");
}

export function inviteMember(body: InviteMemberRequest): Promise<TeamMemberResponse> {
  return request<TeamMemberResponse>("/teams/me/members", { method: "POST", body });
}

export function updateMemberRole(
  userId: string,
  body: UpdateMemberRoleRequest,
): Promise<TeamMemberResponse> {
  return request<TeamMemberResponse>(`/teams/me/members/${userId}/role`, {
    method: "PATCH",
    body,
  });
}

export function removeMember(userId: string): Promise<void> {
  return request<void>(`/teams/me/members/${userId}`, { method: "DELETE" });
}
