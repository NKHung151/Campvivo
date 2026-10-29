package vn.campvivo.chatbot.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import vn.campvivo.chatbot.catalog.CatalogModels.CategoryNode;
import vn.campvivo.chatbot.catalog.CatalogModels.ComboDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductFilter;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductSort;
import vn.campvivo.chatbot.catalog.CatalogReadPort;
import vn.campvivo.chatbot.embedding.QueryEmbedder;
import vn.campvivo.chatbot.tools.ToolViews.ComboView;
import vn.campvivo.chatbot.tools.ToolViews.Notice;
import vn.campvivo.chatbot.tools.ToolViews.ProductDetailView;
import vn.campvivo.chatbot.tools.ToolViews.ProductView;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Tool tra cứu sản phẩm/combo trong DB cho model (FR-28). Model chỉ truyền tham số; query cố định ở
 * {@link CatalogReadPort} — không có SQL do model viết. Giá, tồn kho luôn lấy từ DB tại thời điểm hỏi.
 */
@Component
public class CatalogTools {

    private static final int MAX_LIMIT = 8;
    private static final int DEFAULT_LIMIT = 5;

    private final CatalogReadPort catalog;
    private final QueryEmbedder embedder;

    public CatalogTools(CatalogReadPort catalog, QueryEmbedder embedder) {
        this.catalog = catalog;
        this.embedder = embedder;
    }

    public record SearchResult(int count, List<ProductView> products, String notice) {}

    public record CategoryView(String slug, String name, boolean hasChildren) {}

    public record ComboSearchResult(int count, List<ComboView> combos, String notice) {}

    public record CompareResult(List<ProductDetailView> products, List<String> notFound) {}

    @Tool(name = "searchProducts", description = """
            Tìm sản phẩm đang bán trong cửa hàng Campvivo theo nhu cầu, tầm giá, danh mục, thương hiệu. \
            Dùng khi khách hỏi "có ... không", "trong tầm X tiền mua được gì", "gợi ý lều/túi ngủ...". \
            Chỉ trả hàng còn bán; mặc định chỉ hàng còn hàng. Giá là VND.""")
    public Object searchProducts(
            @ToolParam(description = "Mô tả món cần tìm bằng tiếng Việt, vd. 'lều 4 người chống mưa'. Có thể bỏ trống nếu chỉ lọc theo danh mục/giá.", required = false) String query,
            @ToolParam(description = "slug danh mục lấy từ listCategories (lấy cả danh mục con), vd. 'leu-trai'", required = false) String categorySlug,
            @ToolParam(description = "Thương hiệu, vd. 'Naturehike'", required = false) String brand,
            @ToolParam(description = "Giá thấp nhất (VND)", required = false) Long minPrice,
            @ToolParam(description = "Giá cao nhất (VND)", required = false) Long maxPrice,
            @ToolParam(description = "Sắp xếp: RELEVANCE (mặc định), PRICE_ASC, PRICE_DESC, NEWEST", required = false) String sort,
            @ToolParam(description = "Có lấy cả hàng đang hết không (mặc định false)", required = false) Boolean includeOutOfStock,
            @ToolParam(description = "Số kết quả 1-8, mặc định 5", required = false) Integer limit,
            ToolContext toolContext) {
        ToolSession session = ToolSession.from(toolContext);
        if (!session.tryCall()) {
            return ToolViews.tooManyCalls();
        }
        List<String> paths = null;
        if (categorySlug != null && !categorySlug.isBlank()) {
            Optional<CategoryNode> category = categoryBySlug(categorySlug.trim());
            if (category.isEmpty()) {
                return new SearchResult(0, List.of(), "Không có danh mục '" + categorySlug
                        + "'. Gọi listCategories để lấy slug đúng.");
            }
            paths = List.of(category.get().path());
        }
        int n = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, limit));
        List<ProductDetail> found = catalog.searchProducts(new ProductFilter(blankToNull(query),
                embedder.embedOrNull(query), paths, null, blankToNull(brand), minPrice, maxPrice,
                !Boolean.TRUE.equals(includeOutOfStock), parseSort(sort), n));
        found.forEach(p -> session.found(ChatCard.product(p.card(), null, null)));
        return new SearchResult(found.size(), found.stream().map(ProductView::of).toList(),
                found.isEmpty() ? "Không có sản phẩm phù hợp với điều kiện này." : null);
    }

    @Tool(name = "getProductDetail", description = "Xem chi tiết một sản phẩm (thông số, mô tả, giá, số lượng còn) theo slug lấy từ searchProducts.")
    public Object getProductDetail(@ToolParam(description = "slug sản phẩm") String slug, ToolContext toolContext) {
        ToolSession session = ToolSession.from(toolContext);
        if (!session.tryCall()) {
            return ToolViews.tooManyCalls();
        }
        return catalog.findProduct(slug == null ? "" : slug.trim())
                .<Object>map(p -> {
                    session.recommend(ChatCard.product(p.card(), null, null));
                    return ProductDetailView.of(p);
                })
                .orElse(new Notice("Không tìm thấy sản phẩm '" + slug + "' hoặc sản phẩm đã ngừng bán."));
    }

    @Tool(name = "compareProducts", description = "So sánh 2-4 sản phẩm (giá, thông số) theo slug lấy từ searchProducts.")
    public Object compareProducts(@ToolParam(description = "Danh sách 2-4 slug sản phẩm") List<String> slugs,
                                  ToolContext toolContext) {
        ToolSession session = ToolSession.from(toolContext);
        if (!session.tryCall()) {
            return ToolViews.tooManyCalls();
        }
        if (slugs == null || slugs.size() < 2) {
            return new Notice("Cần ít nhất 2 sản phẩm để so sánh.");
        }
        List<String> wanted = slugs.stream().map(String::trim).distinct().limit(4).toList();
        List<ProductDetail> found = catalog.findProducts(wanted);
        found.forEach(p -> session.recommend(ChatCard.product(p.card(), null, null)));
        List<String> foundSlugs = found.stream().map(p -> p.card().slug()).toList();
        return new CompareResult(found.stream().map(ProductDetailView::of).toList(),
                wanted.stream().filter(s -> !foundSlugs.contains(s)).toList());
    }

    @Tool(name = "listCategories", description = """
            Liệt kê danh mục sản phẩm để biết slug. Không truyền parentSlug → danh mục gốc; truyền slug → danh mục con \
            của nó. Dùng trước searchProducts khi khách nói tên nhóm hàng (vd. 'đồ nấu ăn', 'túi ngủ').""")
    public Object listCategories(@ToolParam(description = "slug danh mục cha", required = false) String parentSlug,
                                 ToolContext toolContext) {
        ToolSession session = ToolSession.from(toolContext);
        if (!session.tryCall()) {
            return ToolViews.tooManyCalls();
        }
        List<CategoryNode> all = catalog.categories();
        Long parentId = null;
        if (parentSlug != null && !parentSlug.isBlank()) {
            Optional<CategoryNode> parent = all.stream().filter(c -> c.slug().equals(parentSlug.trim())).findFirst();
            if (parent.isEmpty()) {
                return new Notice("Không có danh mục '" + parentSlug + "'.");
            }
            parentId = parent.get().id();
        }
        Long pid = parentId;
        return all.stream()
                .filter(c -> pid == null ? c.parentId() == null : pid.equals(c.parentId()))
                .map(c -> new CategoryView(c.slug(), c.name(),
                        all.stream().anyMatch(x -> Long.valueOf(c.id()).equals(x.parentId()))))
                .toList();
    }

    @Tool(name = "searchCombos", description = "Tìm combo (bộ sản phẩm bán theo bộ, giá ưu đãi) đang bán. Dùng khi khách hỏi về combo/bộ đồ.")
    public Object searchCombos(
            @ToolParam(description = "Mô tả combo cần tìm, vd. 'combo cắm trại'", required = false) String query,
            @ToolParam(description = "Giá combo cao nhất (VND)", required = false) Long maxPrice,
            ToolContext toolContext) {
        ToolSession session = ToolSession.from(toolContext);
        if (!session.tryCall()) {
            return ToolViews.tooManyCalls();
        }
        List<ComboDetail> combos = catalog.searchCombos(blankToNull(query), embedder.embedOrNull(query), maxPrice,
                true, DEFAULT_LIMIT);
        combos.forEach(c -> session.found(ChatCard.combo(c, null, null)));
        return new ComboSearchResult(combos.size(), combos.stream().map(ComboView::of).toList(),
                combos.isEmpty() ? "Hiện không có combo còn hàng phù hợp." : null);
    }

    @Tool(name = "getComboDetail", description = "Xem chi tiết một combo (thành phần, số lượng, giá, số bộ còn) theo slug.")
    public Object getComboDetail(@ToolParam(description = "slug combo") String slug, ToolContext toolContext) {
        ToolSession session = ToolSession.from(toolContext);
        if (!session.tryCall()) {
            return ToolViews.tooManyCalls();
        }
        return catalog.findCombo(slug == null ? "" : slug.trim())
                .<Object>map(c -> {
                    session.recommend(ChatCard.combo(c, null, null));
                    return ComboView.of(c);
                })
                .orElse(new Notice("Không tìm thấy combo '" + slug + "' hoặc combo đã ngừng bán."));
    }

    private Optional<CategoryNode> categoryBySlug(String slug) {
        return catalog.categories().stream().filter(c -> c.slug().equals(slug)).findFirst();
    }

    private static ProductSort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return ProductSort.RELEVANCE;
        }
        try {
            return ProductSort.valueOf(sort.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ProductSort.RELEVANCE;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
