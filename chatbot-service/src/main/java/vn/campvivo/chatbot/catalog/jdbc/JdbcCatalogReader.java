package vn.campvivo.chatbot.catalog.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.campvivo.chatbot.catalog.CatalogModels.CategoryNode;
import vn.campvivo.chatbot.catalog.CatalogModels.ComboDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.ComboItem;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductCard;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductFilter;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductSort;
import vn.campvivo.chatbot.catalog.CatalogModels.Spec;
import vn.campvivo.chatbot.catalog.CatalogReadPort;
import vn.campvivo.chatbot.common.util.PgVector;
import vn.campvivo.chatbot.common.util.TextUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Đọc catalog bằng SQL trên DB dùng chung (D-28) — chỉ SELECT, query cố định, tham số bind (không ghép chuỗi giá trị).
 * Schema theo docs/02-ky-thuat/database.md 3.2–3.6; đổi tên cột ở đó thì JdbcCatalogReaderIT báo lỗi.
 * <p>Xếp hạng khi có câu tìm: 0.7 × độ giống embedding (chatbot_item_embeddings) + 0.3 × word_similarity của chữ
 * đã bỏ dấu (pg_trgm + unaccent). Chưa có embedding thì chỉ dùng chữ và lọc bớt kết quả không liên quan.
 */
@Repository
public class JdbcCatalogReader implements CatalogReadPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcCatalogReader.class);
    private static final TypeReference<List<Spec>> SPECS = new TypeReference<>() { };
    /** Ngưỡng word_similarity khi chỉ tìm bằng chữ (không có embedding). */
    private static final double TEXT_ONLY_MIN_SIMILARITY = 0.3;
    private static final int DESCRIPTION_MAX_CHARS = 600;

    private static final String PRODUCT_COLUMNS = """
            p.id, p.slug, p.name, p.brand, p.price, p.short_description, p.description, p.specs::text AS specs,
            c.name AS category_name, c.slug AS category_slug, c.path AS category_path,
            p.stock_quantity - p.reserved_quantity AS available,
            (SELECT i.image_url FROM images i WHERE i.product_id = p.id
              ORDER BY i.is_primary DESC, i.display_order, i.id LIMIT 1) AS image_url
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcCatalogReader(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<CategoryNode> categories() {
        return jdbc.query("SELECT id, parent_id, name, slug, path FROM categories ORDER BY path",
                (rs, i) -> new CategoryNode(rs.getLong("id"), (Long) rs.getObject("parent_id"),
                        rs.getString("name"), rs.getString("slug"), rs.getString("path")));
    }

    @Override
    public List<ProductDetail> searchProducts(ProductFilter f) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" WHERE p.is_active");
        String join = "";
        String score = null;

        boolean hasText = f.text() != null && !f.text().isBlank();
        boolean hasVector = f.queryEmbedding() != null;
        if (hasText) {
            params.addValue("text", f.text().trim());
        }
        String textScore = "word_similarity(unaccent(lower(:text)), "
                + "unaccent(lower(p.name || ' ' || coalesce(p.brand, '') || ' ' || c.name)))";
        if (hasVector) {
            join = " LEFT JOIN chatbot_item_embeddings e ON e.item_type = 'PRODUCT' AND e.item_id = p.id";
            params.addValue("qvec", PgVector.toLiteral(f.queryEmbedding()));
            String vectorScore = "coalesce(1 - (e.embedding <=> CAST(:qvec AS vector)), 0)";
            score = hasText ? "0.7 * " + vectorScore + " + 0.3 * " + textScore : vectorScore;
        } else if (hasText) {
            score = textScore;
            where.append(" AND ").append(textScore).append(" >= ").append(TEXT_ONLY_MIN_SIMILARITY);
        }

        appendPathFilter(where, params, "c.path", f.categoryPaths(), "cp", false);
        appendPathFilter(where, params, "c.path", f.excludePaths(), "xp", true);
        if (f.brand() != null && !f.brand().isBlank()) {
            where.append(" AND lower(p.brand) = lower(:brand)");
            params.addValue("brand", f.brand().trim());
        }
        if (f.minPrice() != null) {
            where.append(" AND p.price >= :minPrice");
            params.addValue("minPrice", f.minPrice());
        }
        if (f.maxPrice() != null) {
            where.append(" AND p.price <= :maxPrice");
            params.addValue("maxPrice", f.maxPrice());
        }
        if (f.inStockOnly()) {
            where.append(" AND p.stock_quantity - p.reserved_quantity > 0");
        }

        String order = switch (f.sort() == null ? ProductSort.RELEVANCE : f.sort()) {
            case PRICE_ASC -> "p.price ASC, p.id";
            case PRICE_DESC -> "p.price DESC, p.id";
            case NEWEST -> "p.created_at DESC, p.id";
            case RELEVANCE -> score != null ? "score DESC, p.id" : "p.created_at DESC, p.id";
        };
        params.addValue("limit", Math.max(1, f.limit()));

        String sql = "SELECT " + PRODUCT_COLUMNS + (score != null ? ", " + score + " AS score" : "")
                + " FROM products p JOIN categories c ON c.id = p.category_id" + join
                + where + " ORDER BY " + order + " LIMIT :limit";
        return jdbc.query(sql, params, (rs, i) -> mapProduct(rs));
    }

    @Override
    public Optional<ProductDetail> findProduct(String slug) {
        return findProducts(List.of(slug)).stream().findFirst();
    }

    @Override
    public List<ProductDetail> findProducts(List<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT " + PRODUCT_COLUMNS
                + " FROM products p JOIN categories c ON c.id = p.category_id"
                + " WHERE p.is_active AND p.slug IN (:slugs)";
        List<ProductDetail> found = jdbc.query(sql, new MapSqlParameterSource("slugs", slugs),
                (rs, i) -> mapProduct(rs));
        // Giữ thứ tự slug được hỏi
        Map<String, ProductDetail> bySlug = new LinkedHashMap<>();
        found.forEach(p -> bySlug.put(p.card().slug(), p));
        return slugs.stream().map(bySlug::get).filter(p -> p != null).distinct().toList();
    }

    @Override
    public List<ComboDetail> searchCombos(String text, float[] queryEmbedding, Long maxPrice, boolean inStockOnly,
                                          int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" WHERE pc.is_active");
        String join = "";
        String score = null;
        boolean hasText = text != null && !text.isBlank();
        if (hasText) {
            params.addValue("text", text.trim());
        }
        String textScore = "word_similarity(unaccent(lower(:text)), unaccent(lower(pc.name || ' ' || c.name)))";
        if (queryEmbedding != null) {
            join = " LEFT JOIN chatbot_item_embeddings e ON e.item_type = 'COMBO' AND e.item_id = pc.id";
            params.addValue("qvec", PgVector.toLiteral(queryEmbedding));
            String vectorScore = "coalesce(1 - (e.embedding <=> CAST(:qvec AS vector)), 0)";
            score = hasText ? "0.7 * " + vectorScore + " + 0.3 * " + textScore : vectorScore;
        } else if (hasText) {
            score = textScore;
        }
        if (maxPrice != null) {
            where.append(" AND pc.combo_price <= :maxPrice");
            params.addValue("maxPrice", maxPrice);
        }
        if (inStockOnly) {
            where.append(" AND pc.stock_quantity - pc.reserved_quantity > 0");
        }
        params.addValue("limit", Math.max(1, limit));
        String sql = "SELECT pc.id" + (score != null ? ", " + score + " AS score" : "")
                + " FROM product_combos pc JOIN categories c ON c.id = pc.category_id" + join + where
                + " ORDER BY " + (score != null ? "score DESC, " : "") + "pc.created_at DESC, pc.id LIMIT :limit";
        List<Long> ids = jdbc.queryForList(sql, params).stream()
                .map(row -> ((Number) row.get("id")).longValue()).toList();
        return loadCombos(ids);
    }

    @Override
    public Optional<ComboDetail> findCombo(String slug) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM product_combos WHERE is_active AND slug = :slug",
                new MapSqlParameterSource("slug", slug), Long.class);
        return loadCombos(ids).stream().findFirst();
    }

    @Override
    public List<ProductDetail> allActiveProducts() {
        return jdbc.query("SELECT " + PRODUCT_COLUMNS
                        + " FROM products p JOIN categories c ON c.id = p.category_id WHERE p.is_active ORDER BY p.id",
                (rs, i) -> mapProduct(rs));
    }

    @Override
    public List<ComboDetail> allActiveCombos() {
        return loadCombos(jdbc.queryForList("SELECT id FROM product_combos WHERE is_active ORDER BY id",
                new MapSqlParameterSource(), Long.class));
    }

    /** Nạp combo + thành phần theo đúng thứ tự `ids`. */
    private List<ComboDetail> loadCombos(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", ids);
        Map<Long, List<ComboItem>> items = new LinkedHashMap<>();
        jdbc.query("""
                SELECT ci.combo_id, p.id, p.slug, p.name, p.price, p.stock_quantity - p.reserved_quantity AS available,
                       ci.quantity, p.specs::text AS specs, c.slug AS category_slug, c.path AS category_path
                FROM combo_items ci
                JOIN products p ON p.id = ci.product_id
                JOIN categories c ON c.id = p.category_id
                WHERE ci.combo_id IN (:ids)
                ORDER BY ci.combo_id, ci.id
                """, params, rs -> {
            items.computeIfAbsent(rs.getLong("combo_id"), k -> new ArrayList<>()).add(new ComboItem(
                    rs.getLong("id"), rs.getString("slug"), rs.getString("name"), rs.getString("category_slug"),
                    rs.getString("category_path"), rs.getBigDecimal("price").longValue(), rs.getInt("available"),
                    rs.getInt("quantity"), parseSpecs(rs.getString("specs"))));
        });
        Map<Long, ComboDetail> byId = new LinkedHashMap<>();
        jdbc.query("""
                SELECT pc.id, pc.slug, pc.name, pc.combo_price, pc.description, c.name AS category_name,
                       c.slug AS category_slug, pc.stock_quantity - pc.reserved_quantity AS available,
                       coalesce(
                         (SELECT i.image_url FROM images i WHERE i.combo_id = pc.id
                           ORDER BY i.is_primary DESC, i.display_order, i.id LIMIT 1),
                         (SELECT i.image_url FROM combo_items ci JOIN images i ON i.product_id = ci.product_id
                           AND i.is_primary WHERE ci.combo_id = pc.id ORDER BY ci.id LIMIT 1)) AS image_url
                FROM product_combos pc JOIN categories c ON c.id = pc.category_id
                WHERE pc.id IN (:ids)
                """, params, rs -> {
            long id = rs.getLong("id");
            byId.put(id, new ComboDetail(id, rs.getString("slug"), rs.getString("name"),
                    rs.getBigDecimal("combo_price").longValue(), rs.getString("category_name"),
                    rs.getString("category_slug"), rs.getInt("available"), rs.getString("image_url"),
                    TextUtil.truncate(TextUtil.htmlToText(rs.getString("description")), DESCRIPTION_MAX_CHARS),
                    items.getOrDefault(id, List.of())));
        });
        return ids.stream().map(byId::get).filter(c -> c != null).toList();
    }

    private ProductDetail mapProduct(ResultSet rs) throws SQLException {
        ProductCard card = new ProductCard(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
                rs.getString("brand"), rs.getBigDecimal("price").longValue(), rs.getString("category_name"),
                rs.getString("category_slug"), rs.getString("category_path"), rs.getInt("available"),
                rs.getString("image_url"), rs.getString("short_description"));
        return new ProductDetail(card, parseSpecs(rs.getString("specs")),
                TextUtil.truncate(TextUtil.htmlToText(rs.getString("description")), DESCRIPTION_MAX_CHARS));
    }

    private List<Spec> parseSpecs(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, SPECS);
        } catch (Exception e) {
            log.warn("Không đọc được specs: {}", e.getMessage());
            return List.of();
        }
    }

    /** `(col LIKE :cp0 OR col LIKE :cp1 ...)` — giá trị là tiền tố path + '%'. Path chỉ gồm số và '/'. */
    private static void appendPathFilter(StringBuilder where, MapSqlParameterSource params, String column,
                                         List<String> prefixes, String name, boolean negate) {
        if (prefixes == null || prefixes.isEmpty()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < prefixes.size(); i++) {
            parts.add(column + " LIKE :" + name + i);
            params.addValue(name + i, prefixes.get(i) + "%");
        }
        where.append(negate ? " AND NOT (" : " AND (").append(String.join(" OR ", parts)).append(")");
    }
}
