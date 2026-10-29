package vn.campvivo.chatbot.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductFilter;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductSort;
import vn.campvivo.chatbot.catalog.CatalogReadPort;
import vn.campvivo.chatbot.support.FakeEmbeddingModel;
import vn.campvivo.chatbot.support.IntegrationTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CB-11: job chỉ embed mặt hàng có văn bản đổi, xoá dòng mặt hàng ngừng bán; tìm kiếm dùng được embedding.
 */
class ItemEmbeddingJobIT extends IntegrationTest {

    @Autowired
    ItemEmbeddingJob job;
    @Autowired
    CatalogReadPort catalog;

    @Test
    void embedsOnlyChangedItemsAndUsesThemForSearch() {
        job.refresh();
        long rows = jdbc.sql("SELECT count(*) FROM chatbot_item_embeddings").query(Long.class).single();
        long active = jdbc.sql("SELECT (SELECT count(*) FROM products WHERE is_active) "
                + "+ (SELECT count(*) FROM product_combos WHERE is_active)").query(Long.class).single();
        assertThat(rows).isEqualTo(active);

        // Không đổi gì → không gọi embedding lần nào
        int before = embeddingModel.embeddedTexts.get();
        assertThat(job.refresh()).isZero();
        assertThat(embeddingModel.embeddedTexts.get()).isEqualTo(before);

        // Đổi tên 1 sản phẩm → embed lại đúng 1 mặt hàng
        long id = jdbc.sql("SELECT id FROM products WHERE is_active ORDER BY id LIMIT 1").query(Long.class).single();
        String name = jdbc.sql("SELECT name FROM products WHERE id = ?").param(id).query(String.class).single();
        jdbc.sql("UPDATE products SET name = name || ' bản mới' WHERE id = ?").param(id).update();
        try {
            assertThat(job.refresh()).isEqualTo(1);
        } finally {
            jdbc.sql("UPDATE products SET name = ? WHERE id = ?").params(name, id).update();
        }

        // Ngừng bán → dòng embedding bị xoá
        jdbc.sql("UPDATE products SET is_active = false WHERE id = ?").param(id).update();
        try {
            job.refresh();
            assertThat(jdbc.sql("SELECT count(*) FROM chatbot_item_embeddings WHERE item_type = 'PRODUCT' AND item_id = ?")
                    .param(id).query(Long.class).single()).isZero();
        } finally {
            jdbc.sql("UPDATE products SET is_active = true WHERE id = ?").param(id).update();
            job.refresh();
        }

        // Tìm bằng vector: sản phẩm giống câu hỏi nhất lên đầu
        String query = "Lều 1 người Coleman Instant up Dome";
        List<ProductDetail> found = catalog.searchProducts(new ProductFilter(query, FakeEmbeddingModel.vector(query),
                null, null, null, null, null, false, ProductSort.RELEVANCE, 3));
        assertThat(found.get(0).card().name()).contains("Coleman Instant up Dome");
    }

    @Test
    void embeddingFailureDoesNotBreakTheJob() {
        embeddingModel.fail = true;
        try {
            jdbc.sql("UPDATE products SET name = name || ' x' WHERE id = (SELECT min(id) FROM products)").update();
            job.refreshSafely();   // chỉ ghi log
        } finally {
            embeddingModel.fail = false;
            jdbc.sql("UPDATE products SET name = left(name, length(name) - 2) WHERE id = (SELECT min(id) FROM products)")
                    .update();
        }
    }
}
