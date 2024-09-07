package com.vtesdecks.db;

import com.vtesdecks.db.model.DbComment;
import com.vtesdecks.enums.CacheEnum;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import java.util.List;

@Mapper
public interface CommentMapper {
    @Select("SELECT * FROM comment WHERE id = #{id}")
    DbComment selectById(Integer id);


    @Select("SELECT count(1) FROM comment WHERE page_identifier = #{pageIdentifier} AND deleted is false")
    Long countByPageIdentifier(String pageIdentifier);

    @Select("SELECT * FROM comment WHERE page_identifier = #{pageIdentifier} ORDER BY creation_date")
    @Cacheable(value = CacheEnum.DB_COMMENT_CACHE, key = "'deck_card_selectByPageIdentifier_'+#a0")
    List<DbComment> selectByPageIdentifier(String pageIdentifier);

    @Insert("INSERT INTO comment (user,parent,page_identifier,content,deleted)"
            + "VALUES(#{user},#{parent},#{pageIdentifier},#{content},#{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    @Caching(evict = {
            @CacheEvict(value = CacheEnum.DB_COMMENT_CACHE, key = "'deck_card_selectByPageIdentifier_'+#a0.pageIdentifier")
    })
    void insert(DbComment entity);

    @Update("UPDATE comment SET user = #{user}, parent = #{parent}, page_identifier = #{pageIdentifier}, content = #{content}, deleted = #{deleted} "
            + "WHERE id=#{id}")
    @Caching(evict = {
            @CacheEvict(value = CacheEnum.DB_COMMENT_CACHE, key = "'deck_card_selectByPageIdentifier_'+#a0.pageIdentifier")
    })
    void update(DbComment entity);
}
