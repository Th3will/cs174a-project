package eDepot.models;

public class ShippingNotice {
    private int shippingNoticeId;
    private String shippingCompanyName;

    public ShippingNotice() {
    }

    public ShippingNotice(int shippingNoticeId, String shippingCompanyName) {
        this.shippingNoticeId = shippingNoticeId;
        this.shippingCompanyName = shippingCompanyName;
    }

    public int getShippingNoticeId() {
        return shippingNoticeId;
    }

    public void setShippingNoticeId(int shippingNoticeId) {
        this.shippingNoticeId = shippingNoticeId;
    }

    public String getShippingCompanyName() {
        return shippingCompanyName;
    }

    public void setShippingCompanyName(String shippingCompanyName) {
        this.shippingCompanyName = shippingCompanyName;
    }

    @Override
    public String toString() {
        return "ShippingNotice{" +
                "shippingNoticeId=" + shippingNoticeId +
                ", shippingCompanyName='" + shippingCompanyName + '\'' +
                '}';
    }
}
