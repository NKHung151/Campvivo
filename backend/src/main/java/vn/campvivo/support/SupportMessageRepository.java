package vn.campvivo.support;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    /** Trang đầu của hội thoại: mới nhất trước. */
    List<SupportMessage> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    /** Các tin CŨ HƠN `before` (cuộn lên), mới nhất trước. Dùng `id` làm mốc vì tăng đơn điệu, không trùng như `created_at`. */
    List<SupportMessage> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long before, Pageable pageable);

    /** Tin cuối của MỖI khách, hội thoại có tin mới nhất đứng đầu. Khách chưa nhắn gì thì không có hội thoại. */
    @Query(value = "select m from SupportMessage m where m.id in (select max(x.id) from SupportMessage x group by x.userId) "
            + "order by m.id desc",
            countQuery = "select count(distinct x.userId) from SupportMessage x")
    Page<SupportMessage> findLastMessagePerConversation(Pageable pageable);
}
