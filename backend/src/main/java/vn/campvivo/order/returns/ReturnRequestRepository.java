package vn.campvivo.order.returns;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    Optional<ReturnRequest> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    /** Chỉ lấy id đơn (không nạp entity vào persistence context trước khi khoá). */
    @Query("select r.orderId from ReturnRequest r where r.id = :id")
    Optional<Long> findOrderIdById(@Param("id") Long id);

    Page<ReturnRequest> findByStatus(ReturnStatus status, Pageable pageable);

    /** Khoá yêu cầu SAU khi đã khoá đơn (thứ tự khoá: orders → return_requests → tồn kho). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequest r where r.id = :id")
    Optional<ReturnRequest> findByIdForUpdate(@Param("id") Long id);
}
