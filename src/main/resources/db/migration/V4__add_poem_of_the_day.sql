CREATE TABLE poem_of_the_day (
    assigned_on date PRIMARY KEY,
    poem_id uuid NOT NULL REFERENCES poem(id) ON DELETE CASCADE,
    cycle_number integer NOT NULL CHECK (cycle_number > 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX ux_poem_of_the_day_cycle_poem
    ON poem_of_the_day (cycle_number, poem_id);
