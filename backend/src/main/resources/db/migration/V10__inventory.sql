-- Phase 09: inventory. See Docs/Schema.md §5 and ADR-026.
-- Consumables the lab runs on (tubes, cups, reagents…) with a level and a low-stock threshold.
-- The level only ever changes through a recorded movement, so "why is it at 12?" always has an answer.

CREATE TABLE inventory_items (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                varchar(120) NOT NULL,
    category            varchar(16)  NOT NULL CHECK (category IN ('TUBE', 'REAGENT', 'CONSUMABLE', 'OTHER')),
    unit                varchar(30)  NOT NULL,
    current_stock       integer      NOT NULL DEFAULT 0 CHECK (current_stock >= 0),
    low_stock_threshold integer      NOT NULL DEFAULT 0 CHECK (low_stock_threshold >= 0),
    is_active           boolean      NOT NULL DEFAULT true,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX inventory_items_name_uq ON inventory_items (lower(name));
CREATE INDEX inventory_items_low_idx ON inventory_items (category, name) WHERE is_active AND current_stock < low_stock_threshold;

-- Every change to a level: what changed, why, who did it, and the level afterwards. Append-only.
CREATE TABLE inventory_movements (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id        uuid         NOT NULL REFERENCES inventory_items (id),
    delta          integer      NOT NULL CHECK (delta <> 0),
    stock_after    integer      NOT NULL CHECK (stock_after >= 0),
    reason         varchar(12)  NOT NULL CHECK (reason IN ('OPENING', 'RESTOCK', 'USED', 'WASTAGE', 'CORRECTION')),
    note           varchar(300),
    actor_user_id  uuid         NOT NULL REFERENCES users (id),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    -- Restocking adds, using or wasting removes; an opening count or a correction can go either way.
    CHECK ((reason = 'RESTOCK' AND delta > 0) OR (reason IN ('USED', 'WASTAGE') AND delta < 0)
           OR reason IN ('OPENING', 'CORRECTION'))
);

CREATE INDEX inventory_movements_item_idx ON inventory_movements (item_id, created_at DESC);

-- forbid_modification() comes from V2.
CREATE TRIGGER inventory_movements_append_only
    BEFORE UPDATE OR DELETE ON inventory_movements
    FOR EACH ROW EXECUTE FUNCTION forbid_modification();

-- Items are retired, never deleted, so the movement history always has something to point at.
CREATE TRIGGER inventory_items_no_delete
    BEFORE DELETE ON inventory_items
    FOR EACH ROW EXECUTE FUNCTION forbid_delete();
