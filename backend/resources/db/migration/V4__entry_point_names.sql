-- Entrance names move from code to data
ALTER TABLE mall_entry_points ADD COLUMN name TEXT;

UPDATE mall_entry_points SET name = CASE
    WHEN mall_id = 1 AND idx = 0 THEN 'Main Entrance (Kraków Główny / Pawia)'
    WHEN mall_id = 1 AND idx = 1 THEN 'East Exit (Dworzec Autobusowy MDA)'
    WHEN mall_id = 1 AND idx = 2 THEN 'South Exit (Plac Jana Nowaka-Jeziorańskiego)'
    WHEN mall_id = 2 AND idx = 0 THEN 'Podgórska Street Entrance (Wisła River)'
    WHEN mall_id = 2 AND idx = 1 THEN 'Rzeźnicza Street Entrance'
    WHEN mall_id = 2 AND idx = 2 THEN 'Daszyńskiego Entrance'
    ELSE 'Entrance / Exit #' || (idx + 1)
END;

ALTER TABLE mall_entry_points ALTER COLUMN name SET NOT NULL;
