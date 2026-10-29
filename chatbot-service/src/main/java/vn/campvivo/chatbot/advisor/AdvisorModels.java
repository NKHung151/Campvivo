package vn.campvivo.chatbot.advisor;

import vn.campvivo.chatbot.catalog.CatalogModels.ComboDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductCard;

import java.util.List;
import java.util.Map;

/**
 * Đầu vào / kết quả của tư vấn chuyến đi (CB-17, CB-18). Chỉ sống trong một lần hỏi, không lưu DB.
 */
public final class AdvisorModels {

    private AdvisorModels() {
    }

    /**
     * Nhu cầu LLM trích từ câu của khách. `alreadyHave`: món khách đã có (vd. "lều") — bỏ qua slot tương ứng.
     */
    public record TripNeed(int people, long budget, TripType tripType, int nights, boolean winter,
                           List<String> alreadyHave) {}

    /**
     * Đánh giá một combo trong DB so với mẫu nhu cầu: mỗi slot combo phủ được bao nhiêu đơn vị
     * (lều: tổng sức chứa), ước lượng số người phù hợp (null nếu không suy ra được).
     */
    public record ComboFit(ComboDetail combo, Map<String, Integer> coveredUnits, Integer estimatedPeople,
                           boolean winterReady, boolean relevant) {}

    public record PlanLine(String slotKey, String slotLabel, ProductCard product, int quantity, long lineTotal,
                           String reason) {}

    public enum PlanKind { COMBO_PLUS_PRODUCTS, PRODUCTS_ONLY }

    /**
     * Một phương án: combo (có thể null) + các món mua thêm. `missingRequired`: món bắt buộc không mua nổi trong ngân
     * sách hoặc hết hàng; `comboCovers`: các nhóm đồ combo đã có.
     */
    public record TripPlan(PlanKind kind, ComboDetail combo, List<String> comboCovers, Integer comboPeople,
                           List<PlanLine> lines, long total, long remaining, List<String> missingRequired,
                           List<String> skippedOptional) {

        public boolean coversRequired() {
            return missingRequired.isEmpty();
        }
    }

    /**
     * Kết quả tư vấn. `minimumBudget`: số tiền tối thiểu để đủ đồ bắt buộc (null nếu không tính được vì hết hàng).
     */
    public record TripAdvice(String tripLabel, int people, long budget, List<TripPlan> plans, Long minimumBudget,
                             List<String> unavailableRequired, List<String> ignoredBecauseOwned) {}
}
