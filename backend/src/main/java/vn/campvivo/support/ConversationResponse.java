package vn.campvivo.support;

import vn.campvivo.user.User;

/** Một hội thoại trong danh sách của nhân viên (api.md mục 4.8). */
public record ConversationResponse(Customer customer, SupportMessageResponse lastMessage) {

    public record Customer(Long id, String fullName, String email) {
        static Customer from(User u) {
            return new Customer(u.getId(), u.getFullName(), u.getEmail());
        }
    }
}
