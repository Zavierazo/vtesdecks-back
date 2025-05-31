package com.vtesdecks.db;

import com.vtesdecks.db.model.DbCardShop;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CardShopMapper {

    @Insert("INSERT INTO card_shop (card_id, platform, `set`, link, price, currency) VALUES(#{cardId}, #{platform}, #{set}, #{link}, #{price}, #{currency})")
    void insert(DbCardShop entity);

    @Insert("UPDATE card_shop  SET card_id=#{cardId}, platform=#{platform},`set`=#{set}, link=#{link}, price=#{price}, currency=#{currency} WHERE id=#{id}")
    void update(DbCardShop entity);

    @Select("SELECT * FROM card_shop ORDER BY Id")
    List<DbCardShop> selectAll();

    @Select("SELECT * FROM card_shop WHERE id=#{id}")
    DbCardShop selectById(Integer id);

    @Select("SELECT * FROM card_shop WHERE card_id=#{cardId}")
    List<DbCardShop> selectByCardId(Integer cardId);

    @Select("SELECT * FROM card_shop WHERE card_id=#{cardId} AND platform=#{platform}")
    List<DbCardShop> selectByCardIdAndPlatform(@Param("cardId") Integer cardId, @Param("platform") String platform);

    @Select("SELECT * FROM card_shop WHERE platform=#{platform}")
    List<DbCardShop> selectByPlatform(String platform);

    @Delete("DELETE FROM card_shop WHERE id=#{id}")
    void delete(Integer id);
}
