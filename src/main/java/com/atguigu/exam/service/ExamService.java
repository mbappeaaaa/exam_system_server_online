package com.atguigu.exam.service;

import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.vo.StartExamVo;
import com.atguigu.exam.vo.SubmitAnswerVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 考试服务接口
 */
public interface ExamService extends IService<ExamRecord> {
    /**
     * 创建和保存考试记录
     * @param startExamVo
     * @return
     */
    ExamRecord saveExam(StartExamVo startExamVo);

    /**
     * 获取考试详情
     * @param id
     * @return
     */
    ExamRecord getExamRecord(Integer id);
    /**
     * 提交答案并进行AI判卷
     * @param examRecordId
     * @param answers
     */
    void submitAnswers(Integer examRecordId, List<SubmitAnswerVo> answers);
    /**
     * AI智能判卷
     * @param examRecordId
     * @return
     */
    ExamRecord gradeExamRecord(Integer examRecordId);
}
 