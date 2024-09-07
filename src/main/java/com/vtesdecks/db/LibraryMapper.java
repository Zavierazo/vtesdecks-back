package com.vtesdecks.db;

import com.vtesdecks.db.model.DbLibrary;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface LibraryMapper {
    @Select("SELECT * FROM library ORDER BY Id")
    List<DbLibrary> selectAll();

    @Select("SELECT * FROM library WHERE Id=#{id}")
    DbLibrary selectById(Integer id);

    @Select("SELECT * FROM library WHERE name = #{name} or aka = #{name}")
    DbLibrary selectByName(String name);

    @Insert("INSERT INTO library (Id,Name,Aka,`Type`,Clan,Discipline,Pool_Cost,Blood_Cost,Conviction_Cost,Burn,Text," +
            "Flavor,`Set`,Requirement,Banned,Artist,Capacity)"
            + "VALUES(#{Id}, #{Name}, #{Aka}, #{Type}, #{Clan}, #{Discipline}, #{PoolCost}, #{BloodCost}, " +
            "#{ConvictionCost}, #{Burn}, #{Text}, #{Flavor}, #{Set}, #{Requirement}, #{Banned}, #{Artist}, " +
            "#{Capacity})")
    void insert(DbLibrary entity);

    @Update("UPDATE library SET Name=#{Name}, Aka=#{Aka}, `Type`=#{Type}, Clan=#{Clan}, Discipline=#{Discipline}, " +
            "Pool_Cost=#{PoolCost}, Blood_Cost=#{BloodCost}, Conviction_Cost=#{ConvictionCost}, Burn=#{Burn}, " +
            "Text=#{Text}, Flavor=#{Flavor}, `Set`=#{Set}, Requirement=#{Requirement}, Banned=#{Banned}, " +
            "Artist=#{Artist}, Capacity=#{Capacity} "
            + "WHERE Id=#{id}")
    void update(DbLibrary entity);

    @Delete(("DELETE FROM library WHERE Id=#{id}"))
    void delete(Integer id);
}
