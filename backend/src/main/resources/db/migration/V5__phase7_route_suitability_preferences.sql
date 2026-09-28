ALTER TABLE route_calculation
    DROP CONSTRAINT IF EXISTS route_calculation_preference_check;

-- H2 assigns this deterministic generated name when it runs the unchanged V4 migration.
ALTER TABLE route_calculation
    DROP CONSTRAINT IF EXISTS CONSTRAINT_D1E79593;

ALTER TABLE route_calculation
    ADD CONSTRAINT ck_route_calculation_preference
        CHECK (preference IN ('FASTEST', 'CLEANEST', 'BALANCED', 'JOGGER', 'CYCLIST'));
