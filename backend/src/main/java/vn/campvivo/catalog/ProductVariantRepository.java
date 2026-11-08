package vn.campvivo.catalog;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    /**
     * SELECT ... FOR UPDATE một biến thể (kèm sản phẩm). Khi khoá nhiều dòng phải theo id biến thể tăng dần
     * (docs/02-ky-thuat/database.md — lock-ordering).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v join fetch v.product where v.id = :id")
    Optional<ProductVariant> findByIdForUpdate(@Param("id") Long id);

    /** Nhiều biến thể một lần, khoá theo id tăng dần (đóng gói/tháo combo — D-22). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v join fetch v.product where v.id in :ids order by v.id")
    List<ProductVariant> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    /** Mọi biến thể của một sản phẩm, khoá theo id tăng dần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.product.id = :productId order by v.id")
    List<ProductVariant> findAllByProductIdForUpdate(@Param("productId") Long productId);

    /** Kèm sản phẩm + giá trị tuỳ chọn để hiển thị tên ("Áo mưa — Tím / S/M"). */
    @Query("""
            select v from ProductVariant v join fetch v.product
            left join fetch v.option1Value left join fetch v.option2Value
            where v.id in :ids
            """)
    List<ProductVariant> findAllWithProductByIdIn(@Param("ids") Collection<Long> ids);
}
