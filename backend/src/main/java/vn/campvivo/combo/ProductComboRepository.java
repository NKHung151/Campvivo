package vn.campvivo.combo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProductComboRepository extends JpaRepository<ProductCombo, Long>, JpaSpecificationExecutor<ProductCombo> {

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    @EntityGraph(attributePaths = "category")
    Optional<ProductCombo> findWithCategoryById(Long id);

    /** Trang chi tiết combo public — chỉ combo đang bán. */
    @EntityGraph(attributePaths = "category")
    Optional<ProductCombo> findBySlugAndActiveTrue(String slug);

    /** SELECT … FOR UPDATE — đóng gói/tháo combo (khoá combo trước, rồi các sản phẩm thành phần theo id tăng dần). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ProductCombo c join fetch c.category where c.id = :id")
    Optional<ProductCombo> findByIdForUpdate(@Param("id") Long id);

    /** Ảnh chính tự tải lên của nhiều combo: [comboId, url]. */
    @Query("select i.combo.id, i.imageUrl from ComboImage i where i.primary = true and i.combo.id in :ids")
    List<Object[]> findOwnPrimaryImageUrls(@Param("ids") Collection<Long> ids);

    /** Ảnh chính của các sản phẩm thành phần: [comboId, url] theo thứ tự dòng trong combo. */
    @Query("""
            select ci.combo.id, img.imageUrl from ComboItem ci, ProductImage img
            where img.product = ci.product and img.primary = true and ci.combo.id in :ids
            order by ci.combo.id, ci.id
            """)
    List<Object[]> findItemImageUrls(@Param("ids") Collection<Long> ids);

    /** Ảnh đại diện (D-26): ảnh chính tự tải lên, chưa có thì ảnh của sản phẩm thành phần đầu tiên có ảnh. */
    default Map<Long, String> findCoverImageUrls(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        findOwnPrimaryImageUrls(ids).forEach(r -> out.putIfAbsent((Long) r[0], (String) r[1]));
        findItemImageUrls(ids).forEach(r -> out.putIfAbsent((Long) r[0], (String) r[1]));
        return out;
    }

    /** URL nào trong danh sách đang thuộc sản phẩm hoặc combo khác (`image_url` unique toàn bảng). comboId = -1 khi tạo mới. */
    @Query(value = "select image_url from images where image_url in (:urls) and (combo_id is null or combo_id <> :comboId)",
            nativeQuery = true)
    List<String> findUrlsUsedElsewhere(@Param("urls") Collection<String> urls, @Param("comboId") long comboId);
}
