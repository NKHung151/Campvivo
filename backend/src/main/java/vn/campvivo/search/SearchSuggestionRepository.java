package vn.campvivo.search;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Truy vấn SQL cho gợi ý khi gõ và sửa lỗi gõ sai (PostgreSQL `unaccent` + `pg_trgm`, migration V14). Tách riêng và dùng SQL thuần vì
 * cần hàm `f_unaccent`, `word_similarity`, `similarity` mà JPA không có; chỉ ĐỌC, mọi giá trị đi qua tham số (không nối chuỗi vào SQL).
 * Không đụng entity hay repository của module khác: chỉ đọc các bảng công khai (`products`, `product_combos`, `articles`, `images`).
 */
@Repository
class SearchSuggestionRepository {

    record Hit(String type, String name, String slug, String imageUrl) {
    }

    /** Từ gần giống nhất trong kho từ: `folded` là dạng không dấu, `original` là dạng gốc (có dấu, viết thường). */
    record ClosestWord(String folded, String original, double similarity) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    SearchSuggestionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Sản phẩm và combo ĐANG BÁN khớp `folded` (đã bỏ dấu, viết thường): tên bắt đầu bằng từ khoá (hạng 0), tên chứa từ khoá (1),
     * thương hiệu chứa từ khoá (2), rồi — chỉ khi `fuzzy` — tên gần giống từ khoá (`word_similarity` ≥ `threshold`, hạng 3).
     */
    List<Hit> suggest(String folded, boolean fuzzy, double threshold, int limit) {
        String escaped = SearchText.escapeLike(folded);
        String sql = """
                WITH cand AS (
                    SELECT 'PRODUCT' AS type, p.id, p.name, p.slug,
                           f_unaccent(lower(p.name)) AS norm, f_unaccent(lower(coalesce(p.brand, ''))) AS brand_norm
                    FROM products p WHERE p.is_active
                    UNION ALL
                    SELECT 'COMBO', c.id, c.name, c.slug, f_unaccent(lower(c.name)), ''
                    FROM product_combos c WHERE c.is_active
                ), scored AS (
                    SELECT type, id, name, slug,
                           CASE WHEN norm LIKE :prefix ESCAPE '\\' THEN 0
                                WHEN norm LIKE :contains ESCAPE '\\' THEN 1
                                WHEN brand_norm LIKE :contains ESCAPE '\\' THEN 2
                                ELSE 3 END AS rank,
                           word_similarity(:q, norm) AS sim
                    FROM cand
                )
                SELECT s.type, s.name, s.slug,
                       COALESCE(
                           (SELECT i.image_url FROM images i
                            WHERE (s.type = 'PRODUCT' AND i.product_id = s.id) OR (s.type = 'COMBO' AND i.combo_id = s.id)
                            ORDER BY i.is_primary DESC, i.display_order, i.id LIMIT 1),
                           (SELECT i.image_url FROM combo_items ci JOIN images i ON i.product_id = ci.product_id
                            WHERE s.type = 'COMBO' AND ci.combo_id = s.id
                            ORDER BY i.is_primary DESC, ci.id, i.display_order, i.id LIMIT 1)
                       ) AS image_url
                FROM scored s
                WHERE s.rank < 3 OR (:fuzzy AND s.sim >= :threshold)
                ORDER BY s.rank, s.sim DESC, s.name, s.id
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("q", folded)
                .addValue("prefix", escaped + "%")
                .addValue("contains", "%" + escaped + "%")
                .addValue("fuzzy", fuzzy)
                .addValue("threshold", threshold)
                .addValue("limit", limit);
        return jdbc.query(sql, params, (rs, i) -> new Hit(rs.getString("type"), rs.getString("name"), rs.getString("slug"),
                rs.getString("image_url")));
    }

    /**
     * Từ trong kho từ (tên và thương hiệu hàng đang bán, tên combo đang bán, tiêu đề bài đã xuất bản) giống `foldedWord` nhất theo
     * `similarity` (trigram), hoặc rỗng nếu chưa có từ nào đạt `threshold`. Từ ngắn hơn 3 ký tự không nằm trong kho.
     */
    Optional<ClosestWord> closestWord(String foldedWord, double threshold) {
        String sql = """
                WITH src AS (
                    SELECT name AS t FROM products WHERE is_active
                    UNION ALL SELECT brand FROM products WHERE is_active AND brand IS NOT NULL
                    UNION ALL SELECT name FROM product_combos WHERE is_active
                    UNION ALL SELECT title FROM articles WHERE is_published
                ), words AS (
                    SELECT lower(w) AS orig, f_unaccent(lower(w)) AS folded
                    FROM (SELECT regexp_split_to_table(t, '[[:space:]_.,:;()/"''!?+&|-]+') AS w FROM src) x
                    WHERE length(w) >= 3
                ), vocab AS (
                    SELECT folded, (array_agg(orig ORDER BY orig))[1] AS orig, count(*) AS c FROM words GROUP BY folded
                )
                SELECT folded, orig, similarity(folded, :w) AS sim FROM vocab
                WHERE similarity(folded, :w) >= :threshold
                ORDER BY sim DESC, c DESC, folded
                LIMIT 1
                """;
        var params = new MapSqlParameterSource().addValue("w", foldedWord).addValue("threshold", threshold);
        return jdbc.query(sql, params, (rs, i) -> new ClosestWord(rs.getString("folded"), rs.getString("orig"), rs.getDouble("sim")))
                .stream().findFirst();
    }
}
