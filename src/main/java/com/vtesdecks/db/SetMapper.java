package com.vtesdecks.db;

import com.vtesdecks.db.model.DbSet;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SetMapper {
    @Select("SELECT * FROM `set` ORDER BY id")
    List<DbSet> selectAll();

    @Select("SELECT * FROM `set` WHERE id=#{id}")
    DbSet selectById(Integer id);

    @Select("SELECT * FROM `set` WHERE abbrev = #{abbrev}")
    DbSet selectByAbbreviation(String abbrev);

    @Select("SELECT Company FROM `set` WHERE full_name = #{fullName}")
    String selectCompanyByFullName(String fullName);

    @Insert("INSERT INTO `set` (id, abbrev, release_date, full_name, company)"
            + "VALUES(#{Id}, #{Abbrev}, #{ReleaseDate}, #{FullName}, #{Company})")
    void insert(DbSet entity);

    @Update("UPDATE `set` SET "
            + "abbrev=#{Abbrev}, release_date=#{ReleaseDate}, full_name=#{FullName}, company=#{Company} "
            + "WHERE id=#{id}")
    void update(DbSet entity);

    @Delete("DELETE FROM `set` WHERE id=#{id}")
    void deleteById(Integer id);
}
