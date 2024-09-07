package com.vtesdecks.db;

import java.time.LocalDate;
import java.util.Set;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import com.vtesdecks.db.model.DbVtesdleDay;
import com.vtesdecks.enums.CacheEnum;

@Mapper
public interface VtesdleDayMapper {

    @Select("SELECT card_id FROM vtesdle_day WHERE day >= current_date - interval '1' year")
    Set<Integer> selectCardsLastYear();

    @Select("SELECT * FROM vtesdle_day WHERE day = #{day}")
    @Cacheable(value = CacheEnum.DB_VTESDLE_CACHE, key = "'vtesdle_day_selectByDay_'+#a0")
    DbVtesdleDay selectByDay(LocalDate day);

    @Insert("INSERT INTO vtesdle_day (day,card_id) VALUES(#{day},#{cardId})")
    @Caching(evict = {
        @CacheEvict(value = CacheEnum.DB_VTESDLE_CACHE, key = "'vtesdle_day_selectByDay_'+#a0.day")
    })
    void insert(DbVtesdleDay entity);
}
