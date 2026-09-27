CREATE TABLE provider_freshness (
    provider_id VARCHAR(100) NOT NULL,
    cell_id VARCHAR(80) NOT NULL REFERENCES geographic_cell(cell_id),
    last_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_success_at TIMESTAMP WITH TIME ZONE,
    last_failure_at TIMESTAMP WITH TIME ZONE,
    last_failure_type VARCHAR(32),
    PRIMARY KEY (provider_id, cell_id)
);
CREATE INDEX ix_provider_freshness_cell ON provider_freshness(cell_id, provider_id);

ALTER TABLE saved_route ADD CONSTRAINT ck_saved_route_origin_latitude CHECK (origin_latitude BETWEEN -90 AND 90);
ALTER TABLE saved_route ADD CONSTRAINT ck_saved_route_origin_longitude CHECK (origin_longitude BETWEEN -180 AND 180);
ALTER TABLE saved_route ADD CONSTRAINT ck_saved_route_destination_latitude CHECK (destination_latitude BETWEEN -90 AND 90);
ALTER TABLE saved_route ADD CONSTRAINT ck_saved_route_destination_longitude CHECK (destination_longitude BETWEEN -180 AND 180);
