CREATE TABLE pollution_forecast (
    cell_id VARCHAR(80) NOT NULL REFERENCES geographic_cell(cell_id),
    target_at TIMESTAMP WITH TIME ZONE NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    aqi INTEGER CHECK (aqi BETWEEN 0 AND 500),
    pm25 DOUBLE PRECISION CHECK (pm25 >= 0),
    pm10 DOUBLE PRECISION CHECK (pm10 >= 0),
    no2 DOUBLE PRECISION CHECK (no2 >= 0),
    so2 DOUBLE PRECISION CHECK (so2 >= 0),
    co DOUBLE PRECISION CHECK (co >= 0),
    o3 DOUBLE PRECISION CHECK (o3 >= 0),
    quality_score INTEGER NOT NULL CHECK (quality_score BETWEEN 0 AND 100),
    quality VARCHAR(12) NOT NULL CHECK (quality IN ('LOW', 'MEDIUM', 'HIGH')),
    sample_count INTEGER NOT NULL CHECK (sample_count > 0),
    provider VARCHAR(100) NOT NULL,
    model_version VARCHAR(80) NOT NULL,
    source_generated BOOLEAN NOT NULL,
    PRIMARY KEY (cell_id, target_at, model_version)
);
CREATE INDEX ix_pollution_forecast_cell_target ON pollution_forecast(cell_id, target_at);

CREATE TABLE route_calculation (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    origin_latitude DOUBLE PRECISION NOT NULL CHECK (origin_latitude BETWEEN -90 AND 90),
    origin_longitude DOUBLE PRECISION NOT NULL CHECK (origin_longitude BETWEEN -180 AND 180),
    destination_latitude DOUBLE PRECISION NOT NULL CHECK (destination_latitude BETWEEN -90 AND 90),
    destination_longitude DOUBLE PRECISION NOT NULL CHECK (destination_longitude BETWEEN -180 AND 180),
    travel_mode VARCHAR(16) NOT NULL,
    preference VARCHAR(16) NOT NULL CHECK (preference IN ('FASTEST', 'CLEANEST', 'BALANCED')),
    result_payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_route_calculation_user_created ON route_calculation(user_id, created_at DESC);
