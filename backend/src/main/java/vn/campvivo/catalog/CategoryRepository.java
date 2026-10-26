package vn.campvivo.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Toàn bộ danh mục (số lượng nhỏ) để dựng cây; sắp theo tên để cây ổn định. */
    @Query("select c from Category c left join fetch c.parent order by lower(c.name)")
    List<Category> findAllForTree();

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsByParent_Id(Long parentId);

    /** Số sản phẩm gắn trực tiếp vào từng danh mục: [categoryId, count]. */
    @Query("select p.category.id, count(p) from Product p group by p.category.id")
    List<Object[]> countProductsByCategory();

    /** Số combo gắn trực tiếp vào từng danh mục: [categoryId, count]. */
    @Query(value = "select category_id, count(*) from product_combos group by category_id", nativeQuery = true)
    List<Object[]> countCombosByCategory();

    @Query(value = "select exists(select 1 from products where category_id = :id)"
            + " or exists(select 1 from product_combos where category_id = :id)", nativeQuery = true)
    boolean hasItems(@Param("id") Long id);

    /** Đổi cha (BR-13): thay tiền tố path của danh mục và cả cây con trong một câu. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update categories set path = :newPrefix || substring(path from length(:oldPrefix) + 1)"
            + " where path like :oldPrefix || '%'", nativeQuery = true)
    int movePaths(@Param("oldPrefix") String oldPrefix, @Param("newPrefix") String newPrefix);
}
