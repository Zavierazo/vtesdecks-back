package com.vtesdecks.db;

import com.vtesdecks.db.model.DbSet;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SetMapper {
    @Select("SELECT * FROM `set` ORDER BY Id")
    List<DbSet> selectAll();

    @Select("SELECT * FROM `set` WHERE Id=#{id}")
    DbSet selectById(Integer id);

    @Select("SELECT * FROM `set` WHERE Abbrev = #{abbrev}")
    DbSet selectByAbbreviation(String abbrev);

    @Select("SELECT Company FROM `set` WHERE full_name = #{fullName}")
    String selectCompanyByFullName(String fullName);

    @Insert("INSERT INTO `set` (Id, Abbrev, release_date, full_name, Company)"
            + "VALUES(#{Id}, #{Abbrev}, #{ReleaseDate}, #{FullName}, #{Company})")
    void insert(DbSet entity);

    @Update("UPDATE `set` SET "
            + "Abbrev=#{Abbrev}, Aka=#{Aka}, `release_date`=#{ReleaseDate}, full_name=#{FullName}, Company=#{Company} "
            + "WHERE Id=#{id}")
    void update(DbSet entity);
}
