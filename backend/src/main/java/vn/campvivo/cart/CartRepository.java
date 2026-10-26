package vn.campvivo.cart;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserId(Long userId);

    /** Khoá giỏ khi sửa: hai request thêm cùng mặt hàng song song không vướng unique (cart_id, product_id). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.userId = :userId")
    Optional<Cart> findByUserIdForUpdate(@Param("userId") Long userId);

    /** Tạo giỏ nếu chưa có — an toàn khi hai request đầu tiên của user chạy song song. */
    @Modifying
    @Query(value = "insert into carts (user_id) values (:userId) on conflict (user_id) do nothing", nativeQuery = true)
    void insertIfAbsent(@Param("userId") Long userId);
}
