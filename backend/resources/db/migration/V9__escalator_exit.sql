ALTER TABLE escalators
    ADD COLUMN exit_x DOUBLE PRECISION,
    ADD COLUMN exit_y DOUBLE PRECISION;

-- Legacy data: land on the nearest escalator of the target floor, otherwise on the same spot.
UPDATE escalators e SET
    exit_x = COALESCE((SELECT o.x FROM escalators o
                       WHERE o.mall_id = e.mall_id
                         AND o.floor_number = e.floor_number + CASE e.direction WHEN 'UP' THEN 1 ELSE -1 END
                       ORDER BY (o.x - e.x) * (o.x - e.x) + (o.y - e.y) * (o.y - e.y) LIMIT 1), e.x),
    exit_y = COALESCE((SELECT o.y FROM escalators o
                       WHERE o.mall_id = e.mall_id
                         AND o.floor_number = e.floor_number + CASE e.direction WHEN 'UP' THEN 1 ELSE -1 END
                       ORDER BY (o.x - e.x) * (o.x - e.x) + (o.y - e.y) * (o.y - e.y) LIMIT 1), e.y);

ALTER TABLE escalators
    ALTER COLUMN exit_x SET NOT NULL,
    ALTER COLUMN exit_y SET NOT NULL;
