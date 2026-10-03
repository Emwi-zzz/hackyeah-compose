CREATE TABLE malls (
    id        BIGINT PRIMARY KEY,
    name      TEXT NOT NULL,
    size_x    INT NOT NULL,
    size_y    INT NOT NULL,
    ul_lat    DOUBLE PRECISION NOT NULL,
    ul_lon    DOUBLE PRECISION NOT NULL,
    dr_lat    DOUBLE PRECISION NOT NULL,
    dr_lon    DOUBLE PRECISION NOT NULL,
    min_floor INT NOT NULL
);

CREATE TABLE mall_entry_points (
    mall_id BIGINT NOT NULL REFERENCES malls (id) ON DELETE CASCADE,
    idx     INT NOT NULL,
    x       DOUBLE PRECISION NOT NULL,
    y       DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (mall_id, idx)
);

-- outline is an SVG path (M/L/Q/C/Z) in the mall's local coordinates
CREATE TABLE floors (
    mall_id BIGINT NOT NULL REFERENCES malls (id) ON DELETE CASCADE,
    number  INT NOT NULL,
    outline TEXT NOT NULL,
    PRIMARY KEY (mall_id, number)
);

CREATE TABLE stores (
    instance_id  BIGINT PRIMARY KEY,
    mall_id      BIGINT NOT NULL,
    floor_number INT NOT NULL,
    shop_id      BIGINT NOT NULL,
    name         TEXT NOT NULL,
    category     TEXT NOT NULL DEFAULT 'Retail',
    description  TEXT,
    area         TEXT NOT NULL,
    FOREIGN KEY (mall_id, floor_number) REFERENCES floors (mall_id, number) ON DELETE CASCADE
);
CREATE INDEX stores_floor_idx ON stores (mall_id, floor_number);

CREATE TABLE store_entry_points (
    store_id BIGINT NOT NULL REFERENCES stores (instance_id) ON DELETE CASCADE,
    idx      INT NOT NULL,
    x        DOUBLE PRECISION NOT NULL,
    y        DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (store_id, idx)
);

CREATE TABLE elevators (
    mall_id      BIGINT NOT NULL,
    floor_number INT NOT NULL,
    id           BIGINT NOT NULL,
    x            DOUBLE PRECISION NOT NULL,
    y            DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (mall_id, floor_number, id),
    FOREIGN KEY (mall_id, floor_number) REFERENCES floors (mall_id, number) ON DELETE CASCADE
);

CREATE TABLE escalators (
    mall_id      BIGINT NOT NULL,
    floor_number INT NOT NULL,
    id           BIGINT NOT NULL,
    x            DOUBLE PRECISION NOT NULL,
    y            DOUBLE PRECISION NOT NULL,
    direction    TEXT NOT NULL CHECK (direction IN ('UP', 'DOWN')),
    PRIMARY KEY (mall_id, floor_number, id),
    FOREIGN KEY (mall_id, floor_number) REFERENCES floors (mall_id, number) ON DELETE CASCADE
);
