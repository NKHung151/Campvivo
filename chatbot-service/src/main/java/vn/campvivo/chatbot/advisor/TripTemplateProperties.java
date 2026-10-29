package vn.campvivo.chatbot.advisor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Map;

/**
 * Mẫu nhu cầu theo loại chuyến — `chatbot/trip-templates.yml` (giải thích từng trường ở đầu file đó).
 */
@ConfigurationProperties("app.trip")
public record TripTemplateProperties(Map<TripType, TripTemplate> templates) {

    public TripTemplateProperties(Map<TripType, TripTemplate> templates) {
        this.templates = templates == null ? Map.of() : templates;
    }

    public TripTemplate get(TripType type) {
        TripTemplate t = templates.get(type);
        if (t == null) {
            throw new IllegalStateException("Thiếu mẫu nhu cầu cho " + type + " trong chatbot/trip-templates.yml");
        }
        return t;
    }

    public record TripTemplate(String label, List<Slot> slots) {}

    public enum QuantityMode { PER_PERSON, PER_GROUP, CAPACITY }

    /**
     * `nameKeywords`: nếu có, sản phẩm lẻ đề xuất cho slot phải có một từ khoá trong tên (chống sản phẩm bị gắn nhầm
     * danh mục). Không áp cho thành phần combo — admin đã chọn.
     */
    public record Slot(String key, String label, List<String> categories,
                       @DefaultValue List<String> exclude, @DefaultValue List<String> winterCategories,
                       boolean required, QuantityMode mode, @DefaultValue("1") int amount,
                       @DefaultValue("4") int groupSize, @DefaultValue("10") int weight,
                       @DefaultValue List<String> aliases, @DefaultValue List<String> nameKeywords) {

        /** Số đơn vị cần (CAPACITY: tổng sức chứa cần). */
        public int needFor(int people) {
            return switch (mode) {
                case PER_PERSON -> people * amount;
                case PER_GROUP -> (int) Math.ceil(people / (double) groupSize) * amount;
                case CAPACITY -> people;
            };
        }
    }
}
