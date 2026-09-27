CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

CREATE TABLE user_preference (
    user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    preferred_travel_mode VARCHAR(16) NOT NULL DEFAULT 'WALK',
    route_preference VARCHAR(16) NOT NULL DEFAULT 'BALANCED',
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    pollution_sensitivity INTEGER NOT NULL DEFAULT 3,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_pollution_sensitivity CHECK (pollution_sensitivity BETWEEN 1 AND 5)
);

CREATE TABLE saved_place (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    address VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_saved_place_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_saved_place_longitude CHECK (longitude BETWEEN -180 AND 180)
);
CREATE INDEX ix_saved_place_user_created ON saved_place(user_id, created_at DESC);

CREATE TABLE saved_route (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    origin_name VARCHAR(200) NOT NULL,
    origin_latitude DOUBLE PRECISION NOT NULL,
    origin_longitude DOUBLE PRECISION NOT NULL,
    destination_name VARCHAR(200) NOT NULL,
    destination_latitude DOUBLE PRECISION NOT NULL,
    destination_longitude DOUBLE PRECISION NOT NULL,
    geometry_polyline TEXT,
    travel_mode VARCHAR(16) NOT NULL,
    route_preference VARCHAR(16) NOT NULL,
    pollution_score DOUBLE PRECISION,
    estimated_travel_time_seconds INTEGER,
    distance_meters DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_saved_route_user_created ON saved_route(user_id, created_at DESC);

CREATE TABLE route_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    origin_name VARCHAR(200) NOT NULL,
    destination_name VARCHAR(200) NOT NULL,
    travel_mode VARCHAR(16) NOT NULL,
    route_preference VARCHAR(16) NOT NULL,
    pollution_score DOUBLE PRECISION,
    estimated_travel_time_seconds INTEGER,
    distance_meters DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_route_history_user_created ON route_history(user_id, created_at DESC);
