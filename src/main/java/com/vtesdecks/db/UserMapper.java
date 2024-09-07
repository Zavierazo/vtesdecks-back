package com.vtesdecks.db;

import com.vtesdecks.db.model.DbUser;
import com.vtesdecks.enums.CacheEnum;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

@Mapper
public interface UserMapper {


    @Select("SELECT * FROM user WHERE email = #{email}")
    @Cacheable(value = CacheEnum.DB_USER_CARD_CACHE, key = "'deck_card_selectByEmail_'+#a0")
    DbUser selectByEmail(String email);

    @Select("SELECT * FROM user WHERE username = #{username}")
    @Cacheable(value = CacheEnum.DB_USER_CARD_CACHE, key = "'deck_card_selectByUserName_'+#a0")
    DbUser selectByUserName(String username);

    @Select("SELECT * FROM user WHERE login_hash = #{loginHash}")
    @Cacheable(value = CacheEnum.DB_USER_CARD_CACHE, key = "'deck_card_selectByHash_'+#a0")
    DbUser selectByHash(String loginHash);

    @Select("SELECT * FROM user WHERE id = #{id}")
    @Cacheable(value = CacheEnum.DB_USER_CARD_CACHE, key = "'deck_card_selectById_'+#a0")
    DbUser selectById(Integer id);

    @Insert("INSERT INTO user (username,email,password,login_hash,display_name,profile_image,validated,admin,tester)"
            + "VALUES(#{username},#{email},#{password},#{loginHash},#{displayName},#{profileImage},#{validated},#{admin},#{tester})")
    @Caching(evict = {
            @CacheEvict(value = CacheEnum.DB_USER_CARD_CACHE, key = "'deck_card_selectByEmail_'+#a0.email"),
            @CacheEvict(value = CacheEnum.DB_USER_CARD_CACHE, key = "'deck_card_selectByUserName_'+#a0.username"),
            @CacheEvict(value = CacheEnum.DB_USER_CARD_CACHE, key = "'deck_card_selectByHash_'+#a0.loginHash")
    })
    void insert(DbUser entity);

    @Update("UPDATE user SET password = #{password}, login_hash = #{loginHash}, display_name = #{displayName}, profile_image = #{profileImage}, validated = #{validated}, admin = #{admin}, tester = #{tester}, forgot_password_date = #{forgotPasswordDate} "
            + "WHERE id=#{id}")
    @Caching(evict = {
            @CacheEvict(value = CacheEnum.DB_USER_CARD_CACHE, key = "'deck_card_selectByEmail_'+#a0.email"),
            @CacheEvict(value = CacheEnum.DB_USER_CARD_CACHE, key = "'deck_card_selectByUserName_'+#a0.username"),
            @CacheEvict(value = CacheEnum.DB_USER_CARD_CACHE, allEntries = true),
            @CacheEvict(value = CacheEnum.DB_USER_CARD_CACHE, key = "'deck_card_selectById_'+#a0.id")
    })
    void update(DbUser entity);
}
