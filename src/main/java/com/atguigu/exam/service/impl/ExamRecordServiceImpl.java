package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.AnswerRecord;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.mapper.AnswerRecordMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.service.ExamRecordService;
import com.atguigu.exam.service.PaperService;
import com.atguigu.exam.vo.ExamRankingVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 考试记录Service实现类
 * 实现考试记录相关的业务逻辑
 */
@Service
@Slf4j
public class ExamRecordServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements ExamRecordService {
    @Autowired
    private PaperService paperService;
    @Autowired
    private AnswerRecordMapper answerRecordMapper;
    @Autowired
    private ExamRecordMapper examRecordMapper;
    /**
     * 分页查询考试记录
     * @param pageData 分页数据
     * @param studentName 学生名称
     * @param status 考试状态
     * @param startDate 开始时间
     * @param endDate 结束时间
     */
    @Override
    public void pageExamRecords(Page<ExamRecord> pageData, String studentName, Integer status, String startDate, String endDate) {
//        1.正常进行考试记录的单表分页查询
        LambdaQueryWrapper<ExamRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(!ObjectUtils.isEmpty(studentName), ExamRecord::getStudentName, studentName);
        if(!ObjectUtils.isEmpty( status)){
           String strStatus=switch (status){
                case 0 -> "进行中";
                case 1 -> "已完成";
                case 2 -> "已批阅";
                default -> "未知";
            };
           queryWrapper.eq(ExamRecord::getStatus, strStatus);
        }
        queryWrapper.ge(!ObjectUtils.isEmpty(startDate), ExamRecord::getStartTime, startDate);
        queryWrapper.le(!ObjectUtils.isEmpty(endDate), ExamRecord::getEndTime, endDate);
        page(pageData, queryWrapper);
//        2.查询考试记录下的所有试卷对象
        List<ExamRecord> records = pageData.getRecords();
        if(ObjectUtils.isEmpty(records)){
            log.debug("没有查询到考试记录");
            return;
        }
        List<Integer> paperIds = pageData.getRecords().stream().map(ExamRecord::getExamId).toList();
        List<Paper> papers = paperService.listByIds(paperIds);
        Map<Long, Paper> paperMap = papers.stream().collect(Collectors.toMap(Paper::getId, paper -> paper));
//        3.在java代码试卷对象一一赋值给考试对象
        pageData.getRecords().forEach(record -> record.setPaper(paperMap.get(record.getExamId().longValue())));
    }
    /**
     * 删除考试记录
     * @param id
     */
    @Override
    public void removeRecord(Integer id) {
//        状态“进行中”的考试不删除
        ExamRecord examRecord = getById(id);
        if(ObjectUtils.isEmpty(examRecord)){
           log.debug("该考试记录为空");
           return;
        }
        if(examRecord.getStatus().equals("进行中")){
            throw new RuntimeException("进行中的考试不允许删除");
        }
//        删除自身以及答题记录子数据
        removeById(id);
        answerRecordMapper.delete(new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getExamRecordId, id));
    }
    /**
     * 获取排行榜数据
     * @param paperId
     * @param limit
     * @return
     */
    @Override
    public List<ExamRankingVO> getExamRanking(Integer paperId, Integer limit) {
     List<ExamRankingVO> rankingList =examRecordMapper.queryExamRanking(paperId, limit);
        return rankingList;
    }
}