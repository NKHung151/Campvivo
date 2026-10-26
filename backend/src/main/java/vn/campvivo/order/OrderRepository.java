package vn.campvivo.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.campvivo.payment.PaymentMethod;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    boolean existsByOrderCode(String orderCode);

    /** Đơn của chính khách (đơn người khác → coi như không tồn tại, BR quyền sở hữu). */
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    /** SELECT … FOR UPDATE — khoá `orders` trước mọi khoá khác khi đổi trạng thái (database.md mục 6). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderCode = :code")
    Optional<Order> findByOrderCodeForUpdate(@Param("code") String code);

    /** Đơn cổng thanh toán còn chờ quá hạn (BR-3) — job xử lý từng đơn trong transaction riêng. */
    @Query("select o.id from Order o where o.status = vn.campvivo.order.OrderStatus.PENDING "
            + "and o.paymentMethod = :method and o.createdAt < :cutoff order by o.id")
    List<Long> findPendingIdsCreatedBefore(@Param("method") PaymentMethod method, @Param("cutoff") OffsetDateTime cutoff);
}
