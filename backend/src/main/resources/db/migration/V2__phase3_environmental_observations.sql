CREATE TABLE geographic_cell (
    cell_id VARCHAR(80) PRIMARY KEY,
    center_latitude DOUBLE PRECISION NOT NULL CHECK (center_latitude BETWEEN -90 AND 90),
    center_longitude DOUBLE PRECISION NOT NULL CHECK (center_longitude BETWEEN -180 AND 180),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pollution_observation (
    id UUID PRIMARY KEY,
    cell_id VARCHAR(80) NOT NULL REFERENCES geographic_cell(cell_id),
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aqi INTEGER CHECK (aqi BETWEEN 0 AND 500),
    pm25 DOUBLE PRECISION CHECK (pm25 >= 0),
    pm10 DOUBLE PRECISION CHECK (pm10 >= 0),
    no2 DOUBLE PRECISION CHECK (no2 >= 0),
    so2 DOUBLE PRECISION CHECK (so2 >= 0),
    co DOUBLE PRECISION CHECK (co >= 0),
    o3 DOUBLE PRECISION CHECK (o3 >= 0),
    provider VARCHAR(100) NOT NULL,
    generated BOOLEAN NOT NULL,
    CONSTRAINT uq_pollution_provider_cell_time UNIQUE (provider, cell_id, observed_at)
);
CREATE INDEX ix_pollution_cell_time ON pollution_observation(cell_id, observed_at DESC);

CREATE TABLE weather_observation (
    id UUID PRIMARY KEY,
    cell_id VARCHAR(80) NOT NULL REFERENCES geographic_cell(cell_id),
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    temperature_c DOUBLE PRECISION,
    humidity_percent DOUBLE PRECISION CHECK (humidity_percent BETWEEN 0 AND 100),
    wind_speed_mps DOUBLE PRECISION CHECK (wind_speed_mps >= 0),
    wind_direction_degrees DOUBLE PRECISION CHECK (wind_direction_degrees BETWEEN 0 AND 360),
    precipitation_mm DOUBLE PRECISION CHECK (precipitation_mm >= 0),
    weather_condition VARCHAR(40),
    provider VARCHAR(100) NOT NULL,
    generated BOOLEAN NOT NULL,
    CONSTRAINT uq_weather_provider_cell_time UNIQUE (provider, cell_id, observed_at)
);
CREATE INDEX ix_weather_cell_time ON weather_observation(cell_id, observed_at DESC);

CREATE TABLE traffic_observation (
    id UUID PRIMARY KEY,
    cell_id VARCHAR(80) NOT NULL REFERENCES geographic_cell(cell_id),
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    traffic_level VARCHAR(20),
    congestion_factor DOUBLE PRECISION CHECK (congestion_factor BETWEEN 1 AND 5),
    average_speed_kph DOUBLE PRECISION CHECK (average_speed_kph >= 0),
    provider VARCHAR(100) NOT NULL,
    generated BOOLEAN NOT NULL,
    CONSTRAINT uq_traffic_provider_cell_time UNIQUE (provider, cell_id, observed_at)
);
CREATE INDEX ix_traffic_cell_time ON traffic_observation(cell_id, observed_at DESC);
