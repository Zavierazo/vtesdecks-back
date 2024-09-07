package com.vtesdecks.db;

import com.vtesdecks.db.model.DbDeckView;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DeckViewMapper {

    @Select("SELECT * FROM deck_view WHERE modification_date < (NOW() - INTERVAL 60 DAY)")
    List<DbDeckView> selectOld();

    @Select("SELECT * FROM deck_view  WHERE deck_id=#{deckId} AND modification_date > (NOW() - INTERVAL 10 DAY)")
    List<DbDeckView> selectByDeckId(@Param("deckId") String deckId);

    @Insert("INSERT INTO deck_view (id,deck_id,source) VALUES (#{id},#{deckId},#{source})")
    void insert(DbDeckView entity);

    @Delete("DELETE FROM deck_view WHERE id=#{id} AND deck_id=#{deckId}")
    void delete(@Param("id") String id, @Param("deckId") String deckId);

    @Delete("DELETE FROM deck_view WHERE deck_id=#{deckId}")
    void deleteByDeckId(@Param("deckId") String deckId);


}
