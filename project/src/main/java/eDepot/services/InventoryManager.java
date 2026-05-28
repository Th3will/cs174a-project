package eDepot.services;

import eDepot.dao.WarehouseItemDAO;
import eDepot.dao.ShippingNoticeDAO;
import eDepot.dao.NoticeLineDAO;
import eDepot.dao.NoticeLineDAO.LineDetail;
import eDepot.dao.ManufacturerDAO;
import eDepot.dao.LocationDAO;
import eDepot.models.Location;
import eDepot.models.Manufacturer;
import eDepot.models.NoticeLine;
import eDepot.models.ShippingNotice;
import eDepot.models.WarehouseItem;
import eDepot.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/*
    * This class performs 4 main actions: 
        * (1) process shipping notice
        * (2) process physical shipment
        * (3) check item quantity
        * (4) fill eMart order
*/
public class InventoryManager {
    private final WarehouseItemDAO warehouseItemDAO;
    private final ShippingNoticeDAO shippingNoticeDAO;
    private final NoticeLineDAO noticeLineDAO;
    private final ManufacturerDAO manufacturerDAO;
    private final LocationDAO locationDAO;

    public InventoryManager() {
        this.warehouseItemDAO = new WarehouseItemDAO();
        this.shippingNoticeDAO = new ShippingNoticeDAO();
        this.noticeLineDAO = new NoticeLineDAO();
        this.manufacturerDAO = new ManufacturerDAO();
        this.locationDAO = new LocationDAO();
    }

    /*
     * Input record for a single notice line collected from the CLI.
     * Notices identify products by (manufacturer, model number, quantity).
     * If the (manufacturer, model number) pair is new to eDepot, the caller
     * must also supply min/max levels and a warehouse location so a new
     * Warehouse_Item row can be created with quantity 0 and replenishment
     * equal to the notice quantity.
     */
    public static class NoticeLineInput {
        private final String manufacturerName;
        private final String modelNumber;
        private final int quantity;
        private final Integer minLevel;
        private final Integer maxLevel;
        private final String locationLetter;
        private final Integer locationNumber;

        public NoticeLineInput(String manufacturerName, String modelNumber, int quantity) {
            this(manufacturerName, modelNumber, quantity, null, null, null, null);
        }

        public NoticeLineInput(String manufacturerName, String modelNumber, int quantity,
                               Integer minLevel, Integer maxLevel,
                               String locationLetter, Integer locationNumber) {
            this.manufacturerName = manufacturerName;
            this.modelNumber = modelNumber;
            this.quantity = quantity;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            this.locationLetter = locationLetter;
            this.locationNumber = locationNumber;
        }

        public String getManufacturerName() { return manufacturerName; }
        public String getModelNumber() { return modelNumber; }
        public int getQuantity() { return quantity; }
        public Integer getMinLevel() { return minLevel; }
        public Integer getMaxLevel() { return maxLevel; }
        public String getLocationLetter() { return locationLetter; }
        public Integer getLocationNumber() { return locationNumber; }

        public boolean hasNewItemDetails() {
            return minLevel != null && maxLevel != null
                    && locationLetter != null && locationNumber != null;
        }
    }

    /*
     * Result of processing a single notice line - exposed so the CLI can
     * report what happened (existing vs. newly-assigned stock number).
     */
    public static class ProcessedLine {
        private final String manufacturerName;
        private final String modelNumber;
        private final int quantity;
        private final String stockNumber;
        private final boolean newlyCreated;

        public ProcessedLine(String manufacturerName, String modelNumber, int quantity,
                             String stockNumber, boolean newlyCreated) {
            this.manufacturerName = manufacturerName;
            this.modelNumber = modelNumber;
            this.quantity = quantity;
            this.stockNumber = stockNumber;
            this.newlyCreated = newlyCreated;
        }

        public String getManufacturerName() { return manufacturerName; }
        public String getModelNumber() { return modelNumber; }
        public int getQuantity() { return quantity; }
        public String getStockNumber() { return stockNumber; }
        public boolean isNewlyCreated() { return newlyCreated; }
    }

    /*
     * Snapshot of a stored shipping notice plus its line items, joined to the
     * warehouse so manufacturer/model are populated. Used by the CLI to show
     * a confirmation preview before processing the physical shipment.
     */
    public static class ShipmentPreview {
        private final int shippingNoticeId;
        private final String shippingCompanyName;
        private final boolean alreadyFulfilled;
        private final List<LineDetail> lines;

        public ShipmentPreview(int shippingNoticeId, String shippingCompanyName,
                               boolean alreadyFulfilled, List<LineDetail> lines) {
            this.shippingNoticeId = shippingNoticeId;
            this.shippingCompanyName = shippingCompanyName;
            this.alreadyFulfilled = alreadyFulfilled;
            this.lines = Collections.unmodifiableList(lines);
        }

        public int getShippingNoticeId() { return shippingNoticeId; }
        public String getShippingCompanyName() { return shippingCompanyName; }
        public boolean isAlreadyFulfilled() { return alreadyFulfilled; }
        public List<LineDetail> getLines() { return lines; }
    }

    /*
     * Per-line outcome of a processed physical shipment - reports stock number,
     * quantity that was moved into the warehouse, and the resulting on-hand
     * quantity after the move so the CLI can confirm the new on-hand counts.
     */
    public static class AppliedShipmentLine {
        private final String stockNumber;
        private final String manufacturerName;
        private final String modelNumber;
        private final int quantityReceived;
        private final int newQuantityOnHand;

        public AppliedShipmentLine(String stockNumber, String manufacturerName, String modelNumber,
                                   int quantityReceived, int newQuantityOnHand) {
            this.stockNumber = stockNumber;
            this.manufacturerName = manufacturerName;
            this.modelNumber = modelNumber;
            this.quantityReceived = quantityReceived;
            this.newQuantityOnHand = newQuantityOnHand;
        }

        public String getStockNumber() { return stockNumber; }
        public String getManufacturerName() { return manufacturerName; }
        public String getModelNumber() { return modelNumber; }
        public int getQuantityReceived() { return quantityReceived; }
        public int getNewQuantityOnHand() { return newQuantityOnHand; }
    }

    /*
     * Single (stock_num, quantity) line of an eMart order, collected by the CLI
     * from the printed order sheet that the operator types in.
     */
    public static class OrderLineInput {
        private final String stockNumber;
        private final int quantity;

        public OrderLineInput(String stockNumber, int quantity) {
            this.stockNumber = stockNumber;
            this.quantity = quantity;
        }

        public String getStockNumber() { return stockNumber; }
        public int getQuantity() { return quantity; }
    }

    /*
     * Aggregate result of fillOrder: every order line that was decremented plus
     * any replenishment orders that were generated as a side effect. The two
     * lists together let the CLI print a full receipt of the transaction.
     */
    public static class FillOrderResult {
        private final int orderNumber;
        private final List<FilledOrderLine> filled;
        private final List<GeneratedReplenishment> replenishments;

        public FillOrderResult(int orderNumber, List<FilledOrderLine> filled,
                               List<GeneratedReplenishment> replenishments) {
            this.orderNumber = orderNumber;
            this.filled = Collections.unmodifiableList(filled);
            this.replenishments = Collections.unmodifiableList(replenishments);
        }

        public int getOrderNumber() { return orderNumber; }
        public List<FilledOrderLine> getFilled() { return filled; }
        public List<GeneratedReplenishment> getReplenishments() { return replenishments; }
    }

    /*
     * Per-line outcome of a filled order: how much was decremented and what the
     * new on-hand quantity is, plus enough manufacturer/model info for the CLI
     * to show a human-readable confirmation.
     */
    public static class FilledOrderLine {
        private final String stockNumber;
        private final String manufacturerName;
        private final String modelNumber;
        private final int quantitySold;
        private final int newQuantityOnHand;

        public FilledOrderLine(String stockNumber, String manufacturerName, String modelNumber,
                               int quantitySold, int newQuantityOnHand) {
            this.stockNumber = stockNumber;
            this.manufacturerName = manufacturerName;
            this.modelNumber = modelNumber;
            this.quantitySold = quantitySold;
            this.newQuantityOnHand = newQuantityOnHand;
        }

        public String getStockNumber() { return stockNumber; }
        public String getManufacturerName() { return manufacturerName; }
        public String getModelNumber() { return modelNumber; }
        public int getQuantitySold() { return quantitySold; }
        public int getNewQuantityOnHand() { return newQuantityOnHand; }
    }

    /*
     * One line of an auto-generated replenishment order. replenishmentQuantity
     * is the units that would be ordered for this product on this PO. These
     * lines are never persisted to the database - they exist only so the CLI
     * can print the replenishment order for the manager to send manually.
     */
    public static class GeneratedReplenishmentLine {
        private final String stockNumber;
        private final String modelNumber;
        private final int replenishmentQuantity;

        public GeneratedReplenishmentLine(String stockNumber, String modelNumber,
                                          int replenishmentQuantity) {
            this.stockNumber = stockNumber;
            this.modelNumber = modelNumber;
            this.replenishmentQuantity = replenishmentQuantity;
        }

        public String getStockNumber() { return stockNumber; }
        public String getModelNumber() { return modelNumber; }
        public int getReplenishmentQuantity() { return replenishmentQuantity; }
    }

    /*
     * One whole replenishment order to a single manufacturer. orderNumber is
     * a per-fillOrder local counter (1-based) used only for labelling the
     * printed output - nothing about this record is persisted.
     */
    public static class GeneratedReplenishment {
        private final int orderNumber;
        private final String manufacturerName;
        private final List<GeneratedReplenishmentLine> lines;

        public GeneratedReplenishment(int orderNumber, String manufacturerName,
                                      List<GeneratedReplenishmentLine> lines) {
            this.orderNumber = orderNumber;
            this.manufacturerName = manufacturerName;
            this.lines = Collections.unmodifiableList(lines);
        }

        public int getOrderNumber() { return orderNumber; }
        public String getManufacturerName() { return manufacturerName; }
        public List<GeneratedReplenishmentLine> getLines() { return lines; }
    }

    /*
     * Option (1): Process a received shipping notice end-to-end inside one DB transaction.
     *
     * For each line: if (manufacturer, model_num) already exists in the
     * warehouse, the existing stock number is reused and its replenishment
     * column is incremented. Otherwise a new stock number is assigned and a
     * new Warehouse_Item row is created (quantity = 0, replenishment = notice
     * quantity). The shipping notice header and one Notice_Line per item are
     * inserted last so all FKs resolve. The whole batch commits or rolls back.
     */
    public List<ProcessedLine> processShippingNotice(ShippingNotice notice, List<NoticeLineInput> inputs) {
        if (notice == null) {
            throw new IllegalArgumentException("Shipping notice is required");
        }
        if (notice.getShippingNoticeId() <= 0) {
            throw new IllegalArgumentException("Shipping notice ID must be a positive integer");
        }
        if (notice.getShippingCompanyName() == null || notice.getShippingCompanyName().trim().isEmpty()) {
            throw new IllegalArgumentException("Shipping company name is required");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("A shipping notice must contain at least one item");
        }

        try (Connection conn = DatabaseConnection.testConnection()) {
            conn.setAutoCommit(false);
            try {
                if (shippingNoticeDAO.exists(conn, notice.getShippingNoticeId())) {
                    throw new IllegalArgumentException(
                            "Shipping notice ID already exists: " + notice.getShippingNoticeId());
                }
                shippingNoticeDAO.insertShippingNotice(conn, notice);

                List<ProcessedLine> processed = new ArrayList<>();
                Set<String> stockNumsInThisNotice = new HashSet<>();

                for (NoticeLineInput input : inputs) {
                    validateLineInput(input);

                    if (!manufacturerDAO.exists(conn, input.getManufacturerName())) {
                        manufacturerDAO.insertManufacturer(conn,
                                new Manufacturer(input.getManufacturerName()));
                    }

                    WarehouseItem existing = warehouseItemDAO.findByManufacturerAndModel(
                            conn, input.getManufacturerName(), input.getModelNumber());

                    String stockNumber;
                    boolean newlyCreated;
                    if (existing != null) {
                        // Reject the notice if fulfilling it would push the warehouse past max_level.
                        // The chk_qty_limit DB constraint guards the eventual move-into-quantity step,
                        // but we catch it here so the notice is rejected up front (no partial work).
                        long projected = (long) existing.getQuantity()
                                + (long) existing.getReplenishment()
                                + (long) input.getQuantity();
                        if (projected > existing.getMaxLevel()) {
                            throw new IllegalArgumentException(
                                    "Notice exceeds max stock level for "
                                            + input.getManufacturerName() + "/" + input.getModelNumber()
                                            + " (stock " + existing.getStockNumber() + "): "
                                            + "quantity " + existing.getQuantity()
                                            + " + replenishment " + existing.getReplenishment()
                                            + " + notice " + input.getQuantity()
                                            + " = " + projected
                                            + " > max " + existing.getMaxLevel());
                        }
                        stockNumber = existing.getStockNumber();
                        newlyCreated = false;
                        if (!warehouseItemDAO.addReplenishment(conn, stockNumber, input.getQuantity())) {
                            throw new SQLException("Failed to update replenishment for " + stockNumber);
                        }
                    } 
                    else {
                        if (!input.hasNewItemDetails()) {
                            throw new IllegalArgumentException(
                                    "New product " + input.getManufacturerName() + "/"
                                            + input.getModelNumber()
                                            + " requires min level, max level, and a warehouse location");
                        }
                        if (input.getMaxLevel() < input.getMinLevel()) {
                            throw new IllegalArgumentException(
                                    "Max stock level must be >= min stock level for "
                                            + input.getManufacturerName() + "/" + input.getModelNumber());
                        }
                        // New product starts at quantity 0; the only thing that could exceed max_level
                        // is a notice quantity larger than max_level itself.
                        if (input.getQuantity() > input.getMaxLevel()) {
                            throw new IllegalArgumentException(
                                    "Notice exceeds max stock level for new product "
                                            + input.getManufacturerName() + "/" + input.getModelNumber()
                                            + ": notice " + input.getQuantity()
                                            + " > max " + input.getMaxLevel());
                        }
                        if (locationDAO.isLocationOccupied(conn,
                                input.getLocationLetter(), input.getLocationNumber())) {
                            throw new IllegalArgumentException(
                                    "Location " + input.getLocationLetter() + input.getLocationNumber()
                                            + " is already occupied by another product");
                        }
                        if (!locationDAO.exists(conn,
                                input.getLocationLetter(), input.getLocationNumber())) {
                            locationDAO.insertLocation(conn,
                                    new Location(input.getLocationLetter(), input.getLocationNumber()));
                        }

                        stockNumber = warehouseItemDAO.generateNextStockNumber(conn);
                        newlyCreated = true;

                        WarehouseItem newItem = new WarehouseItem(
                                stockNumber,
                                input.getManufacturerName(),
                                input.getModelNumber(),
                                0,
                                input.getMinLevel(),
                                input.getMaxLevel(),
                                input.getQuantity(),
                                input.getLocationLetter(),
                                input.getLocationNumber());

                        if (!warehouseItemDAO.insertWarehouseItem(conn, newItem)) {
                            throw new SQLException("Failed to insert new warehouse item " + stockNumber);
                        }
                    }

                    if (!stockNumsInThisNotice.add(stockNumber)) {
                        throw new IllegalArgumentException(
                                "Duplicate item within the same notice: " + stockNumber
                                        + " (combine the quantities into a single line)");
                    }

                    NoticeLine line = new NoticeLine(
                            notice.getShippingNoticeId(), stockNumber, input.getQuantity());
                    if (!noticeLineDAO.insertNoticeLine(conn, line)) {
                        throw new SQLException("Failed to insert notice line for " + stockNumber);
                    }

                    processed.add(new ProcessedLine(
                            input.getManufacturerName(),
                            input.getModelNumber(),
                            input.getQuantity(),
                            stockNumber,
                            newlyCreated));
                }

                conn.commit();
                return processed;
            }
            catch (RuntimeException | SQLException e) {
                try { conn.rollback(); } catch (SQLException ignore) { /* surfaced below */ }
                if (e instanceof IllegalArgumentException) {
                    throw (IllegalArgumentException) e;
                }
                throw new RuntimeException("Failed to process shipping notice: " + e.getMessage(), e);
            }
        }
        catch (SQLException e) {
            throw new RuntimeException("DB connection error while processing shipping notice: "
                    + e.getMessage(), e);
        }
    }

    /*
     * Option (2): Process a physical shipment for a previously-received shipping
     * notice. The shipment is assumed to match the notice exactly (no partial /
     * mismatched fulfillment is modeled).
     *
     * For every Notice_Line of the given snid, this transaction does a single
     * batched MERGE that moves notice_quantity out of replenishment and into
     * quantity on the matching Warehouse_Item row, then marks the notice
     * fulfilled. Double-fulfillment is prevented by the conditional UPDATE on
     * fulfilled='N' inside markFulfilled - if the row had already flipped to
     * 'Y', no row is updated and we abort.
     */
    public List<AppliedShipmentLine> processShipmentArrival(int snid) {
        try (Connection conn = DatabaseConnection.testConnection()) {
            conn.setAutoCommit(false);
            try {
                ShippingNotice notice = shippingNoticeDAO.findByShippingNoticeId(conn, snid);
                if (notice == null) {
                    throw new IllegalArgumentException("No shipping notice exists for ID " + snid);
                }
                if (shippingNoticeDAO.isFulfilled(conn, snid)) {
                    throw new IllegalArgumentException(
                            "Shipping notice " + snid + " has already been fulfilled");
                }

                List<LineDetail> details = noticeLineDAO.getLineDetailsForNotice(conn, snid);
                if (details.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Shipping notice " + snid + " has no line items (cannot process empty shipment)");
                }

                int updated = warehouseItemDAO.applyShipmentForNotice(conn, snid);
                if (updated != details.size()) {
                    throw new SQLException("Expected to update " + details.size()
                            + " warehouse rows for snid " + snid
                            + " but updated " + updated);
                }

                if (!shippingNoticeDAO.markFulfilled(conn, snid)) {
                    // Lost a race: another caller flipped the flag between our isFulfilled() check
                    // and the markFulfilled UPDATE. Roll back to keep the inventory move atomic
                    // with the fulfillment flag.
                    throw new IllegalArgumentException(
                            "Shipping notice " + snid + " was fulfilled by another transaction");
                }

                // Re-read the new on-hand quantities so the CLI can echo what changed.
                Map<String, Integer> newQuantities = new HashMap<>();
                for (LineDetail d : details) {
                    Integer q = warehouseItemDAO.getQuantityByStockNumber(conn, d.getStockNumber());
                    if (q == null) {
                        throw new SQLException("Could not re-read quantity for " + d.getStockNumber());
                    }
                    newQuantities.put(d.getStockNumber(), q);
                }

                conn.commit();

                List<AppliedShipmentLine> applied = new ArrayList<>();
                for (LineDetail d : details) {
                    applied.add(new AppliedShipmentLine(
                            d.getStockNumber(),
                            d.getManufacturerName(),
                            d.getModelNumber(),
                            d.getNoticeQuantity(),
                            newQuantities.get(d.getStockNumber())));
                }
                return applied;
            }
            catch (RuntimeException | SQLException e) {
                try { conn.rollback(); } catch (SQLException ignore) { /* surfaced below */ }
                if (e instanceof IllegalArgumentException) {
                    throw (IllegalArgumentException) e;
                }
                throw new RuntimeException("Failed to process shipment arrival for snid " + snid
                        + ": " + e.getMessage(), e);
            }
        }
        catch (SQLException e) {
            throw new RuntimeException("DB connection error while processing shipment arrival: "
                    + e.getMessage(), e);
        }
    }

    /*
     * Option (3): check an item's quantity based off of its stock number; simply queries Warehouse_Item table
     */
    public int checkItemQuantity(String stockNum) {
        if (stockNum == null || !stockNum.matches("^[A-Z]{2}[0-9]{5}$")) {
            throw new IllegalArgumentException("Invalid stock number format - expected XXnnnnn where XX = 2 uppercase letters and nnnnn = 5 numbers");
        }

        Integer quantity = warehouseItemDAO.getQuantityByStockNumber(stockNum);
        if (quantity == null) {
            throw new IllegalArgumentException("No warehouse item found for stock number: " + stockNum);
        }

        return quantity;
    }

    /*
     * Option (4): fill an order from eMart inside one DB transaction.
     *
     * Inputs are an order number (printed on the eMart order sheet) and a list
     * of (stock_num, quantity) lines that the CLI operator manually types in.
     * eDepot does NOT cross into the eMart database - eMart hands the data in.
     *
     * Behavior:
     *  - Reject the whole order if any line references an unknown stock number
     *    or asks for more units than are on-hand. The "quantity >= ?" guard in
     *    decrementQuantity catches concurrent under-runs.
     *  - After decrements, for every manufacturer that appeared in the order,
     *    count how many of THEIR inventory items are now below their min_level.
     *    Three or more triggers a replenishment order for that manufacturer.
     *  - The replenishment order is COMPUTED ONLY - it is not written to the
     *    database. It is returned in the result so the CLI can print it for
     *    the manager to send to the manufacturer by hand. The warehouse
     *    replenishment column is NOT modified by this trigger; it is only
     *    bumped by an actual shipping notice arrival (option 1).
     *  - The replenishment order includes every one of that manufacturer's
     *    items where quantity + replenishment < max_level, ordered up to
     *    max_level (so replenishment_quantity = max_level - quantity -
     *    replenishment).
     *  - Atomic: any failure during the inventory decrements rolls back the
     *    whole order. Replenishment computation is read-only and never causes
     *    a rollback.
     */
    public FillOrderResult fillOrder(int orderNumber, List<OrderLineInput> inputs) {
        if (orderNumber < 0) {
            throw new IllegalArgumentException("Order number must be >= 0");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("An order must contain at least one line item");
        }

        try (Connection conn = DatabaseConnection.testConnection()) {
            conn.setAutoCommit(false);
            try {
                List<FilledOrderLine> filled = new ArrayList<>();
                Set<String> manufacturersInOrder = new HashSet<>();
                Set<String> stockNumsInThisOrder = new HashSet<>();

                for (OrderLineInput input : inputs) {
                    validateOrderLineInput(input);

                    if (!stockNumsInThisOrder.add(input.getStockNumber())) {
                        throw new IllegalArgumentException(
                                "Duplicate stock number within the same order: " + input.getStockNumber()
                                        + " (combine the quantities into a single line)");
                    }

                    WarehouseItem item = warehouseItemDAO.findByStockNumber(conn, input.getStockNumber());
                    if (item == null) {
                        throw new IllegalArgumentException(
                                "No warehouse item found for stock number: " + input.getStockNumber());
                    }
                    if (item.getQuantity() < input.getQuantity()) {
                        throw new IllegalArgumentException(
                                "Insufficient stock for " + input.getStockNumber()
                                        + " (" + item.getManufacturerName() + "/" + item.getModelNumber()
                                        + "): requested " + input.getQuantity()
                                        + ", on-hand " + item.getQuantity());
                    }

                    if (!warehouseItemDAO.decrementQuantity(conn, input.getStockNumber(), input.getQuantity())) {
                        // Race: another transaction drove the on-hand quantity below our request
                        // between findByStockNumber() above and this UPDATE. Abort cleanly.
                        throw new IllegalArgumentException(
                                "Stock for " + input.getStockNumber()
                                        + " became insufficient during the transaction");
                    }

                    int newQuantity = item.getQuantity() - input.getQuantity();
                    manufacturersInOrder.add(item.getManufacturerName());
                    filled.add(new FilledOrderLine(
                            input.getStockNumber(),
                            item.getManufacturerName(),
                            item.getModelNumber(),
                            input.getQuantity(),
                            newQuantity));
                }

                // -- TRIGGER FOR REPLENISHMENT ORDER BELOW --
                //
                // Replenishment orders are PRINT-ONLY. We do not insert into
                // eDepot_Replenishment_Order / eDepot_Replenishment_Line, and we do
                // not bump the warehouse_item.replenishment column - that column is
                // only moved by the shipping-notice / shipment-arrival flow.

                // need a list of GeneratedReplenishment, because each replenishment order is tied to a manufacturer
                // there can be items from multiple manufacturers that need to be replenished
                List<GeneratedReplenishment> replenishments = new ArrayList<>();

                // Sort the triggered manufacturers alphabetically so the printed
                // replenishment-order numbers are assigned deterministically when one
                // eMart order touches several manufacturers.
                int nextOrderNumber = 1;
                for (String mname : new TreeSet<>(manufacturersInOrder)) {
                    int belowMin = warehouseItemDAO.countItemsBelowMinForManufacturer(conn, mname);
                    if (belowMin < 3) {
                        continue;
                    }
                    List<WarehouseItem> candidates = warehouseItemDAO.findItemsBelowMaxForManufacturer(conn, mname);
                    
                    // i believe this condition is to check for race conditions, if somehow 2 eMart orders are placed close together
                    // (probably not tested on the demo though, but keep in case)
                    if (candidates.isEmpty()) {
                        // Trigger fired but every item for this manufacturer is already at
                        // or fully covered up to max_level. Nothing to order.
                        continue;
                    }

                    List<GeneratedReplenishmentLine> repLines = new ArrayList<>();
                    for (WarehouseItem item : candidates) {
                        int repQty = item.getMaxLevel() - item.getQuantity() - item.getReplenishment();
                        // Defensive: query filter guarantees repQty > 0, but skip just in
                        // case so we don't print a zero/negative line.
                        if (repQty <= 0) {
                            continue;
                        }
                        repLines.add(new GeneratedReplenishmentLine(
                                item.getStockNumber(),
                                item.getModelNumber(),
                                repQty));
                    }

                    if (repLines.isEmpty()) {
                        continue;
                    }

                    replenishments.add(new GeneratedReplenishment(nextOrderNumber++, mname, repLines));
                }

                conn.commit();
                return new FillOrderResult(orderNumber, filled, replenishments);
            }
            catch (RuntimeException | SQLException e) {
                try { conn.rollback(); } catch (SQLException ignore) { /* surfaced below */ }
                if (e instanceof IllegalArgumentException) {
                    throw (IllegalArgumentException) e;
                }
                throw new RuntimeException("Failed to fill order " + orderNumber + ": " + e.getMessage(), e);
            }
        }
        catch (SQLException e) {
            throw new RuntimeException("DB connection error while filling order " + orderNumber
                    + ": " + e.getMessage(), e);
        }
    }

    // HELPER METHODS -- all below this line
    private void validateOrderLineInput(OrderLineInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Order line is null");
        }
        if (input.getStockNumber() == null || !input.getStockNumber().matches("^[A-Z]{2}[0-9]{5}$")) {
            throw new IllegalArgumentException(
                    "Invalid stock number format - expected XXnnnnn where XX = 2 uppercase letters "
                            + "and nnnnn = 5 digits (got: " + input.getStockNumber() + ")");
        }
        if (input.getQuantity() <= 0) {
            throw new IllegalArgumentException(
                    "Order quantity must be positive for " + input.getStockNumber());
        }
    }

    private void validateLineInput(NoticeLineInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Notice line is null");
        }
        if (input.getManufacturerName() == null || input.getManufacturerName().trim().isEmpty()) {
            throw new IllegalArgumentException("Manufacturer name is required on every notice line");
        }
        if (input.getModelNumber() == null || input.getModelNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Model number is required on every notice line");
        }
        if (input.getQuantity() <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be positive for " + input.getManufacturerName() + "/"
                            + input.getModelNumber());
        }
        if (input.hasNewItemDetails()) {
            if (input.getMinLevel() < 0 || input.getMaxLevel() < 0) {
                throw new IllegalArgumentException("Stock levels must be non-negative");
            }
            if (input.getLocationNumber() < 0) {
                throw new IllegalArgumentException("Location number must be >= 0");
            }
            if (input.getLocationLetter() == null
                    || !input.getLocationLetter().matches("^[A-Z]$")) {
                throw new IllegalArgumentException(
                        "Location letter must be a single A-Z character (uppercase)");
            }
        }
    }

    /*
     * Returns true iff a warehouse item already exists for the given
     * (manufacturer, model number) pair. The CLI uses this to decide
     * whether it needs to collect extra setup fields (min/max levels,
     * location) for a notice line. The authoritative new-vs-existing
     * decision still happens inside processShippingNotice's transaction.
     */
    public boolean isKnownWarehouseProduct(String manufacturerName, String modelNumber) {
        if (manufacturerName == null || manufacturerName.trim().isEmpty() || modelNumber == null || modelNumber.trim().isEmpty()) {
            return false;
        }
        try (Connection conn = DatabaseConnection.testConnection()) {
            return warehouseItemDAO.findByManufacturerAndModel(conn, manufacturerName, modelNumber) != null;
        }
        catch (SQLException e) {
            throw new RuntimeException("DB error while looking up product "
                    + manufacturerName + "/" + modelNumber + ": " + e.getMessage(), e);
        }
    }

    public boolean shippingNoticeExists(int snid) {
        try (Connection conn = DatabaseConnection.testConnection()) {
            return shippingNoticeDAO.exists(conn, snid);
        } catch (SQLException e) {
            throw new RuntimeException("DB error while checking shipping notice ID: " + e.getMessage(), e);
        }
    }

    public boolean isLocationOccupied(String letter, int number) {
        try (Connection conn = DatabaseConnection.testConnection()) {
            return locationDAO.isLocationOccupied(conn, letter, number);
        } catch (SQLException e) {
            throw new RuntimeException("DB error while checking location: " + e.getMessage(), e);
        }
    }

    /*
     * Read-only lookup the CLI uses to confirm a shipment before processing it.
     * Returns null if the notice ID does not exist. The alreadyFulfilled flag
     * lets the CLI bail out with a clean message before asking for confirmation.
     */
    public ShipmentPreview getShipmentPreview(int snid) {
        try (Connection conn = DatabaseConnection.testConnection()) {
            ShippingNotice notice = shippingNoticeDAO.findByShippingNoticeId(conn, snid);
            if (notice == null) {
                return null;
            }
            boolean fulfilled = shippingNoticeDAO.isFulfilled(conn, snid);
            List<LineDetail> lines = noticeLineDAO.getLineDetailsForNotice(conn, snid);
            return new ShipmentPreview(
                    notice.getShippingNoticeId(),
                    notice.getShippingCompanyName(),
                    fulfilled,
                    lines);
        }
        catch (SQLException e) {
            throw new RuntimeException("DB error while loading shipping notice " + snid + ": "
                    + e.getMessage(), e);
        }
    }
}
