package eDepot.models;

public class NoticeLine {
    private int shippingNoticeId;
    private String stockNumber;
    private int noticeQuantity;

    public NoticeLine() {
    }

    public NoticeLine(int shippingNoticeId, String stockNumber, int noticeQuantity) {
        this.shippingNoticeId = shippingNoticeId;
        this.stockNumber = stockNumber;
        this.noticeQuantity = noticeQuantity;
    }

    public int getShippingNoticeId() {
        return shippingNoticeId;
    }

    public void setShippingNoticeId(int shippingNoticeId) {
        this.shippingNoticeId = shippingNoticeId;
    }

    public String getStockNumber() {
        return stockNumber;
    }

    public void setStockNumber(String stockNumber) {
        this.stockNumber = stockNumber;
    }

    public int getNoticeQuantity() {
        return noticeQuantity;
    }

    public void setNoticeQuantity(int noticeQuantity) {
        this.noticeQuantity = noticeQuantity;
    }

    @Override
    public String toString() {
        return "NoticeLine{" +
                "shippingNoticeId=" + shippingNoticeId +
                ", stockNumber='" + stockNumber + '\'' +
                ", noticeQuantity=" + noticeQuantity +
                '}';
    }
}
