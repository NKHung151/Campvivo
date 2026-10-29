package vn.campvivo.chatbot.advisor;

import org.springframework.stereotype.Component;
import vn.campvivo.chatbot.advisor.AdvisorModels.ComboFit;
import vn.campvivo.chatbot.advisor.TripTemplateProperties.QuantityMode;
import vn.campvivo.chatbot.catalog.CatalogModels.ComboDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.ComboItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đánh giá combo admin dựng trong DB ngay lúc khách hỏi (phương án A — D-28): không lưu thông tin phụ nào về combo.
 * Đọc thành phần (danh mục, số lượng, tên/thông số) để biết combo phủ được slot nào của mẫu nhu cầu, cho mấy người.
 */
@Component
public class ComboFitAnalyzer {

    /** Lều không ghi sức chứa: coi như 2 người (loại phổ biến nhất). */
    static final int DEFAULT_TENT_CAPACITY = 2;

    public ComboFit analyze(ComboDetail combo, List<ResolvedSlot> slots) {
        Map<String, Integer> covered = new LinkedHashMap<>();
        boolean winter = combo.descriptionText().toLowerCase().contains("mùa đông")
                || combo.name().toLowerCase().contains("mùa đông");
        for (ResolvedSlot slot : slots) {
            int units = 0;
            for (ComboItem item : combo.items()) {
                if (!slot.matches(item.categoryPath())) {
                    continue;
                }
                units += slot.slot().mode() == QuantityMode.CAPACITY
                        ? item.quantity() * tentCapacity(item)
                        : item.quantity();
                if (slot.isWinter(item.categoryPath())) {
                    winter = true;
                }
            }
            if (units > 0) {
                covered.put(slot.key(), units);
            }
        }
        boolean relevant = slots.stream().anyMatch(s -> s.slot().required() && covered.containsKey(s.key()));
        return new ComboFit(combo, covered, estimatePeople(slots, covered), winter, relevant);
    }

    /** Số người combo đủ dùng = nhỏ nhất qua các slot theo đầu người/sức chứa mà combo có. */
    static Integer estimatePeople(List<ResolvedSlot> slots, Map<String, Integer> covered) {
        Integer people = null;
        for (ResolvedSlot slot : slots) {
            Integer units = covered.get(slot.key());
            if (units == null) {
                continue;
            }
            Integer forSlot = switch (slot.slot().mode()) {
                case PER_PERSON -> units / Math.max(1, slot.slot().amount());
                case CAPACITY -> units;
                case PER_GROUP -> null;
            };
            if (forSlot != null) {
                people = people == null ? forSlot : Math.min(people, forSlot);
            }
        }
        return people;
    }

    static int tentCapacity(ComboItem item) {
        return CapacityParser.capacity(item.productName(), item.specs()).orElse(DEFAULT_TENT_CAPACITY);
    }
}
