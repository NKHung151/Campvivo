package vn.campvivo.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
