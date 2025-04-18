package com.vtesdecks.db;

import com.vtesdecks.db.model.DbUserAiAsk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserAiAskMapper {
    @Select("SELECT count(1) FROM user_ai_ask WHERE user = #{user} AND  creation_date > (NOW() - INTERVAL 300 SECOND)")
    Integer selectLastByUser(String user);

    @Insert("INSERT INTO user_ai_ask (user,question,answer)"
            + "VALUES(#{user},#{question},#{answer})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(DbUserAiAsk entity);

    @Delete("DELETE FROM user_ai_ask WHERE creation_date < (NOW() - INTERVAL 300 SECOND)")
    void deleteOld();

}
