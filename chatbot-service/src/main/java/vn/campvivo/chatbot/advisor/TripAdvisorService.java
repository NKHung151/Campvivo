package vn.campvivo.chatbot.advisor;

import org.springframework.stereotype.Service;
import vn.campvivo.chatbot.advisor.AdvisorModels.ComboFit;
import vn.campvivo.chatbot.advisor.AdvisorModels.PlanKind;
import vn.campvivo.chatbot.advisor.AdvisorModels.PlanLine;
import vn.campvivo.chatbot.advisor.AdvisorModels.TripAdvice;
import vn.campvivo.chatbot.advisor.AdvisorModels.TripNeed;
import vn.campvivo.chatbot.advisor.AdvisorModels.TripPlan;
import vn.campvivo.chatbot.advisor.TripTemplateProperties.QuantityMode;
import vn.campvivo.chatbot.advisor.TripTemplateProperties.TripTemplate;
import vn.campvivo.chatbot.catalog.CatalogModels.CategoryNode;
import vn.campvivo.chatbot.catalog.CatalogModels.ComboDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductFilter;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductSort;
import vn.campvivo.chatbot.catalog.CatalogReadPort;
import vn.campvivo.chatbot.common.util.TextUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tư vấn chuyến đi (FR-28, D-28): ưu tiên combo admin dựng trong DB, thiếu thì bổ sung sản phẩm lẻ trong ngân sách
 * còn lại. Chọn món và cộng tiền hoàn toàn bằng code (LLM chỉ trích nhu cầu và diễn giải kết quả).
 * <ol>
 *   <li>Mẫu nhu cầu của loại chuyến → số đơn vị cần cho từng slot (bỏ slot khách đã có).</li>
 *   <li>Mỗi combo đang bán, còn bộ, giá ≤ ngân sách → {@link ComboFitAnalyzer}; bỏ combo không phủ slot bắt buộc nào.</li>
 *   <li>Phần còn thiếu → chọn sản phẩm lẻ còn hàng: slot bắt buộc trước (giữ đủ tiền cho các slot bắt buộc còn lại),
 *       rồi slot nên có nếu còn tiền. Trong phần tiền chia cho slot, chọn món giá cao nhất vừa túi.</li>
 *   <li>Thêm phương án chỉ mua lẻ để so sánh. Xếp: đủ đồ bắt buộc → có combo → đủ đồ nên có → combo phủ nhiều
 *       → rẻ hơn. Trả tối đa 3 phương án.</li>
 * </ol>
 */
@Service
public class TripAdvisorService {

    static final int MAX_PLANS = 3;
    private static final int CANDIDATES_PER_SLOT = 60;
    private static final int MAX_COMBOS = 50;

    private final CatalogReadPort catalog;
    private final ComboFitAnalyzer analyzer;
    private final TripTemplateProperties templates;

    public TripAdvisorService(CatalogReadPort catalog, ComboFitAnalyzer analyzer, TripTemplateProperties templates) {
        this.catalog = catalog;
        this.analyzer = analyzer;
        this.templates = templates;
    }

    public TripAdvice advise(TripNeed need) {
        TripTemplate template = templates.get(need.tripType());
        Map<String, CategoryNode> bySlug = catalog.categories().stream()
                .collect(Collectors.toMap(CategoryNode::slug, Function.identity(), (a, b) -> a));
        List<ResolvedSlot> slots = template.slots().stream().map(s -> ResolvedSlot.of(s, bySlug)).toList();

        List<String> owned = new ArrayList<>();
        Map<String, Integer> needUnits = new LinkedHashMap<>();
        for (ResolvedSlot s : slots) {
            boolean alreadyHave = need.alreadyHave() != null
                    && need.alreadyHave().stream().anyMatch(s::mentionedIn);
            if (alreadyHave) {
                owned.add(s.label());
            } else {
                needUnits.put(s.key(), s.slot().needFor(need.people()));
            }
        }

        Map<String, List<Option>> options = new LinkedHashMap<>();
        for (ResolvedSlot s : slots) {
            if (needUnits.containsKey(s.key())) {
                options.put(s.key(), candidates(s));
            }
        }
        List<String> unavailableRequired = slots.stream()
                .filter(s -> s.slot().required() && needUnits.containsKey(s.key()))
                .filter(s -> options.get(s.key()).isEmpty())
                .map(ResolvedSlot::label).toList();

        Planner planner = new Planner(slots, options, need.winter());
        List<TripPlan> plans = new ArrayList<>();
        Long minimumBudget = planner.minimumRequiredCost(needUnits);

        for (ComboDetail combo : catalog.searchCombos(null, null, need.budget(), true, MAX_COMBOS)) {
            ComboFit fit = analyzer.analyze(combo, slots);
            if (!fit.relevant()) {
                continue;
            }
            Map<String, Integer> remaining = new LinkedHashMap<>();
            needUnits.forEach((key, units) -> remaining.put(key,
                    Math.max(0, units - fit.coveredUnits().getOrDefault(key, 0))));
            TripPlan plan = planner.fill(PlanKind.COMBO_PLUS_PRODUCTS, fit, remaining, need.budget());
            plans.add(plan);
            Long withCombo = planner.minimumRequiredCost(remaining);
            if (withCombo != null) {
                long candidate = withCombo + combo.price();
                minimumBudget = minimumBudget == null ? candidate : Math.min(minimumBudget, candidate);
            }
        }
        plans.add(planner.fill(PlanKind.PRODUCTS_ONLY, null, needUnits, need.budget()));

        plans.sort(Comparator.comparingLong(TripAdvisorService::rank).thenComparingLong(TripPlan::total));

        return new TripAdvice(template.label(), need.people(), need.budget(),
                plans.stream().limit(MAX_PLANS).toList(), minimumBudget, unavailableRequired, owned);
    }

    /** Khoá xếp hạng: nhỏ hơn = tốt hơn. */
    private static long rank(TripPlan p) {
        long r = 0;
        r += p.coversRequired() ? 0 : 1_000_000;
        r += p.kind() == PlanKind.COMBO_PLUS_PRODUCTS ? 0 : 100_000;
        r += p.skippedOptional().size() * 1_000L;
        r -= p.comboCovers().size() * 10L;
        return r;
    }

    private List<Option> candidates(ResolvedSlot slot) {
        if (slot.paths().isEmpty()) {
            return List.of();
        }
        List<ProductDetail> products = catalog.searchProducts(new ProductFilter(null, null, slot.paths(),
                slot.excludePaths(), null, null, null, true, ProductSort.PRICE_ASC, CANDIDATES_PER_SLOT));
        return products.stream().filter(p -> slot.acceptsName(p.card().name()))
                .map(p -> new Option(p, slot.slot().mode() == QuantityMode.CAPACITY
                ? CapacityParser.capacity(p.card().name(), p.specs()).orElse(ComboFitAnalyzer.DEFAULT_TENT_CAPACITY)
                : 1)).toList();
    }

    /** Sản phẩm ứng viên cho một slot; `unitCapacity` chỉ có nghĩa với slot CAPACITY (lều). */
    record Option(ProductDetail product, int unitCapacity) {

        /** Số cái cần mua để đủ `units` (CAPACITY: tổng sức chứa). */
        int quantityFor(int units, QuantityMode mode) {
            return mode == QuantityMode.CAPACITY ? (int) Math.ceil(units / (double) unitCapacity) : units;
        }

        boolean inStockFor(int quantity) {
            return product.card().availableQuantity() >= quantity;
        }
    }

    /** Lựa chọn cụ thể cho một slot: mua `quantity` cái của `option`. */
    record Choice(ResolvedSlot slot, Option option, int quantity) {
        long cost() {
            return option.product().card().price() * quantity;
        }
    }

    /** Ghép combo + sản phẩm lẻ cho một bộ nhu cầu. */
    static final class Planner {
        private final List<ResolvedSlot> slots;
        private final Map<String, List<Option>> options;
        private final boolean winter;

        Planner(List<ResolvedSlot> slots, Map<String, List<Option>> options, boolean winter) {
            this.slots = slots;
            this.options = options;
            this.winter = winter;
        }

        /** Tổng tiền rẻ nhất cho đủ các slot bắt buộc còn thiếu; null nếu có slot bắt buộc hết hàng. */
        Long minimumRequiredCost(Map<String, Integer> units) {
            long sum = 0;
            for (ResolvedSlot s : slots) {
                int u = units.getOrDefault(s.key(), 0);
                if (!s.slot().required() || u <= 0) {
                    continue;
                }
                List<Choice> choices = choices(s, u);
                if (choices.isEmpty()) {
                    return null;
                }
                sum += choices.get(0).cost();
            }
            return sum;
        }

        TripPlan fill(PlanKind kind, ComboFit fit, Map<String, Integer> units, long budget) {
            long comboPrice = fit == null ? 0 : fit.combo().price();
            long left = budget - comboPrice;
            List<PlanLine> lines = new ArrayList<>();
            List<String> missingRequired = new ArrayList<>();
            List<String> skippedOptional = new ArrayList<>();

            // 1) Slot bắt buộc: luôn giữ đủ tiền tối thiểu cho các slot bắt buộc còn lại
            List<ResolvedSlot> required = byWeight(true, units);
            long minRequired = 0;
            Map<String, List<Choice>> requiredChoices = new LinkedHashMap<>();
            for (ResolvedSlot s : required) {
                List<Choice> c = choices(s, units.get(s.key()));
                requiredChoices.put(s.key(), c);
                if (!c.isEmpty()) {
                    minRequired += c.get(0).cost();
                }
            }
            long extra = left - minRequired;
            int weightLeft = required.stream().mapToInt(s -> s.slot().weight()).sum();
            for (ResolvedSlot s : required) {
                List<Choice> c = requiredChoices.get(s.key());
                int w = s.slot().weight();
                if (c.isEmpty()) {
                    missingRequired.add(s.label() + " (hết hàng)");
                    weightLeft -= w;
                    continue;
                }
                long cheapest = c.get(0).cost();
                Choice chosen;
                if (extra >= 0) {
                    long allowance = cheapest + (weightLeft > 0 ? extra * w / weightLeft : 0);
                    chosen = bestWithin(c, allowance);
                    extra -= chosen.cost() - cheapest;
                } else if (cheapest <= left) {
                    // Không đủ tiền cho mọi slot bắt buộc: mua rẻ nhất cho slot quan trọng trước
                    chosen = c.get(0);
                } else {
                    missingRequired.add(s.label());
                    weightLeft -= w;
                    continue;
                }
                weightLeft -= w;
                left -= chosen.cost();
                lines.add(line(chosen, units.get(s.key()), fit));
            }

            // 2) Slot nên có: mua nếu còn tiền
            List<ResolvedSlot> optional = byWeight(false, units);
            int optionalWeight = optional.stream().mapToInt(s -> s.slot().weight()).sum();
            for (ResolvedSlot s : optional) {
                List<Choice> c = choices(s, units.get(s.key()));
                int w = s.slot().weight();
                if (c.isEmpty() || c.get(0).cost() > left) {
                    skippedOptional.add(s.label());
                    optionalWeight -= w;
                    continue;
                }
                long share = optionalWeight > 0 ? left * w / optionalWeight : left;
                Choice chosen = bestWithin(c, Math.max(share, c.get(0).cost()));
                optionalWeight -= w;
                left -= chosen.cost();
                lines.add(line(chosen, units.get(s.key()), fit));
            }

            long total = comboPrice + lines.stream().mapToLong(PlanLine::lineTotal).sum();
            List<String> comboCovers = fit == null ? List.of() : slots.stream()
                    .filter(s -> fit.coveredUnits().containsKey(s.key())).map(ResolvedSlot::label).toList();
            return new TripPlan(kind, fit == null ? null : fit.combo(), comboCovers,
                    fit == null ? null : fit.estimatedPeople(), lines, total, budget - total, missingRequired,
                    skippedOptional);
        }

        private List<ResolvedSlot> byWeight(boolean required, Map<String, Integer> units) {
            return slots.stream()
                    .filter(s -> s.slot().required() == required && units.getOrDefault(s.key(), 0) > 0)
                    .sorted(Comparator.comparingInt((ResolvedSlot s) -> s.slot().weight()).reversed())
                    .toList();
        }

        /** Các cách mua đủ `units` cho slot, rẻ nhất trước. Đi mùa đông: chỉ lấy đồ mùa đông nếu có. */
        List<Choice> choices(ResolvedSlot s, int units) {
            List<Option> all = options.getOrDefault(s.key(), List.of());
            if (winter && !s.winterPaths().isEmpty()) {
                List<Option> winterOnly = all.stream()
                        .filter(o -> s.isWinter(o.product().card().categoryPath())).toList();
                if (!winterOnly.isEmpty()) {
                    all = winterOnly;
                }
            }
            QuantityMode mode = s.slot().mode();
            return all.stream()
                    .map(o -> new Choice(s, o, o.quantityFor(units, mode)))
                    .filter(c -> c.quantity() > 0 && c.option().inStockFor(c.quantity()))
                    .sorted(Comparator.comparingLong(Choice::cost))
                    .toList();
        }

        /** Món đắt nhất không vượt `allowance` (đồ tốt hơn trong tầm tiền); không có thì rẻ nhất. */
        private static Choice bestWithin(List<Choice> sortedByCost, long allowance) {
            Choice best = sortedByCost.get(0);
            for (Choice c : sortedByCost) {
                if (c.cost() <= allowance) {
                    best = c;
                }
            }
            return best;
        }

        private static PlanLine line(Choice c, int units, ComboFit fit) {
            String reason;
            if (c.slot().slot().mode() == QuantityMode.CAPACITY) {
                reason = c.quantity() + " lều × " + c.option().unitCapacity() + " người, đủ chỗ cho " + units
                        + " người" + (fit != null ? " còn lại" : "");
            } else if (fit != null && fit.coveredUnits().containsKey(c.slot().key())) {
                reason = "Combo đã có " + fit.coveredUnits().get(c.slot().key()) + ", mua thêm " + c.quantity();
            } else {
                reason = (c.slot().slot().required() ? "Cần có" : "Nên có") + ": " + c.slot().label().toLowerCase()
                        + " × " + c.quantity() + " (" + TextUtil.formatVnd(c.option().product().card().price())
                        + "/cái)";
            }
            return new PlanLine(c.slot().key(), c.slot().label(), c.option().product().card(), c.quantity(),
                    c.cost(), reason);
        }
    }
}
