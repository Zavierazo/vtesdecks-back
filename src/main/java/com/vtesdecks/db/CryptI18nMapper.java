package com.vtesdecks.db;

import com.vtesdecks.db.model.DbCryptI18n;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CryptI18nMapper {
    @Select("SELECT * FROM crypt_i18n")
    List<DbCryptI18n> selectAll();

    @Select("SELECT * FROM crypt_i18n WHERE id = #{id}")
    List<DbCryptI18n> selectById(@Param("id") Integer id);

    @Select("SELECT * FROM crypt_i18n WHERE id = #{id} AND locale = #{locale}")
    DbCryptI18n selectByIdAndLocale(@Param("id") Integer id, @Param("locale") String locale);

    @Select("SELECT * FROM crypt_i18n WHERE locale = #{locale}")
    List<DbCryptI18n> selectByLocale(String locale);

    @Select("SELECT Id FROM crypt_i18n WHERE locale = #{locale}")
    List<Integer> selectKeysByLocale(String locale);


    @Insert("INSERT INTO crypt_i18n (id, locale, name, text, image) VALUES (#{id}, #{locale}, #{name}, #{text}, #{image})")
    void insert(DbCryptI18n entity);

    @Update("UPDATE crypt_i18n SET name=#{name}, text=#{text}, image=#{image} WHERE id=#{id} AND locale=#{locale}")
    void update(DbCryptI18n entity);

    @Delete("DELETE FROM crypt_i18n WHERE id=#{id} AND locale=#{locale}")
    void deleteByIdAndLocale(@Param("id") Integer id, @Param("locale") String locale);

}
