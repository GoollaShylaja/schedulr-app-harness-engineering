package com.schedulr.teams.dto;

import java.util.List;
import java.util.UUID;

public record TeamResponse(UUID id, String name, String slug, List<TeamMemberResponse> members) {}
