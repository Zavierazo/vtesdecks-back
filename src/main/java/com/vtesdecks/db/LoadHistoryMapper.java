package com.vtesdecks.db;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.vtesdecks.db.model.DbLoadHistory;

@Mapper
public interface LoadHistoryMapper {

    @Select("SELECT * FROM load_history")
    List<DbLoadHistory> selectAll();

    @Select("SELECT * FROM load_history WHERE script=#{script}")
    DbLoadHistory selectById(String script);

    @Insert("INSERT INTO load_history (script,checksum,execution_time)"
        + "VALUES(#{script},#{checksum},#{executionTime})")
    void insert(DbLoadHistory entity);

    @Update("UPDATE load_history SET checksum=#{checksum}, execution_time=#{executionTime} "
        + "WHERE script=#{script}")
    void update(DbLoadHistory entity);


}
