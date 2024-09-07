package com.vtesdecks.db;

import com.vtesdecks.db.model.DbCardCount;
import com.vtesdecks.db.model.DbDeckCard;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DeckCardMapper {

    @Select("SELECT * FROM deck_card WHERE deck_id = #{deckId} ORDER BY id")
    List<DbDeckCard> selectByDeck(String deckId);

    @Select("SELECT dc.id, count(1) as number FROM deck_card dc WHERE deck_id in(SELECT id FROM deck d WHERE d.type = 'TOURNAMENT' AND d.creation_date >= current_date  - interval '2' year) GROUP BY dc.id")
    List<DbCardCount> selectDeckCountByCard();

    @Select("SELECT dc.id, sum(dc.number) as number FROM deck_card dc WHERE deck_id in(SELECT id FROM deck d WHERE d.type = 'TOURNAMENT' AND d.creation_date >= current_date  - interval '2' year) GROUP BY dc.id")
    List<DbCardCount> selectCountByCard();

    @Insert("INSERT INTO deck_card (deck_id,id,number)"
            + "VALUES(#{deckId},#{id},#{number})")
    void insert(DbDeckCard entity);

    @Update("UPDATE deck_card SET number=#{number} "
            + "WHERE deck_id=#{deckId} AND id=#{id}")
    void update(DbDeckCard entity);

    @Delete("DELETE FROM deck_card WHERE deck_id=#{deckId} AND id=#{id}")
    void delete(@Param("deckId") String deckId, @Param("id") Integer id);
    
    @Delete("DELETE FROM deck_card WHERE deck_id=#{deckId}")
    void deleteByDeckId(@Param("deckId") String deckId);
}
