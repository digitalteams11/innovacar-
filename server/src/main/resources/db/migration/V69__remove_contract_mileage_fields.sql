-- Business decision: the Moroccan rental contract (and its PDF) no longer
-- requires or displays vehicle mileage. This removes mileage entirely from
-- the contract module — not just from the UI/PDF, the columns themselves.
--
-- Explicitly NOT touched by this migration: vehicles.mileage_current and any
-- maintenance/service-record mileage columns — fleet mileage tracking is a
-- separate concern from the rental contract and remains fully intact. The
-- return-inspection flow still updates vehicles.mileage_current directly;
-- it simply no longer round-trips that value through the contract itself.
ALTER TABLE contracts
    DROP COLUMN IF EXISTS mileage_start,
    DROP COLUMN IF EXISTS mileage_end,
    DROP COLUMN IF EXISTS allowed_mileage,
    DROP COLUMN IF EXISTS extra_mileage_cost;
