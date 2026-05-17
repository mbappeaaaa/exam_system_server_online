package com.atguigu.exam.mapper;


import com.atguigu.exam.entity.Question;
import com.atguigu.exam.vo.QuestionQueryVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 题目Mapper接口
 * 继承MyBatis Plus的BaseMapper，提供基础的CRUD操作
 */
public interface QuestionMapper extends BaseMapper<Question> {
    @Select("select category_id,count(*) count from questions where questions.is_deleted=0 group by category_id")
    List<Map<String,Long>> selectCategoryQuestionCount();

    /**
     *第一步 分页查询题目列表
     * @param page
     * @param questionQueryVo
     * @return
     */
    IPage<Question> selectQuestionPage(IPage<Question> page,@Param("questionQueryVo") QuestionQueryVo questionQueryVo);
} 