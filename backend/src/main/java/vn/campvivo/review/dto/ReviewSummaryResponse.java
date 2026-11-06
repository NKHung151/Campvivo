package vn.campvivo.review.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * `ReviewSummary` (api.md mục 4.3, D-32). `averageRating` làm tròn 1 chữ số (cùng cách `ROUND(AVG(rating), 1)` của
 * `@Formula` — D-35); null khi chưa có đánh giá. `ratingCounts` luôn đủ 5 khoá 1..5.
 */
public record ReviewSummaryResponse(Double averageRating, long reviewCount, Map<Integer, Long> ratingCounts) {

    /** Từ các dòng [rating, count] của {@code ReviewRepository.countByRatingFor*}. */
    public static ReviewSummaryResponse fromCounts(List<Object[]> rows) {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (int star = 1; star <= 5; star++) {
            counts.put(star, 0L);
        }
        long total = 0;
        long sum = 0;
        for (Object[] row : rows) {
            int star = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            counts.put(star, count);
            total += count;
            sum += star * count;
        }
        Double average = total == 0 ? null
                : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP).doubleValue();
        return new ReviewSummaryResponse(average, total, counts);
    }
}
