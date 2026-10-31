package vn.campvivo.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Email đã chuẩn hoá chữ thường — gọi User.normalizeEmail() trước khi tra cứu. */
    Optional<User> findByEmail(String email);

    /**
     * Tăng token_version bằng một câu UPDATE nguyên tử (không đọc rồi ghi) để hai đăng xuất đồng thời
     * không ghi đè nhau. Gọi trong @Transactional.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.tokenVersion = u.tokenVersion + 1, u.updatedAt = CURRENT_TIMESTAMP where u.id = :id")
    int incrementTokenVersion(@Param("id") Long id);

    /** Id user có email hoặc họ tên chứa `pattern` (đã kèm %, chữ thường) — tìm đơn hàng theo khách (FR-13). */
    @Query("select u.id from User u where lower(u.email) like :pattern or lower(u.fullName) like :pattern order by u.id")
    List<Long> findIdsByKeyword(@Param("pattern") String pattern, Pageable pageable);

    /**
     * Tính một lần thử OTP, chỉ khi chưa đủ số lần cho phép (BR-11). Một câu UPDATE nguyên tử nên nhiều lần đoán
     * song song cũng không vượt quá `max`. Trả 0 nếu đã đủ số lần.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.otpAttempts = u.otpAttempts + 1 where u.id = :id and u.otpAttempts < :max")
    int incrementOtpAttemptsIfBelow(@Param("id") Long id, @Param("max") int max);

    /**
     * Xoá OTP sau khi dùng đúng (dùng một lần). Chỉ thành công nếu OTP còn tồn tại: trả 0 khi một yêu cầu song song
     * đã dùng mất rồi. Giữ nguyên otp_last_sent_at để vẫn giới hạn tần suất gửi lại.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.otpCodeHash = null, u.otpPurpose = null, u.otpExpiresAt = null, u.otpAttempts = 0, "
            + "u.updatedAt = CURRENT_TIMESTAMP where u.id = :id and u.otpCodeHash is not null")
    int clearOtp(@Param("id") Long id);

    /** Đánh dấu email đã xác thực (một câu UPDATE nguyên tử). */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.emailVerified = true, u.updatedAt = CURRENT_TIMESTAMP where u.id = :id")
    int markEmailVerified(@Param("id") Long id);
}
