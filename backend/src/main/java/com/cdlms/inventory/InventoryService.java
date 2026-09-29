package com.cdlms.inventory;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.ApiException;
import com.cdlms.inventory.InventoryDtos.AdjustRequest;
import com.cdlms.inventory.InventoryDtos.Alerts;
import com.cdlms.inventory.InventoryDtos.CreateItemRequest;
import com.cdlms.inventory.InventoryDtos.ItemView;
import com.cdlms.inventory.InventoryDtos.MovementView;
import com.cdlms.inventory.InventoryDtos.Reason;
import com.cdlms.inventory.InventoryDtos.UpdateItemRequest;
import com.cdlms.sample.SampleQueries;
import com.cdlms.sample.SampleQueries.UserRef;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The lab's consumables (Docs/Rules.md §4, ADR-026).
 *
 * <p>An item is low when its level is below its threshold. Levels change only through {@link #adjust}:
 * one conditional UPDATE that refuses to go below zero (so two people adjusting at once can't push it
 * negative or lose a change), plus an append-only movement saying what changed, why and who did it.
 * Lab technicians and admins adjust; only admins add, edit or retire items.
 */
@Service
public class InventoryService {

    static final int MOVEMENT_LIMIT = 30;
    static final int ALERT_ITEMS = 6;

    private final InventoryItemRepository items;
    private final NamedParameterJdbcTemplate jdbc;
    private final SampleQueries users;

    public InventoryService(InventoryItemRepository items, NamedParameterJdbcTemplate jdbc, SampleQueries users) {
        this.items = items;
        this.jdbc = jdbc;
        this.users = users;
    }

    // ---------------------------------------------------------------- reads

    /** Items with the ones needing attention first (out of stock, then low), then by name. Retired ones only on request. */
    @Transactional(readOnly = true)
    public List<ItemView> list(String query, InventoryItem.Category category, boolean lowOnly, boolean includeInactive) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return items.findAllByOrderByName().stream()
                .filter(i -> includeInactive || i.isActive())
                .filter(i -> category == null || i.getCategory() == category)
                .filter(i -> q.isEmpty() || i.getName().toLowerCase().contains(q))
                .filter(i -> !lowOnly || i.isLow())
                .sorted(Comparator.comparing((InventoryItem i) -> i.isOut() ? 0 : i.isLow() ? 1 : 2)
                        .thenComparing(InventoryItem::getName, String.CASE_INSENSITIVE_ORDER))
                .map(InventoryService::view).toList();
    }

    @Transactional(readOnly = true)
    public Alerts alerts() {
        List<InventoryItem> low = items.findAllByOrderByName().stream().filter(InventoryItem::isLow)
                .sorted(Comparator.comparingDouble(InventoryService::fill)).toList();
        return new Alerts(low.size(), low.stream().filter(InventoryItem::isOut).count(),
                low.stream().limit(ALERT_ITEMS).map(InventoryService::view).toList());
    }

    /** The item's recent movements, newest first, with who made each. */
    @Transactional(readOnly = true)
    public List<MovementView> movements(UUID itemId) {
        find(itemId);
        record Row(UUID id, int delta, int after, Reason reason, String note, UUID actor, java.time.Instant at) {
        }
        List<Row> rows = jdbc.query("""
                SELECT id, delta, stock_after, reason, note, actor_user_id, created_at
                FROM inventory_movements WHERE item_id = :id ORDER BY created_at DESC, id LIMIT :limit
                """, new MapSqlParameterSource("id", itemId).addValue("limit", MOVEMENT_LIMIT),
                (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getInt("delta"), rs.getInt("stock_after"),
                        Reason.valueOf(rs.getString("reason")), rs.getString("note"), rs.getObject("actor_user_id", UUID.class),
                        rs.getTimestamp("created_at").toInstant()));
        Map<UUID, UserRef> who = users.users(rows.stream().map(Row::actor).distinct().toList());
        return rows.stream().map(r -> new MovementView(r.id(), r.delta(), r.after(), r.reason(), r.note(),
                who.containsKey(r.actor()) ? who.get(r.actor()).name() : null, r.at())).toList();
    }

    // ---------------------------------------------------------------- admin

    @Transactional
    public ItemView create(AuthUser admin, CreateItemRequest request) {
        String name = request.name().trim();
        if (items.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "NAME_TAKEN", "There is already an item called " + name);
        }
        int opening = request.openingStock() == null ? 0 : request.openingStock();
        InventoryItem item = items.saveAndFlush(new InventoryItem(name, request.category(), request.unit().trim(), opening,
                request.lowStockThreshold()));
        if (opening > 0) {
            recordMovement(item.getId(), opening, opening, Reason.OPENING, "Opening stock", admin.id());
        }
        return view(item);
    }

    @Transactional
    public ItemView update(UUID id, UpdateItemRequest request) {
        InventoryItem item = find(id);
        String name = request.name().trim();
        if (items.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "NAME_TAKEN", "There is already an item called " + name);
        }
        item.update(name, request.category(), request.unit().trim(), request.lowStockThreshold(),
                request.active() == null || request.active());
        return view(items.saveAndFlush(item));
    }

    // ---------------------------------------------------------------- stock

    /**
     * Changes the level by {@code delta} and records why. The direction has to match the reason (a restock
     * adds; use and wastage remove; a correction may do either), and the level can't go below zero.
     */
    @Transactional
    public ItemView adjust(AuthUser actor, UUID id, AdjustRequest request) {
        int delta = request.delta();
        Reason reason = request.reason();
        if (delta == 0 || Math.abs((long) delta) > 1_000_000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Enter how many to add or take away");
        }
        if (reason == Reason.OPENING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "An opening count can only be set when the item is added");
        }
        if ((reason == Reason.RESTOCK && delta < 0) || ((reason == Reason.USED || reason == Reason.WASTAGE) && delta > 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", reason == Reason.RESTOCK
                    ? "A restock adds stock — use a positive number" : "Use and wastage take stock away — use a negative number");
        }

        // One statement decides: it applies the change only if the item is active and the level stays >= 0.
        List<Integer> after = jdbc.queryForList("""
                UPDATE inventory_items SET current_stock = current_stock + :delta, updated_at = now()
                WHERE id = :id AND is_active AND current_stock + :delta >= 0
                RETURNING current_stock
                """, new MapSqlParameterSource("id", id).addValue("delta", delta), Integer.class);
        if (after.isEmpty()) {
            InventoryItem item = find(id);
            throw item.isActive()
                    ? new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Only " + item.getCurrentStock() + " "
                            + item.getUnit() + " of " + item.getName() + " left — that would take it below zero")
                    : new ApiException(HttpStatus.CONFLICT, "ITEM_RETIRED", item.getName() + " has been retired");
        }
        recordMovement(id, delta, after.getFirst(), reason, trim(request.note()), actor.id());
        return view(find(id));
    }

    // ---------------------------------------------------------------- helpers

    private void recordMovement(UUID itemId, int delta, int after, Reason reason, String note, UUID actorId) {
        jdbc.update("""
                INSERT INTO inventory_movements (item_id, delta, stock_after, reason, note, actor_user_id)
                VALUES (:item, :delta, :after, :reason, :note, :actor)
                """, new MapSqlParameterSource("item", itemId).addValue("delta", delta).addValue("after", after)
                .addValue("reason", reason.name()).addValue("note", note).addValue("actor", actorId));
    }

    private InventoryItem find(UUID id) {
        return items.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Item not found"));
    }

    /** How full an item is relative to its threshold (0 = empty, 1 = at the threshold): the emptiest sort first. */
    private static double fill(InventoryItem i) {
        return i.getLowStockThreshold() == 0 ? 1 : (double) i.getCurrentStock() / i.getLowStockThreshold();
    }

    static ItemView view(InventoryItem i) {
        return new ItemView(i.getId(), i.getName(), i.getCategory(), i.getUnit(), i.getCurrentStock(),
                i.getLowStockThreshold(), i.isLow(), i.isOut(), i.isActive(), i.getUpdatedAt());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
