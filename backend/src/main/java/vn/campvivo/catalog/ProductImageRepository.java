package vn.campvivo.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /** Ảnh chính của nhiều sản phẩm một lần (tránh N+1 khi dựng bảng danh sách). */
    @Query("select i from ProductImage i where i.primary = true and i.product.id in :productIds")
    List<ProductImage> findPrimaryByProductIds(@Param("productIds") Collection<Long> productIds);

    /**
     * URL nào trong danh sách đang thuộc sản phẩm khác hoặc combo (`image_url` unique toàn bảng).
     * `productId` = id sản phẩm đang sửa, hoặc -1 khi tạo mới.
     */
    @Query(value = "select image_url from images where image_url in (:urls) "
            + "and (product_id is null or product_id <> :productId)", nativeQuery = true)
    List<String> findUrlsUsedElsewhere(@Param("urls") Collection<String> urls, @Param("productId") long productId);
}
