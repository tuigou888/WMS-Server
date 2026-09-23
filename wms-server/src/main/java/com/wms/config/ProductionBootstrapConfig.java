package com.wms.config;

import com.wms.model.entity.UserAccount;
import com.wms.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 生产空库的显式管理员引导；创建成功后应立即从部署环境移除引导密码。 */
@Configuration
@Profile("prod")
public class ProductionBootstrapConfig {
    @Bean
    CommandLineRunner bootstrapAdmin(Environment env, UserAccountRepository users, PasswordEncoder passwords) {
        return args -> {
            boolean hasAdmin = users.findAll().stream().anyMatch(u -> "ADMIN".equals(u.getRole()));
            if (hasAdmin) return;
            String username = trim(env.getProperty("WMS_BOOTSTRAP_ADMIN_USERNAME"));
            String password = env.getProperty("WMS_BOOTSTRAP_ADMIN_PASSWORD");
            if (username == null || password == null || password.length() < 12) {
                throw new IllegalStateException("生产数据库没有管理员，请临时设置 WMS_BOOTSTRAP_ADMIN_USERNAME 和至少 12 位的 WMS_BOOTSTRAP_ADMIN_PASSWORD 后重新启动");
            }
            if (username.length() > 50) throw new IllegalStateException("WMS_BOOTSTRAP_ADMIN_USERNAME 长度不能超过 50 个字符");
            UserAccount admin = new UserAccount();
            admin.setUsername(username);
            admin.setDisplayName(username);
            admin.setRole("ADMIN");
            admin.setEnabled(true);
            admin.setPassword(passwords.encode(password));
            users.save(admin);
        };
    }

    private String trim(String value) { return value == null || value.trim().isBlank() ? null : value.trim(); }
}
