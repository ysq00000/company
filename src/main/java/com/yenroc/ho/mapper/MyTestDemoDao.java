package com.yenroc.ho.mapper;

import com.yenroc.ho.mapper.entity.TestDemo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MyTestDemoDao extends BlogCommonMapper<TestDemo> {

}