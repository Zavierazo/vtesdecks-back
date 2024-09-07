package com.vtesdecks.db;

import com.vtesdecks.db.model.DbDeckUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DeckUserMapper {
    @Select("SELECT * FROM deck_user WHERE user=#{user} AND deck_id=#{deckId}")
    DbDeckUser selectById(@Param("user") Integer user, @Param("deckId") String deckId);

    @Select("SELECT * FROM deck_user  WHERE deck_id=#{deckId}")
    List<DbDeckUser> selectByDeckId(@Param("deckId") String deckId);

    @Select("SELECT * FROM deck_user  WHERE user=#{user} AND favorite ORDER BY modification_date DESC")
    List<DbDeckUser> selectFavoriteByUser(@Param("user") Integer user);

    @Insert("INSERT INTO deck_user (user,deck_id,rate,favorite) VALUES (#{user},#{deckId},#{rate},#{favorite})")
    void insert(DbDeckUser entity);

    @Update("UPDATE deck_user SET rate = #{rate}, favorite = #{favorite} WHERE user=#{user} AND deck_id=#{deckId}")
    void update(DbDeckUser entity);


    @Delete("DELETE FROM deck_user WHERE deck_id=#{deckId}")
    void deleteByDeckId(@Param("deckId") String deckId);
}
