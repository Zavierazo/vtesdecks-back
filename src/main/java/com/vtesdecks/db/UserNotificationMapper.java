package com.vtesdecks.db;

import com.vtesdecks.db.model.DbUserNotification;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserNotificationMapper {
    @Select("SELECT * FROM user_notification WHERE id = #{id}")
    DbUserNotification selectById(Integer id);

    @Select("SELECT * FROM user_notification WHERE reference_id = #{referenceId}")
    DbUserNotification selectByReferenceId(Integer referenceId);

    @Select("SELECT COUNT(1) FROM user_notification WHERE user = #{user} AND `read` IS FALSE")
    Integer countUnreadByUser(Integer user);

    @Select("SELECT * FROM user_notification WHERE user = #{user} ORDER BY creation_date DESC")
    List<DbUserNotification> selectByUser(Integer user);

    @Insert("INSERT INTO user_notification (user,reference_id,`read`,type,notification,link) "
            + "VALUES (#{user},#{referenceId},#{read},#{type},#{notification},#{link})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(DbUserNotification entity);

    @Update("UPDATE user_notification " +
            "SET `read` = #{read}, type = #{type}, notification = #{notification}, link = #{link} " +
            "WHERE id=#{id}")
    void update(DbUserNotification entity);

    @Update("UPDATE user_notification SET `read` = true WHERE user = #{user}")
    void updateReadAllByUser(Integer user);


    @Delete("DELETE FROM user_notification WHERE id = #{id}")
    void delete(Integer id);

    @Delete("DELETE FROM user_notification WHERE reference_id = #{referenceId}")
    void deleteByReferenceId(Integer referenceId);
}
