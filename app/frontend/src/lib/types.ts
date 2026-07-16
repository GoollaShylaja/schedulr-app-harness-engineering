export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---- auth ----

export interface UserResponse {
  id: string;
  email: string;
  fullName: string;
  timezone: string;
  role: string;
  teamId: string;
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  user: UserResponse;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface ProfileUpdateRequest {
  fullName?: string;
  timezone?: string;
}

export interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
}

// ---- meetings ----

export interface InviteeResponse {
  id: string;
  contactId: string;
  contactName: string;
  contactEmail: string;
  response: string;
}

export interface MeetingResponse {
  id: string;
  title: string;
  host: string;
  hostId: string;
  start: string;
  end: string;
  timezone: string;
  status: string;
  notes: string | null;
  inviteeCount: number;
  invitees: InviteeResponse[];
}

export interface MeetingCreateRequest {
  title: string;
  startTime: string;
  endTime: string;
  meetingTimezone?: string;
  notes?: string;
  inviteeContactIds?: string[];
}

export interface MeetingUpdateRequest {
  title?: string;
  notes?: string;
  startTime?: string;
  endTime?: string;
  meetingTimezone?: string;
  status?: string;
}

export interface RsvpUpdateRequest {
  response: string;
}

export type MeetingListParams = {
  hostId?: string;
  startAfter?: string;
  startBefore?: string;
  contactId?: string;
  status?: string;
  search?: string;
  page?: number;
  size?: number;
};

// ---- contacts ----

export interface ContactResponse {
  id: string;
  name: string;
  email: string;
  company: string | null;
  phone: string | null;
  title: string | null;
  notes: string | null;
  stage: string;
  createdAt: string;
}

export interface ContactCreateRequest {
  name: string;
  email: string;
  company?: string;
  phone?: string;
  title?: string;
  notes?: string;
  stage?: string;
}

export interface ContactUpdateRequest {
  name?: string;
  email?: string;
  company?: string;
  phone?: string;
  title?: string;
  notes?: string;
  stage?: string;
}

export type ContactListParams = {
  page?: number;
  size?: number;
};

// ---- teams ----

export interface TeamMemberResponse {
  id: string;
  name: string;
  email: string;
  timezone: string;
  role: string;
}

export interface TeamResponse {
  id: string;
  name: string;
  slug: string;
  members: TeamMemberResponse[];
}

export interface InviteMemberRequest {
  email: string;
  fullName: string;
  role?: string;
  timezone?: string;
}

export interface UpdateMemberRoleRequest {
  role: string;
}

// ---- availability ----

export interface AvailabilitySlotResponse {
  id: string;
  userId: string;
  weekday: number;
  start: string;
  end: string;
}

export interface AvailabilitySlotCreateRequest {
  weekday: number;
  start: string;
  end: string;
}

export interface AvailabilityBulkSetRequest {
  slots: AvailabilitySlotCreateRequest[];
}
