package vn.campvivo.chatbot.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import vn.campvivo.chatbot.advisor.AdvisorModels.PlanKind;
import vn.campvivo.chatbot.advisor.AdvisorModels.PlanLine;
import vn.campvivo.chatbot.advisor.AdvisorModels.TripAdvice;
import vn.campvivo.chatbot.advisor.AdvisorModels.TripNeed;
import vn.campvivo.chatbot.advisor.AdvisorModels.TripPlan;
import vn.campvivo.chatbot.advisor.TripAdvisorService;
import vn.campvivo.chatbot.advisor.TripType;
import vn.campvivo.chatbot.common.util.TextUtil;
import vn.campvivo.chatbot.tools.ToolViews.Notice;

import java.util.List;
import java.util.Locale;

/**
 * Tool tư vấn chuyến đi: combo trong DB khớp nhất + sản phẩm bổ sung trong ngân sách ({@link TripAdvisorService}).
 * Thẻ của phương án tốt nhất được gửi kèm câu trả lời (số lượng đề xuất + lý do).
 */
@Component
public class AdvisorTools {

    static final int MAX_PEOPLE = 30;

    private final TripAdvisorService advisor;

    public AdvisorTools(TripAdvisorService advisor) {
        this.advisor = advisor;
    }

    public record LineView(String item, String productSlug, String productName, int quantity, long unitPrice,
                           long lineTotal, String reason) {}

    public record PlanView(String type, String comboSlug, String comboName, Long comboPrice, Integer comboFitsPeople,
                           List<String> comboAlreadyCovers, List<LineView> addOns, long total, String totalText,
                           long remainingBudget, boolean enoughForEssentials, List<String> missingEssentials,
                           List<String> skippedOptional) {}

    public record AdviceView(String trip, int people, long budget, String budgetText, List<PlanView> plans,
                             Long minimumBudgetForEssentials, List<String> essentialsOutOfStock,
                             List<String> skippedBecauseCustomerHasIt, String guidance) {}

    @Tool(name = "recommendForTrip", description = """
            Tư vấn đồ cho một chuyến đi theo số người và ngân sách: chọn combo có sẵn của cửa hàng khớp nhất, rồi đề \
            xuất thêm sản phẩm lẻ cho phần còn thiếu, tổng tiền không vượt ngân sách. Chỉ gọi khi đã biết số người và \
            ngân sách (thiếu thì hỏi khách trước). Trả về tối đa 3 phương án đã tính sẵn tiền — KHÔNG tự cộng lại.""")
    public Object recommendForTrip(
            @ToolParam(description = "Số người đi (1-30)") Integer people,
            @ToolParam(description = "Ngân sách tối đa, VND (vd. 8 triệu = 8000000)") Long budget,
            @ToolParam(description = "Loại chuyến: CAMPING (cắm trại qua đêm), PICNIC (dã ngoại trong ngày), "
                    + "TREKKING_DAY (leo núi trong ngày), TREKKING_OVERNIGHT (leo núi qua đêm), BEACH (đi biển/bơi)")
            String tripType,
            @ToolParam(description = "Số đêm (0 nếu đi trong ngày)", required = false) Integer nights,
            @ToolParam(description = "true nếu đi mùa đông / nơi lạnh", required = false) Boolean winter,
            @ToolParam(description = "Món khách đã có, không cần mua (vd. ['lều', 'bếp'])", required = false)
            List<String> alreadyHave,
            ToolContext toolContext) {
        ToolSession session = ToolSession.from(toolContext);
        if (!session.tryCall()) {
            return ToolViews.tooManyCalls();
        }
        if (people == null || people < 1 || people > MAX_PEOPLE) {
            return new Notice("Số người không hợp lệ (1-" + MAX_PEOPLE + "). Hãy hỏi lại khách đi mấy người.");
        }
        if (budget == null || budget <= 0) {
            return new Notice("Chưa có ngân sách hợp lệ. Hãy hỏi khách dự định chi khoảng bao nhiêu.");
        }
        TripType type = parseType(tripType, nights);
        if (type == null) {
            return new Notice("Không rõ loại chuyến '" + tripType + "'. Hãy hỏi khách đi cắm trại, picnic, leo núi "
                    + "hay đi biển.");
        }
        TripAdvice advice = advisor.advise(new TripNeed(people, budget, type, nights == null ? 0 : nights,
                Boolean.TRUE.equals(winter), alreadyHave == null ? List.of() : alreadyHave));
        collectCards(advice, session);
        return view(advice);
    }

    private static void collectCards(TripAdvice advice, ToolSession session) {
        for (int i = 0; i < advice.plans().size(); i++) {
            TripPlan plan = advice.plans().get(i);
            boolean best = i == 0;
            if (plan.combo() != null) {
                ChatCard card = ChatCard.combo(plan.combo(), 1, plan.comboCovers().isEmpty() ? null
                        : "Combo đã gồm: " + String.join(", ", plan.comboCovers()).toLowerCase(Locale.ROOT));
                if (best) {
                    session.recommend(card);
                } else {
                    session.found(card);
                }
            }
            if (best) {
                plan.lines().forEach(l -> session.recommend(ChatCard.product(l.product(), l.quantity(), l.reason())));
            }
        }
    }

    private static AdviceView view(TripAdvice a) {
        List<PlanView> plans = a.plans().stream().map(AdvisorTools::planView).toList();
        String guidance;
        if (a.plans().isEmpty() || !a.plans().get(0).coversRequired()) {
            guidance = a.minimumBudget() != null && a.minimumBudget() > a.budget()
                    ? "Ngân sách chưa đủ cho đồ cần thiết; cần tối thiểu khoảng "
                    + TextUtil.formatVnd(a.minimumBudget()) + ". Hãy nói rõ với khách và gợi ý tăng ngân sách "
                    + "hoặc bỏ bớt món."
                    : "Có món cần thiết đang hết hàng; hãy nói rõ với khách.";
        } else {
            guidance = "Giới thiệu phương án đầu tiên trước (nói rõ phần nào có trong combo, phần nào mua thêm, "
                    + "tổng tiền), sau đó nhắc ngắn gọn phương án khác nếu có.";
        }
        return new AdviceView(a.tripLabel(), a.people(), a.budget(), TextUtil.formatVnd(a.budget()), plans,
                a.minimumBudget(), a.unavailableRequired(), a.ignoredBecauseOwned(), guidance);
    }

    private static PlanView planView(TripPlan p) {
        List<LineView> lines = p.lines().stream().map(AdvisorTools::lineView).toList();
        boolean combo = p.kind() == PlanKind.COMBO_PLUS_PRODUCTS;
        return new PlanView(combo ? "Combo + mua thêm" : "Chỉ mua lẻ",
                combo ? p.combo().slug() : null, combo ? p.combo().name() : null,
                combo ? p.combo().price() : null, p.comboPeople(), p.comboCovers(), lines, p.total(),
                TextUtil.formatVnd(p.total()), p.remaining(), p.coversRequired(), p.missingRequired(),
                p.skippedOptional());
    }

    private static LineView lineView(PlanLine l) {
        return new LineView(l.slotLabel(), l.product().slug(), l.product().name(), l.quantity(), l.product().price(),
                l.lineTotal(), l.reason());
    }

    static TripType parseType(String tripType, Integer nights) {
        if (tripType == null || tripType.isBlank()) {
            return null;
        }
        try {
            TripType t = TripType.valueOf(tripType.trim().toUpperCase(Locale.ROOT));
            // "Leo núi" có ngủ lại → qua đêm
            if (t == TripType.TREKKING_DAY && nights != null && nights > 0) {
                return TripType.TREKKING_OVERNIGHT;
            }
            return t;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
