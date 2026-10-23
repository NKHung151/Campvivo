package vn.campvivo.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Email đã chuẩn hoá chữ thường — gọi User.normalizeEmail() trước khi tra cứu. */
    Optional<User> findByEmail(String email);
}
