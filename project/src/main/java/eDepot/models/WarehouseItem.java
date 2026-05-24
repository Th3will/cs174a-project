package eDepot.models;

public class WarehouseItem {
    private String stockNumber;
    private String manufacturerName;
    private String modelNumber;
    private int quantity;
    private int minLevel;
    private int maxLevel;
    private int replenishment;
    private String locationLetter;
    private int locationNumber;

    public WarehouseItem() {
    }

    public WarehouseItem(
            String stockNumber,
            String manufacturerName,
            String modelNumber,
            int quantity,
            int minLevel,
            int maxLevel,
            int replenishment,
            String locationLetter,
            int locationNumber
    ) {
        this.stockNumber = stockNumber;
        this.manufacturerName = manufacturerName;
        this.modelNumber = modelNumber;
        this.quantity = quantity;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.replenishment = replenishment;
        this.locationLetter = locationLetter;
        this.locationNumber = locationNumber;
    }

    public String getStockNumber() {
        return stockNumber;
    }

    public void setStockNumber(String stockNumber) {
        this.stockNumber = stockNumber;
    }

    public String getManufacturerName() {
        return manufacturerName;
    }

    public void setManufacturerName(String manufacturerName) {
        this.manufacturerName = manufacturerName;
    }

    public String getModelNumber() {
        return modelNumber;
    }

    public void setModelNumber(String modelNumber) {
        this.modelNumber = modelNumber;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public void setMinLevel(int minLevel) {
        this.minLevel = minLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public int getReplenishment() {
        return replenishment;
    }

    public void setReplenishment(int replenishment) {
        this.replenishment = replenishment;
    }

    public String getLocationLetter() {
        return locationLetter;
    }

    public void setLocationLetter(String locationLetter) {
        this.locationLetter = locationLetter;
    }

    public int getLocationNumber() {
        return locationNumber;
    }

    public void setLocationNumber(int locationNumber) {
        this.locationNumber = locationNumber;
    }

    @Override
    public String toString() {
        return "WarehouseItem{" +
                "stockNumber='" + stockNumber + '\'' +
                ", manufacturerName='" + manufacturerName + '\'' +
                ", modelNumber='" + modelNumber + '\'' +
                ", quantity=" + quantity +
                ", minLevel=" + minLevel +
                ", maxLevel=" + maxLevel +
                ", replenishment=" + replenishment +
                ", locationLetter='" + locationLetter + '\'' +
                ", locationNumber=" + locationNumber +
                '}';
    }
}
