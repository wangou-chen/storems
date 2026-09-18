package com.storems.userservice.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public AdminInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        addColumn("role", "VARCHAR(20) NOT NULL DEFAULT 'OPERATOR'");
        addColumn("enabled", "TINYINT(1) NOT NULL DEFAULT 1");
        addColumn("deleted", "TINYINT(1) NOT NULL DEFAULT 0");
        addColumn("active_username", "VARCHAR(50) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN username ELSE NULL END) STORED");
        if (!hasIndex("uk_sys_user_live_username")) {
            jdbc.execute("CREATE UNIQUE INDEX uk_sys_user_live_username ON sys_user(active_username)");
        }
        for (String old : new String[]{"uk_sys_user_username", "uk_sys_user_active_username"}) {
            if (hasIndex(old)) jdbc.execute("ALTER TABLE sys_user DROP INDEX " + old);
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND username = 'admin'", Integer.class);
        if (count == 0) {
            try {
                jdbc.update("INSERT INTO sys_user(username,password_hash,role,enabled) VALUES ('admin',?,'ADMIN',1)",
                        new BCryptPasswordEncoder().encode("adminadmin"));
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
                // Another instance initialized the account first.
            }
        }
        String role = jdbc.queryForObject("SELECT role FROM sys_user WHERE deleted = 0 AND username = 'admin'", String.class);
        if (!"ADMIN".equals(role)) {
            throw new IllegalStateException("Existing admin username is not an administrator; resolve the conflict manually. No password or role was overwritten.");
        }
    }

    private boolean hasIndex(String name) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() " +
                "AND table_name = 'sys_user' AND index_name = ?", Integer.class, name) > 0;
    }

    private void addColumn(String name, String definition) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = ?", Integer.class, name);
        if (count == 0) {
            try { jdbc.execute("ALTER TABLE sys_user ADD COLUMN " + name + " " + definition); }
            catch (org.springframework.dao.DataAccessException error) {
                Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = ?", Integer.class, name);
                if (exists == 0) throw error;
            }
        }
    }
}
