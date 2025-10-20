package com.vtesdecks.db;

import com.vtesdecks.db.model.DbCrypt;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CryptMapper {
    @Select("SELECT * FROM crypt ORDER BY Id")
    List<DbCrypt> selectAll();

    @Select("SELECT * FROM crypt WHERE Id=#{id}")
    DbCrypt selectById(Integer id);

    @Select("SELECT * FROM crypt WHERE name = #{name} OR aka = #{name}")
    DbCrypt selectByName(String name);

    @Insert("INSERT INTO crypt " +
            "(Id, Name, Aka, `Type`, Clan, Path, Adv, `Group`, Capacity, Disciplines, Text, `Set`, Title, Banned, Artist)"
            + " VALUES " +
            "(#{Id}, #{Name}, #{Aka}, #{Type}, #{Clan}, #{Path}, #{Adv}, #{Group}, #{Capacity}, #{Disciplines}, #{Text}, #{Set}, #{Title}, #{Banned}, #{Artist})")
    void insert(DbCrypt entity);

    @Update("UPDATE crypt SET Name=#{Name}, Aka=#{Aka}, `Type`=#{Type}, Clan=#{Clan}, Path=#{Path}, Adv=#{Adv}, `Group`=#{Group}, " +
            "Capacity=#{Capacity}, Disciplines=#{Disciplines}, Text=#{Text}, `Set`=#{Set}, Title=#{Title}, " +
            "Banned=#{Banned}, Artist=#{Artist} " +
            "WHERE Id=#{id}")
    void update(DbCrypt entity);

    @Delete(("DELETE FROM crypt WHERE Id=#{id}"))
    void delete(Integer id);

}
