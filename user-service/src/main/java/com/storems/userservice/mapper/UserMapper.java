package com.storems.userservice.mapper;

import com.storems.userservice.po.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("SELECT id, username, role, enabled, created_at AS createdAt FROM sys_user WHERE deleted = 0 AND id = #{id}")
    User findById(@Param("id") Long id);

    @Select("SELECT id, username, role, enabled, password_hash AS passwordHash " +
            "FROM sys_user WHERE deleted = 0 AND username = #{username} FOR UPDATE")
    User findByUsername(@Param("username") String username);

    @Insert("INSERT INTO sys_user(username, password_hash) " +
            "VALUES(#{username}, #{passwordHash})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Select("SELECT id, username, role, enabled, created_at AS createdAt FROM sys_user WHERE deleted = 0 ORDER BY id")
    java.util.List<User> listUsers();

    @org.apache.ibatis.annotations.Update("UPDATE sys_user SET enabled = #{enabled} WHERE deleted = 0 AND id = #{id} AND role <> 'ADMIN'")
    int setEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);

    @org.apache.ibatis.annotations.Update("UPDATE sys_user SET password_hash = #{hash} WHERE deleted = 0 AND id = #{id}")
    int resetPassword(@Param("id") Long id, @Param("hash") String hash);

    @org.apache.ibatis.annotations.Update("UPDATE sys_user SET deleted = 1, enabled = 0 WHERE id = #{id} AND deleted = 0 AND role <> 'ADMIN'")
    int softDelete(@Param("id") Long id);

    @Select("SELECT id FROM sys_user WHERE deleted = 0 AND id = #{id} FOR UPDATE")
    Long lockUser(@Param("id") Long id);
}
