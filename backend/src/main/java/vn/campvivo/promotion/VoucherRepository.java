package vn.campvivo.promotion;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long>, JpaSpecificationExecutor<Voucher> {

    /** `code` phải đã chuẩn hoá bằng {@link Voucher#normalizeCode}. */
    Optional<Voucher> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    /** SELECT … FOR UPDATE — khoá voucher SAU products/combos khi tạo đơn (database.md mục 6). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where v.code = :code")
    Optional<Voucher> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where v.id = :id")
    Optional<Voucher> findByIdForUpdate(@Param("id") Long id);

    /** BR-5: khách đã có đơn dùng mã này ở trạng thái khác CANCELLED/PAYMENT_FAILED. */
    @Query(value = """
            select exists(select 1 from orders where voucher_id = :voucherId and user_id = :userId
                          and status not in ('CANCELLED', 'PAYMENT_FAILED'))
            """, nativeQuery = true)
    boolean existsActiveUsage(@Param("voucherId") Long voucherId, @Param("userId") Long userId);

    /** Mã đã từng gắn với đơn nào (kể cả đơn huỷ) → không xoá được, chỉ tắt (FR-16). */
    @Query(value = "select exists(select 1 from orders where voucher_id = :voucherId)", nativeQuery = true)
    boolean hasOrders(@Param("voucherId") Long voucherId);

    /** Voucher công khai đang hiệu lực, còn lượt — danh sách mã (D-29), sắp hết hạn trước. */
    @Query("""
            select v from Voucher v
            where v.active = true and v.publicVisible = true and v.startDate <= :now and v.expiryDate > :now
              and v.usedCount < v.quantity
            order by v.expiryDate asc, v.id asc
            """)
    List<Voucher> findPublicAvailable(@Param("now") OffsetDateTime now);
}
