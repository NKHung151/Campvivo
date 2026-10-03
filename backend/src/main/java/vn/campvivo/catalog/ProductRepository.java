package vn.campvivo.catalog;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySlugAndActiveTrue(String slug);

    /**
     * SELECT ... FOR UPDATE — dùng trong OrderService khi tạo đơn, để chống bán vượt tồn kho
     * khi nhiều request đặt hàng song song. Xem quy tắc lock-ordering trong
     * docs/02-ky-thuat/database.md trước khi gọi (phải lock theo thứ tự product_id tăng dần).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
