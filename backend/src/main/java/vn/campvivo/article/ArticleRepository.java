package vn.campvivo.article;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long>, JpaSpecificationExecutor<Article> {

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    @EntityGraph(attributePaths = "author")
    Optional<Article> findBySlugAndPublishedTrue(String slug);

    @EntityGraph(attributePaths = "author")
    Optional<Article> findWithAuthorById(Long id);

    Page<Article> findByPublishedTrue(Pageable pageable);

    Page<Article> findByPublishedTrueAndCategory(ArticleCategory category, Pageable pageable);
}
