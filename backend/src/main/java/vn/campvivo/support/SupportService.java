package vn.campvivo.support;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chat hỗ trợ khách ↔ nhân viên (FR-26, D-05). Mỗi khách có đúng MỘT hội thoại, khoá bằng `user_id`; khách chỉ thao tác trên
 * hội thoại của chính mình vì `customerId` lấy từ token, không bao giờ từ client.
 */
@Service
public class SupportService {

    private final SupportMessageRepository messages;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    public SupportService(SupportMessageRepository messages, UserRepository users, ApplicationEventPublisher events) {
        this.messages = messages;
        this.users = users;
        this.events = events;
    }

    /** Khách gửi tin vào hội thoại của chính mình. */
    @Transactional
    public SupportMessageResponse sendAsCustomer(Long customerId, String content) {
        return save(customerId, content, false, null);
    }

    /**
     * Nhân viên `staffId` trả lời khách `customerId`. Không có người dùng đó, hoặc không phải khách → 404 NOT_FOUND. `staffId` chỉ để
     * không đẩy lại tin cho chính người gửi; không được lưu (D-05, KI-02).
     */
    @Transactional
    public SupportMessageResponse sendAsStaff(Long staffId, Long customerId, String content) {
        requireCustomer(customerId);
        return save(customerId, content, true, staffId);
    }

    /** Tin của hội thoại, MỚI NHẤT TRƯỚC; `before` = id tin cũ nhất client đang có (bỏ trống = từ tin mới nhất). */
    @Transactional(readOnly = true)
    public List<SupportMessageResponse> listMessages(Long customerId, Long before, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<SupportMessage> found = before == null
                ? messages.findByUserIdOrderByIdDesc(customerId, page)
                : messages.findByUserIdAndIdLessThanOrderByIdDesc(customerId, before, page);
        return found.stream().map(SupportMessageResponse::from).toList();
    }

    /** Tin hội thoại của khách `customerId` cho nhân viên xem. Không phải khách → 404. */
    @Transactional(readOnly = true)
    public List<SupportMessageResponse> listMessagesForStaff(Long customerId, Long before, int limit) {
        requireCustomer(customerId);
        return listMessages(customerId, before, limit);
    }

    /** Danh sách hội thoại cho nhân viên: tin cuối của mỗi khách, hội thoại có tin mới nhất đứng đầu. */
    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> listConversations(int page, int limit) {
        var result = messages.findLastMessagePerConversation(PageRequest.of(page - 1, limit));
        Map<Long, User> customers = users.findAllById(result.getContent().stream().map(SupportMessage::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(result.map(m -> new ConversationResponse(
                ConversationResponse.Customer.from(customers.get(m.getUserId())), SupportMessageResponse.from(m))));
    }

    private SupportMessageResponse save(Long customerId, String content, boolean fromStaff, Long staffSenderId) {
        SupportMessage saved = messages.save(new SupportMessage(customerId, content.trim(), fromStaff));
        SupportMessageResponse response = SupportMessageResponse.from(saved);
        events.publishEvent(new SupportMessageSentEvent(response, staffSenderId));
        return response;
    }

    private void requireCustomer(Long customerId) {
        users.findById(customerId)
                .filter(u -> u.getRole() == Role.CUSTOMER)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
