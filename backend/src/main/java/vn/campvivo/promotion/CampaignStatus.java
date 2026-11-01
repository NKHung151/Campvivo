package vn.campvivo.promotion;

/** Trạng thái chiến dịch tính từ dữ liệu cho trang admin (api.md mục 4.10) — không lưu DB, xem {@link DiscountCampaign#statusAt}. */
public enum CampaignStatus {
    UPCOMING, RUNNING, ENDED, INACTIVE
}
