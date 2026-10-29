package vn.campvivo.chatbot.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.campvivo.chatbot.catalog.CatalogModels.ComboDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductFilter;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductSort;
import vn.campvivo.chatbot.support.IntegrationTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CB-10: query đọc catalog chạy đúng trên schema + seed thật của backend (V2–V4). Đổi tên cột ở backend → test này đỏ.
 */
class JdbcCatalogReaderIT extends IntegrationTest {

    @Autowired
    CatalogReadPort catalog;

    private static ProductFilter filter(String text, List<String> paths, Long min, Long max, boolean inStock,
                                        ProductSort sort, int limit) {
        return new ProductFilter(text, null, paths, null, null, min, max, inStock, sort, limit);
    }

    @Test
    void filtersByPriceRangeInclusiveAndSortsByPrice() {
        List<ProductDetail> found = catalog.searchProducts(filter(null, null, 500_000L, 1_000_000L, false,
                ProductSort.PRICE_ASC, 50));
        assertThat(found).isNotEmpty();
        assertThat(found).allSatisfy(p -> assertThat(p.card().price()).isBetween(500_000L, 1_000_000L));
        assertThat(found).extracting(p -> p.card().price()).isSorted();
    }

    @Test
    void categoryFilterIncludesSubcategoriesAndExcludesInactiveAndOutOfStock() {
        String campingPath = catalog.categories().stream().filter(c -> c.slug().equals("do-cam-trai"))
                .findFirst().orElseThrow().path();
        List<ProductDetail> found = catalog.searchProducts(filter(null, List.of(campingPath), null, null, true,
                ProductSort.NEWEST, 500));
        assertThat(found).isNotEmpty();
        assertThat(found).allSatisfy(p -> {
            assertThat(p.card().categoryPath()).startsWith(campingPath);
            assertThat(p.card().availableQuantity()).isPositive();
        });
        // Danh mục con (vd. lều) cũng có mặt
        assertThat(found).anyMatch(p -> p.card().categorySlug().equals("leu-trai"));

        String someSlug = found.get(0).card().slug();
        jdbc.sql("UPDATE products SET is_active = false WHERE slug = ?").param(someSlug).update();
        try {
            assertThat(catalog.findProduct(someSlug)).isEmpty();
        } finally {
            jdbc.sql("UPDATE products SET is_active = true WHERE slug = ?").param(someSlug).update();
        }
    }

    @Test
    void textSearchIgnoresVietnameseDiacritics() {
        List<ProductDetail> found = catalog.searchProducts(filter("leu 1 nguoi", null, null, null, false,
                ProductSort.RELEVANCE, 5));
        assertThat(found).isNotEmpty();
        assertThat(found.get(0).card().name()).containsIgnoringCase("Lều 1 người");
    }

    @Test
    void readsComboWithItemsSpecsAndImage() {
        ComboDetail combo = catalog.findCombo("combo-cam-trai-1-nguoi-co-ban").orElseThrow();
        assertThat(combo.price()).isEqualTo(5_490_000L);
        assertThat(combo.availableQuantity()).isEqualTo(5);
        assertThat(combo.items()).hasSize(4);
        assertThat(combo.items().get(0).productName()).contains("Lều 1 người");
        assertThat(combo.items().get(0).categorySlug()).isEqualTo("leu-trai");
        assertThat(combo.primaryImageUrl()).isNotBlank();
        assertThat(combo.descriptionText()).doesNotContain("<p>");
        assertThat(combo.itemsTotal()).isPositive();
    }

    @Test
    void comboSearchCanSkipSoldOutCombos() {
        List<String> inStock = catalog.searchCombos(null, null, null, true, 50).stream().map(ComboDetail::slug).toList();
        List<String> all = catalog.searchCombos(null, null, null, false, 50).stream().map(ComboDetail::slug).toList();
        assertThat(inStock).doesNotContain("combo-ngu-am-mua-dong");
        assertThat(all).contains("combo-ngu-am-mua-dong");
        assertThat(catalog.searchCombos(null, null, 1_000_000L, false, 50))
                .allSatisfy(c -> assertThat(c.price()).isLessThanOrEqualTo(1_000_000L));
    }
}
