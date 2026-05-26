package eDepot.services;

import eDepot.dao.WarehouseItemDAO;
import eDepot.dao.ShippingNoticeDAO;
import eDepot.dao.NoticeLineDAO;
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
import java.util.HashSet;
import java.util.List;
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
     * Option (2): 
     */
    public void processShipmentArrival() {

    }

    /*
     * Option (3): 
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
     * Option (4): 
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
}
