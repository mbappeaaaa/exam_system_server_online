package com.atguigu.exam.service;

import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.vo.ExamRankingVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;


import java.util.List;

/**
 * 考试记录Service接口
 * 定义考试记录相关的业务方法
 */
public interface ExamRecordService extends IService<ExamRecord> {
    /**
     * 分页查询考试记录
     * @param pageData 分页数据
     * @param studentName 学生名称
     * @param status 考试状态
     * @param startDate 开始时间
     * @param endDate 结束时间
     */
    void pageExamRecords(Page<ExamRecord> pageData, String studentName, Integer status, String startDate, String endDate);
    /**
     * 删除考试记录
     * @param id
     */
    void removeRecord(Integer id);
    /**
     * 获取排行榜数据
     * @param paperId
     * @param limit
     * @return
     */
    List<ExamRankingVO> getExamRanking(Integer paperId, Integer limit);
} 