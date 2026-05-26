package eDepot.services;

import eDepot.dao.WarehouseItemDAO;
import eDepot.dao.ShippingNoticeDAO;
import eDepot.dao.NoticeLineDAO;
import eDepot.dao.NoticeLineDAO.LineDetail;
import eDepot.dao.ReplenishmentOrderDAO;
import eDepot.dao.ReplenishmentLineDAO;
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
    private final ReplenishmentOrderDAO replenishmentOrderDAO;
    private final ReplenishmentLineDAO replenishmentLineDAO;
    private final ManufacturerDAO manufacturerDAO;
    private final LocationDAO locationDAO;

    public InventoryManager() {
        this.warehouseItemDAO = new WarehouseItemDAO();
        this.shippingNoticeDAO = new ShippingNoticeDAO();
        this.noticeLineDAO = new NoticeLineDAO();
        this.replenishmentOrderDAO = new ReplenishmentOrderDAO();
        this.replenishmentLineDAO = new ReplenishmentLineDAO();
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
                ShippingNotice notice = shippingNoticeDAO.findById(conn, snid);
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
     * Option (4): fill an order from eMart
     */
    public void fillOrder() {
        
    }

    // HELPER METHODS -- all below this line
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
        if (manufacturerName == null || manufacturerName.trim().isEmpty()
                || modelNumber == null || modelNumber.trim().isEmpty()) {
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
            ShippingNotice notice = shippingNoticeDAO.findById(conn, snid);
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
