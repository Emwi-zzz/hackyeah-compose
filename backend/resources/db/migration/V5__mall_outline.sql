-- Building footprint, separate from per-floor shapes. Backfilled from the lowest floor (what the map used so far).
ALTER TABLE malls ADD COLUMN outline TEXT;

UPDATE malls m SET outline = (
    SELECT f.outline FROM floors f WHERE f.mall_id = m.id ORDER BY f.number LIMIT 1
);
