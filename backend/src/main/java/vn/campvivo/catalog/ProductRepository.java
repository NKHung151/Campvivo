package vn.campvivo.catalog;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** Chi tiết public: chỉ sản phẩm đang bán, kèm danh mục. */
    @EntityGraph(attributePaths = "category")
    Optional<Product> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    /** Chi tiết cho admin: kèm danh mục (ảnh nạp lazy trong transaction). */
    @EntityGraph(attributePaths = "category")
    Optional<Product> findWithCategoryById(Long id);

    /**
     * SELECT ... FOR UPDATE — dùng trong OrderService khi tạo đơn, để chống bán vượt tồn kho
     * khi nhiều request đặt hàng song song. Xem quy tắc lock-ordering trong
     * docs/02-ky-thuat/database.md trước khi gọi (phải lock theo thứ tự product_id tăng dần).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    /** Nhiều sản phẩm một lần, khoá theo id tăng dần (đóng gói/tháo combo — D-22). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    /** Thương hiệu đang có sản phẩm bán (GET /brands), sắp theo tên. */
    @Query("""
            select new vn.campvivo.catalog.dto.BrandResponse(p.brand, count(p))
            from Product p
            where p.active = true and p.brand is not null and p.brand <> ''
            group by p.brand
            order by lower(p.brand)
            """)
    List<vn.campvivo.catalog.dto.BrandResponse> findActiveBrands();
}
