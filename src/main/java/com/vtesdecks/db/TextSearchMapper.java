package com.vtesdecks.db;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.vtesdecks.db.model.DbTextSearch;

@Mapper
public interface TextSearchMapper {

    @Select("<script>" +
        "SELECT id, name, score " +
        "FROM(SELECT id, name, MATCH(name, aka) AGAINST(#{name} IN NATURAL LANGUAGE MODE) AS score FROM crypt " +
        "WHERE MATCH(name, aka) AGAINST(#{name} IN NATURAL LANGUAGE MODE) " +
        "<if test=\"advanced != null\">AND adv = #{advanced} </if> " +
        "UNION ALL " +
        "SELECT id, name, MATCH(name, aka) AGAINST(#{name} IN NATURAL LANGUAGE MODE) AS score FROM library " +
        "WHERE MATCH(name, aka) AGAINST(#{name} IN NATURAL LANGUAGE MODE)) AS cards " +
        "WHERE cards.score > 5.0 " +
        "ORDER BY cards.score DESC" +
        "</script>")
    List<DbTextSearch> search(@Param("name") String name, @Param("advanced") Boolean advanced);


}
