package com.example.demo.repository;
import com.example.demo.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByStudentCode(String studentCode);
    Optional<User> findByEmailOrStudentCodeOrUsername(String email, String studentCode, String username);
    boolean existsByEmail(String email);
    boolean existsByStudentCode(String studentCode);
    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE (:roleCode IS NULL OR u.role.code = :roleCode) " +
           "AND (:status IS NULL OR u.accountStatus = :status) " +
           "AND (:keyword IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.studentCode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<User> searchUsers(@Param("roleCode") String roleCode,
                           @Param("status") String status,
                           @Param("keyword") String keyword,
                           Pageable pageable);
}
