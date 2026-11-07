package vn.campvivo.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long>, JpaSpecificationExecutor<Review> {

    Optional<Review> findByUserIdAndProductId(Long userId, Long productId);

    Optional<Review> findByUserIdAndComboId(Long userId, Long comboId);

    /** Đánh giá của chính khách (của người khác → coi như không tồn tại). */
    Optional<Review> findByIdAndUserId(Long id, Long userId);

    /** Đánh giá của khách cho các dòng hàng trong một đơn (chi tiết đơn — 1 truy vấn cho mọi sản phẩm). */
    List<Review> findByUserIdAndProductIdIn(Long userId, Collection<Long> productIds);

    List<Review> findByUserIdAndComboIdIn(Long userId, Collection<Long> comboIds);

    /** [rating, số lượt] của đánh giá đang hiện — phân bố sao + điểm TB (summary, D-32). */
    @Query("select r.rating, count(r) from Review r where r.productId = :id and r.hidden = false group by r.rating")
    List<Object[]> countByRatingForProduct(@Param("id") Long productId);

    @Query("select r.rating, count(r) from Review r where r.comboId = :id and r.hidden = false group by r.rating")
    List<Object[]> countByRatingForCombo(@Param("id") Long comboId);
}
