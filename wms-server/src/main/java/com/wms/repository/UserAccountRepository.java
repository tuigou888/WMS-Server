package com.wms.repository;
import com.wms.model.entity.UserAccount; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional;
public interface UserAccountRepository extends JpaRepository<UserAccount,Long> { Optional<UserAccount> findByUsername(String username); }
