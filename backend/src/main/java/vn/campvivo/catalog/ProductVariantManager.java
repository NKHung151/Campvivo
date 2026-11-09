package vn.campvivo.catalog;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Đồng bộ tuỳ chọn + biến thể của sản phẩm từ form admin (D-37).
 * <ul>
 *   <li>Tối đa 2 tuỳ chọn; server sinh đủ mọi tổ hợp giá trị (tối đa {@value #MAX_VARIANTS} biến thể).</li>
 *   <li>Tuỳ chọn / giá trị nhận diện theo id → đổi tên vẫn giữ biến thể và tồn kho.</li>
 *   <li>Thêm tuỳ chọn mới: biến thể cũ chuyển sang giá trị đầu tiên của tuỳ chọn đó (mang theo tồn kho).</li>
 *   <li>Bỏ tuỳ chọn / giá trị: biến thể không còn tổ hợp bị xoá — chỉ được khi không còn tồn kho, chưa dùng trong
 *       combo và chưa có lịch sử kho; nếu không thì phải tắt bán thay vì xoá.</li>
 *   <li>Tuỳ chọn đã có giữ nguyên thứ tự và đứng trước tuỳ chọn mới (không hoán đổi vị trí 1 ↔ 2).</li>
 * </ul>
 * Ghi xuống DB chia nhiều lần flush để không vướng unique / khoá ngoại ở trạng thái trung gian.
 */
@Component
public class ProductVariantManager {

    public static final int MAX_VARIANTS = 100;

    public record OptionInput(Long id, String name, List<ValueInput> values) {}

    public record ValueInput(Long id, String value) {}

    public record VariantInput(List<String> values, String sku, Boolean active) {}

    private final EntityManager entityManager;

    public ProductVariantManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Gọi sau khi sản phẩm đã được lưu (có id). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sync(Product product, List<OptionInput> rawOptions, List<VariantInput> variantInputs,
                     OffsetDateTime now) {
        List<OptionInput> options = normalizeOptions(product, rawOptions == null ? List.of() : rawOptions);
        int combinations = options.stream().mapToInt(o -> o.values().size()).reduce(1, (a, b) -> a * b);
        if (combinations > MAX_VARIANTS) {
            throw fieldError("options", "Tối đa " + MAX_VARIANTS + " biến thể (hiện " + combinations + " tổ hợp).");
        }
        Map<String, VariantInput> settings = variantSettings(options, variantInputs == null ? List.of() : variantInputs);
        // Biến thể duy nhất lấy lại tồn từ sản phẩm (đơn hàng trừ thẳng tổng) trước khi tách/gộp tổ hợp
        product.getVariants().forEach(ProductVariant::syncFromProduct);

        // 1. Mỗi biến thể hiện có → tổ hợp đích (chỉ số giá trị theo từng tuỳ chọn mới). Hai biến thể cùng đích
        //    (khi bỏ một tuỳ chọn) → giữ cái còn tồn kho / cũ nhất, cái kia bị xoá.
        Map<ProductVariant, List<Integer>> targets = new LinkedHashMap<>();
        Set<String> claimed = new HashSet<>();
        List<ProductVariant> dropped = new ArrayList<>();
        List<ProductVariant> existing = product.getVariants().stream()
                .sorted(Comparator.comparing((ProductVariant v) -> !v.hasStock())
                        .thenComparing(v -> v.getId() == null ? Long.MAX_VALUE : v.getId()))
                .toList();
        for (ProductVariant v : existing) {
            List<Integer> target = targetOf(v, options);
            if (target == null || !claimed.add(key(target))) {
                dropped.add(v);
            } else {
                targets.put(v, target);
            }
        }
        checkDeletable(dropped);
        checkSkus(product, settings);

        // 2. Xoá biến thể không còn tổ hợp
        product.getVariants().removeAll(dropped);
        entityManager.flush();

        // 3. Trỏ biến thể còn lại sang giá trị đã có (để trống chỗ của tuỳ chọn mới) — rời khỏi tuỳ chọn sắp xoá
        Map<Long, ProductOptionValue> valuesById = product.getOptions().stream()
                .flatMap(o -> o.getValues().stream())
                .collect(Collectors.toMap(ProductOptionValue::getId, Function.identity()));
        targets.forEach((v, target) -> {
            List<ProductOptionValue> refs = new ArrayList<>();
            for (int k = 0; k < options.size(); k++) {
                Long valueId = options.get(k).values().get(target.get(k)).id();
                refs.add(valueId == null ? null : valuesById.get(valueId));
            }
            assignIfChanged(v, refs, now);
        });
        entityManager.flush();

        // 4. Xoá tuỳ chọn / giá trị bị bỏ
        Set<Long> keptOptionIds = options.stream().map(OptionInput::id).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        product.getOptions().removeIf(o -> !keptOptionIds.contains(o.getId()));
        Map<Long, ProductOption> optionsById = product.getOptions().stream()
                .collect(Collectors.toMap(ProductOption::getId, Function.identity()));
        for (OptionInput in : options) {
            if (in.id() != null) {
                Set<Long> keptValueIds = in.values().stream().map(ValueInput::id).filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                optionsById.get(in.id()).getValues().removeIf(val -> !keptValueIds.contains(val.getId()));
            }
        }
        entityManager.flush();

        // 5. Đổi tên / vị trí cái còn lại, thêm giá trị và tuỳ chọn mới
        List<List<ProductOptionValue>> finalValues = new ArrayList<>();
        for (int k = 0; k < options.size(); k++) {
            OptionInput in = options.get(k);
            ProductOption option = in.id() == null ? product.addOption(in.name(), k + 1) : optionsById.get(in.id());
            option.update(in.name(), k + 1);
            List<ProductOptionValue> values = new ArrayList<>();
            for (int i = 0; i < in.values().size(); i++) {
                ValueInput vi = in.values().get(i);
                ProductOptionValue value = vi.id() == null ? option.addValue(vi.value(), i) : valuesById.get(vi.id());
                value.update(vi.value(), i);
                values.add(value);
            }
            finalValues.add(values);
        }
        entityManager.flush();

        // 6. Gán tổ hợp đầy đủ, sinh biến thể cho tổ hợp còn thiếu, áp SKU / bật-tắt và thứ tự
        Map<String, ProductVariant> byKey = new HashMap<>();
        targets.forEach((v, target) -> {
            assignIfChanged(v, refsOf(target, finalValues), now);
            byKey.put(key(target), v);
        });
        List<List<Integer>> matrix = matrix(options);
        for (int pos = 0; pos < matrix.size(); pos++) {
            List<Integer> combo = matrix.get(pos);
            String k = key(combo);
            ProductVariant v = byKey.get(k);
            if (v == null) {
                List<ProductOptionValue> refs = refsOf(combo, finalValues);
                v = product.addVariant(valueAt(refs, 0), valueAt(refs, 1), now);
            }
            VariantInput s = settings.get(k);
            String sku = s == null ? v.getSku() : blankToNull(s.sku());
            boolean active = s == null || s.active() == null ? v.isActive() : s.active();
            v.update(sku, active, pos, now);
        }
        product.getVariants().forEach(ProductVariant::syncFromProduct);
        product.touch(now);
        entityManager.flush();
    }

    // ---------------------------------------------------------------------------------------------

    private static List<OptionInput> normalizeOptions(Product product, List<OptionInput> raw) {
        Map<Long, ProductOption> current = product.getOptions().stream()
                .collect(Collectors.toMap(ProductOption::getId, Function.identity()));
        Set<String> names = new HashSet<>();
        List<OptionInput> out = new ArrayList<>();
        int lastExistingPosition = 0;
        boolean seenNew = false;
        for (OptionInput o : raw) {
            String name = o.name().trim();
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw fieldError("options", "Tuỳ chọn \"" + name + "\" bị trùng.");
            }
            ProductOption existing = null;
            if (o.id() != null) {
                existing = current.get(o.id());
                if (existing == null) {
                    throw fieldError("options", "Tuỳ chọn \"" + name + "\" không thuộc sản phẩm này.");
                }
                if (seenNew || existing.getPosition() < lastExistingPosition) {
                    throw fieldError("options", "Không đổi được thứ tự các tuỳ chọn đã có — tuỳ chọn mới thêm ở sau.");
                }
                lastExistingPosition = existing.getPosition();
            } else {
                seenNew = true;
            }
            Set<Long> ownValueIds = existing == null ? Set.of()
                    : existing.getValues().stream().map(ProductOptionValue::getId).collect(Collectors.toSet());
            Set<String> seen = new HashSet<>();
            List<ValueInput> values = new ArrayList<>();
            for (ValueInput v : o.values()) {
                String value = v.value().trim();
                if (!seen.add(value.toLowerCase(Locale.ROOT))) {
                    throw fieldError("options", "Giá trị \"" + value + "\" của \"" + name + "\" bị trùng.");
                }
                if (v.id() != null && !ownValueIds.contains(v.id())) {
                    throw fieldError("options", "Giá trị \"" + value + "\" không thuộc tuỳ chọn \"" + name + "\".");
                }
                values.add(new ValueInput(v.id(), value));
            }
            out.add(new OptionInput(o.id(), name, values));
        }
        return out;
    }

    /** SKU / bật-tắt theo tổ hợp, khoá bằng chỉ số giá trị ("0,2"). */
    private static Map<String, VariantInput> variantSettings(List<OptionInput> options, List<VariantInput> inputs) {
        Map<String, VariantInput> out = new HashMap<>();
        for (VariantInput in : inputs) {
            if (in.values().size() != options.size()) {
                throw fieldError("variants", "Biến thể không khớp với các tuỳ chọn.");
            }
            List<Integer> combo = new ArrayList<>();
            for (int k = 0; k < options.size(); k++) {
                String wanted = in.values().get(k).trim().toLowerCase(Locale.ROOT);
                List<ValueInput> values = options.get(k).values();
                int idx = -1;
                for (int i = 0; i < values.size() && idx < 0; i++) {
                    if (values.get(i).value().toLowerCase(Locale.ROOT).equals(wanted)) {
                        idx = i;
                    }
                }
                if (idx < 0) {
                    throw fieldError("variants", "Biến thể có giá trị \"" + in.values().get(k) + "\" không tồn tại.");
                }
                combo.add(idx);
            }
            out.put(key(combo), in);
        }
        return out;
    }

    /** Tổ hợp đích của một biến thể đang có; null nếu giá trị của nó đã bị bỏ. */
    private static List<Integer> targetOf(ProductVariant v, List<OptionInput> options) {
        List<Integer> target = new ArrayList<>();
        for (OptionInput o : options) {
            ProductOptionValue own = o.id() == null ? null : v.getOptionValues().stream()
                    .filter(val -> val.getOption().getId().equals(o.id())).findFirst().orElse(null);
            if (own == null) {
                target.add(0); // tuỳ chọn mới (hoặc biến thể mặc định) → giá trị đầu tiên
                continue;
            }
            int idx = -1;
            for (int i = 0; i < o.values().size() && idx < 0; i++) {
                if (own.getId().equals(o.values().get(i).id())) {
                    idx = i;
                }
            }
            if (idx < 0) {
                return null;
            }
            target.add(idx);
        }
        return target;
    }

    /** Biến thể sắp xoá: không còn tồn kho, chưa dùng trong combo, chưa có lịch sử kho. */
    private void checkDeletable(List<ProductVariant> dropped) {
        for (ProductVariant v : dropped) {
            if (v.hasStock()) {
                throw fieldError("options", "Biến thể \"" + label(v) + "\" còn tồn kho — hãy xuất hết kho hoặc "
                        + "tắt bán thay vì xoá.");
            }
        }
        List<Long> ids = dropped.stream().map(ProductVariant::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Number> used = entityManager.createNativeQuery("""
                        select variant_id from combo_items where variant_id in (:ids)
                        union select variant_id from inventory_logs where variant_id in (:ids)
                        """)
                .setParameter("ids", ids)
                .getResultList();
        if (!used.isEmpty()) {
            long id = used.get(0).longValue();
            ProductVariant v = dropped.stream().filter(d -> Objects.equals(d.getId(), id)).findFirst().orElseThrow();
            throw fieldError("options", "Biến thể \"" + label(v) + "\" đã dùng trong combo hoặc đã có lịch sử kho — "
                    + "hãy tắt bán thay vì xoá.");
        }
    }

    /** SKU không trùng trong sản phẩm và với sản phẩm khác. */
    private void checkSkus(Product product, Map<String, VariantInput> settings) {
        Set<String> skus = new HashSet<>();
        for (VariantInput in : settings.values()) {
            String sku = blankToNull(in.sku());
            if (sku != null && !skus.add(sku)) {
                throw fieldError("variants", "SKU \"" + sku + "\" bị trùng.");
            }
        }
        if (skus.isEmpty()) {
            return;
        }
        List<String> taken = entityManager.createQuery(
                        "select v.sku from ProductVariant v where v.sku in :skus and v.product.id <> :productId",
                        String.class)
                .setParameter("skus", skus)
                .setParameter("productId", product.getId())
                .getResultList();
        if (!taken.isEmpty()) {
            throw fieldError("variants", "SKU \"" + taken.get(0) + "\" đã dùng cho sản phẩm khác.");
        }
    }

    private static void assignIfChanged(ProductVariant v, List<ProductOptionValue> refs, OffsetDateTime now) {
        ProductOptionValue v1 = valueAt(refs, 0);
        ProductOptionValue v2 = valueAt(refs, 1);
        if (v.getOption1Value() != v1 || v.getOption2Value() != v2) {
            v.assign(v1, v2, now);
        }
    }

    private static ProductOptionValue valueAt(List<ProductOptionValue> refs, int i) {
        return refs.size() > i ? refs.get(i) : null;
    }

    private static List<ProductOptionValue> refsOf(List<Integer> combo, List<List<ProductOptionValue>> values) {
        List<ProductOptionValue> refs = new ArrayList<>();
        for (int k = 0; k < combo.size(); k++) {
            refs.add(values.get(k).get(combo.get(k)));
        }
        return refs;
    }

    /** Mọi tổ hợp theo thứ tự hiển thị (tuỳ chọn 1 ngoài, tuỳ chọn 2 trong). */
    private static List<List<Integer>> matrix(List<OptionInput> options) {
        List<List<Integer>> out = List.of(List.of());
        for (OptionInput o : options) {
            List<List<Integer>> next = new ArrayList<>();
            for (List<Integer> prefix : out) {
                for (int i = 0; i < o.values().size(); i++) {
                    List<Integer> c = new ArrayList<>(prefix);
                    c.add(i);
                    next.add(c);
                }
            }
            out = next;
        }
        return out;
    }

    private static String key(List<Integer> combo) {
        return combo.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String label(ProductVariant v) {
        String title = v.getTitle();
        return title == null ? "mặc định" : title;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static BusinessException fieldError(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                Map.of("fieldErrors", Map.of(field, message)));
    }
}
