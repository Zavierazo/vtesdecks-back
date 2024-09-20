package com.vtesdecks.db;

import com.vtesdecks.db.model.DbLibraryI18n;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface LibraryI18nMapper {

    @Select("SELECT * FROM library_i18n")
    List<DbLibraryI18n> selectAll();

    @Select("SELECT * FROM library_i18n WHERE id = #{id}")
    List<DbLibraryI18n> selectById(Integer id);

    @Select("SELECT * FROM library_i18n WHERE id = #{id} AND locale = #{locale}")
    DbLibraryI18n selectByIdAndLocale(@Param("id") Integer id, @Param("locale") String locale);

    @Select("SELECT * FROM library_i18n WHERE locale = #{locale}")
    List<DbLibraryI18n> selectByLocale(String locale);

    @Select("SELECT Id FROM library_i18n WHERE locale = #{locale}")
    List<Integer> selectKeysByLocale(String locale);

    @Insert("INSERT INTO library_i18n (id, locale, name, text, image) VALUES (#{id}, #{locale}, #{name}, #{text}, #{image})")
    void insert(DbLibraryI18n crypt);

    @Update("UPDATE library_i18n SET name=#{name}, text=#{text}, image=#{image} WHERE id=#{id} AND locale=#{locale}")
    void update(DbLibraryI18n crypt);

    @Delete("DELETE FROM library_i18n WHERE id=#{id} AND locale=#{locale}")
    void deleteByIdAndLocale(@Param("id") Integer id, @Param("locale") String locale);

}
