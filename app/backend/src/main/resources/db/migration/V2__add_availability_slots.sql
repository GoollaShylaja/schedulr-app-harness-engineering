-- Phase 2: availability_slots. UUID PK assigned by the application (see V1 note).

CREATE TABLE availability_slots (
    id      UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    weekday INTEGER NOT NULL,
    start   VARCHAR(5) NOT NULL,
    "end"   VARCHAR(5) NOT NULL
);

CREATE INDEX ix_availability_slots_user_id ON availability_slots (user_id);
