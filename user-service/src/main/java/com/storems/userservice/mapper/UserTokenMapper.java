package com.storems.userservice.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface UserTokenMapper {

    @Delete("DELETE FROM user_token WHERE user_id = #{userId}")
    int deleteForUser(@Param("userId") Long userId);

    @Delete("DELETE FROM user_token WHERE token_hash = #{tokenHash}")
    int delete(@Param("tokenHash") String tokenHash);

    @Select("SELECT t.user_id FROM user_token t JOIN sys_user u ON u.id = t.user_id " +
            "WHERE t.token_hash = #{tokenHash} AND t.expires_at > NOW() AND u.enabled = 1 AND u.deleted = 0")
    Long findValidUserId(@Param("tokenHash") String tokenHash);

    @Insert("INSERT INTO user_token(token_hash, user_id, expires_at) " +
            "VALUES(#{tokenHash}, #{userId}, " +
            "DATE_ADD(NOW(), INTERVAL 2 HOUR))")
    int insert(@Param("tokenHash") String tokenHash,
               @Param("userId") Long userId);
}
