package com.vtesdecks.db;

import com.vtesdecks.db.model.DbDeck;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DeckMapper {

    @Select("SELECT * FROM deck")
    List<DbDeck> selectAll();

    @Select("SELECT * FROM deck WHERE id=#{id}")
    DbDeck selectById(String id);

    @Insert("INSERT INTO deck (id,type,user,name,tournament,players,`year`,author,url,source,description,creation_date,verified,published,deleted,views)"
            + "VALUES(#{id},#{type},#{user},#{name},#{tournament},#{players},#{year},#{author},#{url},#{source},#{description},#{creationDate},#{verified},#{published},#{deleted},#{views})")
    void insert(DbDeck entity);

    @Update("UPDATE deck SET type=#{type},user=#{user},name=#{name},tournament=#{tournament},players=#{players},`year`=#{year}," +
            "author=#{author},url=#{url},source=#{source},description=#{description},creation_date=#{creationDate},verified=#{verified}," +
            "published=#{published},views=#{views},deleted=#{deleted} WHERE id=#{id}")
    void update(DbDeck entity);


    @Select("SELECT * FROM deck WHERE type ='COMMUNITY' AND deleted = true AND modification_date < (NOW() - INTERVAL 60 DAY)")
    List<DbDeck> selectOldDeleted();

    @Select("SELECT * FROM deck WHERE type ='COMMUNITY' AND deleted = true AND user=#{userId}")
    List<DbDeck> selectUserDeleted(@Param("userId") Integer userId);

    @Delete("DELETE FROM deck WHERE id=#{id}")
    void delete(@Param("id") String id);

}
