ALTER TABLE elevators
    ADD COLUMN is_accessible BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE escalators
    ADD COLUMN is_accessible BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE floor_voids (
    mall_id      BIGINT NOT NULL,
    floor_number INT NOT NULL,
    idx          INT NOT NULL,
    outline      TEXT NOT NULL,
    PRIMARY KEY (mall_id, floor_number, idx),
    FOREIGN KEY (mall_id, floor_number) REFERENCES floors (mall_id, number) ON DELETE CASCADE
);
