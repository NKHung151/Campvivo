package vn.campvivo.favorite;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByIdAndUserId(Long id, Long userId);

    List<Favorite> findByUserId(Long userId);

    /**
     * Mục yêu thích của khách mà đối tượng đang hiển thị (sản phẩm / combo đang bán, bài đã xuất bản — D-47), mới thích trước.
     * `type` = PRODUCT | COMBO | ARTICLE | ALL (ALL thay cho null: tham số null trong SQL thuần không xác định được kiểu).
     */
    @Query(value = """
            select f.* from favorites f
              left join products p on p.id = f.product_id
              left join product_combos c on c.id = f.combo_id
              left join articles a on a.id = f.article_id
            where f.user_id = :userId and (p.is_active or c.is_active or a.is_published)
              and (:type = 'ALL' or (:type = 'PRODUCT' and f.product_id is not null)
                   or (:type = 'COMBO' and f.combo_id is not null) or (:type = 'ARTICLE' and f.article_id is not null))
            order by f.created_at desc, f.id desc
            """, countQuery = """
            select count(*) from favorites f
              left join products p on p.id = f.product_id
              left join product_combos c on c.id = f.combo_id
              left join articles a on a.id = f.article_id
            where f.user_id = :userId and (p.is_active or c.is_active or a.is_published)
              and (:type = 'ALL' or (:type = 'PRODUCT' and f.product_id is not null)
                   or (:type = 'COMBO' and f.combo_id is not null) or (:type = 'ARTICLE' and f.article_id is not null))
            """, nativeQuery = true)
    Page<Favorite> findVisible(@Param("userId") Long userId, @Param("type") String type, Pageable pageable);
}
