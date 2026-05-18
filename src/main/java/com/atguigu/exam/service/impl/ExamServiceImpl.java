package com.atguigu.exam.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.exam.entity.*;
import com.atguigu.exam.mapper.AnswerRecordMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.PaperMapper;
import com.atguigu.exam.service.AnswerRecordService;
import com.atguigu.exam.service.ExamService;
import com.atguigu.exam.service.KimiAiService;
import com.atguigu.exam.service.PaperService;
import com.atguigu.exam.vo.StartExamVo;
import com.atguigu.exam.vo.SubmitAnswerVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 考试服务实现类
 */
@Service
@Slf4j
public class ExamServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements ExamService {
    @Autowired
    private PaperService paperService;
    @Autowired
    private AnswerRecordMapper answerRecordMapper;
    @Autowired
    private AnswerRecordService answerRecordService;
    @Autowired
    private KimiAiService kimiAiService;
    /**
     * 创建和保存考试记录
     * @param startExamVo
     * @return
     */
    @Override
    public ExamRecord saveExam(StartExamVo startExamVo) {
//        1.校验当前考生姓名正在进行考试
        LambdaQueryWrapper<ExamRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ExamRecord::getStudentName, startExamVo.getStudentName());
        //当前试卷的 ID
        queryWrapper.eq(ExamRecord::getExamId, startExamVo.getPaperId());
        queryWrapper.eq(ExamRecord::getStatus, "进行中");
        ExamRecord examRecord = getOne(queryWrapper);
        if (examRecord != null) {
            log.warn("当前考生正在考试中，请勿重复开始考试");
            return examRecord;
        }
        //        2.补全考试记录对象的属性
        examRecord = new ExamRecord();
        examRecord.setExamId(startExamVo.getPaperId());//试卷ID
        examRecord.setStudentName(startExamVo.getStudentName());//考生姓名
        examRecord.setStatus("进行中");//考试状态
        examRecord.setStartTime(LocalDateTime.now());
        examRecord.setWindowSwitches(0);
        //        3.考试记录对象保存并返回对应的记录
        save(examRecord);
        return examRecord;
    }
    /**
     * 获取考试记录详情
     * @param id
     * @return
     */
    @Override
    public ExamRecord getExamRecord(Integer id) {
//        1.根据id查询考试记录对象
        ExamRecord examRecord = getById(id);
        if (examRecord == null) {
            throw new RuntimeException("考试记录不存在");
        }
//        2.根据考试记录对象的试卷id获取试卷信息对象
        Paper paper = paperService.getPaperById(examRecord.getExamId());
        examRecord.setPaper(paper);
//        3.根据记录对象的id查询对应的答题记录集合
        LambdaQueryWrapper<AnswerRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AnswerRecord::getExamRecordId, id);
        List<AnswerRecord> answerRecords = answerRecordMapper.selectList(queryWrapper);
        if(!ObjectUtils.isEmpty(answerRecords)){
            List<Long> questionIds = paper.getQuestions().stream().map(Question::getId).toList();
            answerRecords.sort((o1, o2) -> {
                int index1 = questionIds.indexOf(o1.getQuestionId());
                int index2 = questionIds.indexOf(o2.getQuestionId());
                return Integer.compare(index1, index2);
            });
            examRecord.setAnswerRecords(answerRecords);
        }
        //注意：答题记录的顺序和试卷相同
        return examRecord;
    }
    /**
     * 提交答案
     * @param examRecordId
     * @param answers
     */
    @Override
    public void submitAnswers(Integer examRecordId, List<SubmitAnswerVo> answers) {
//        1将传入的集合转为AnswerRecord对象集合
        if(!ObjectUtils.isEmpty(answers)){
            List<AnswerRecord> answerRecords = answers.stream().map(answer -> {
                AnswerRecord answerRecord = new AnswerRecord();
                answerRecord.setExamRecordId(examRecordId);
                answerRecord.setQuestionId(answer.getQuestionId());
                answerRecord.setUserAnswer(answer.getUserAnswer());
                return answerRecord;
            }).toList();
//         2进行AnswerRecord对象集合批量保存
            answerRecordService.saveBatch(answerRecords);
        }

//        3修改考试记录对象（考试状态已完成+结束时间）
        ExamRecord byId = getById(examRecordId);
        byId.setStatus("已完成");
        byId.setEndTime(LocalDateTime.now());
        updateById(byId);
        //调用判卷业务方法
         gradeExamRecord(examRecordId);
    }
    /**
     * 获取考试记录的判卷结果
     * @param examRecordId
     * @return
     */
    @Override
    public ExamRecord gradeExamRecord(Integer examRecordId) {
        //1.获取考生的试卷信息(考试📝对象，对应的正确但和学生答案)
        ExamRecord examRecord = getExamRecord(examRecordId);
        //2.校验考试记录对应的试卷是否被删除（正确答案没了）
        Paper paper = examRecord.getPaper();
        if(ObjectUtils.isEmpty(paper)){
            examRecord.setStatus("已批阅");
            examRecord.setAnswers("考试对应的题目已删除，无法判卷");
            updateById(examRecord);
            log.warn("考试对应的题目已删除，无法判卷");
            return examRecord;
        }
        //3.获取考试提交记录是否为空
        List<AnswerRecord> answerRecords = examRecord.getAnswerRecords();
        if(ObjectUtils.isEmpty(answerRecords)){
            examRecord.setStatus("已批阅");
            examRecord.setAnswers("考生未提交答案，直接0分");
            examRecord.setScore(0);
            updateById(examRecord);
            log.warn("考生未提交答案，直接0分");
            return examRecord;
        }
        //4.声明来个变量，记录正确的题目数量和总分数
        int correctCount = 0;
        int totalScore = 0;
        //5.将试卷中问题的集合转为map格式
        Map<Long, Question> questionMap = paper.getQuestions().stream().collect(Collectors.toMap(Question::getId, question -> question));
        //6.循环学生的答题记录，在内部进行逐一判题
        //建议：容错处理：但个题目错了，不影响剩余的题目
        for (AnswerRecord answerRecord : answerRecords) {
            //获取答题记录题目
            Question question = questionMap.get(answerRecord.getQuestionId().longValue());
            //答题记录对应的题目被删除了
            if (question == null)continue;
            //获取题目的正确答案信息和学生的答案
           QuestionAnswer questionAnswer = question.getAnswer();
           String answer = questionAnswer.getAnswer();
           String userAnswer = answerRecord.getUserAnswer();
           //判断题，学生答案：T->TRUE F->FALSE
           if("JUDGE".equals(question.getType())){
               userAnswer = convertJudgeAnswer(userAnswer);
           }
            try {
            //1.非简答题
           if(!"TEXT".equals(question.getType())){
               if(userAnswer.equalsIgnoreCase(answer)){
                   answerRecord.setIsCorrect(1);//0错误 1正确 2部分正确
                   answerRecord.setScore(question.getPaperScore().intValue());
               }else{
                   answerRecord.setIsCorrect(0);
                   answerRecord.setScore(0);
               }
             }else{
               String prompt = kimiAiService.buildGradingPrompt(question, userAnswer, question.getPaperScore().intValue());
               String aiCorrection = kimiAiService.callKimiAi(prompt);
               JSONObject jsonObject = JSON.parseObject(aiCorrection);
               //获取AI给的评分
               Integer aiScore = jsonObject.getIntValue("score");
               if(aiScore >= question.getPaperScore().intValue()){
                   answerRecord.setScore(question.getPaperScore().intValue());
                   answerRecord.setIsCorrect(1);
                   answerRecord.setAiCorrection(jsonObject.getString("feedback"));
               } else if (aiScore<=0) {
                   answerRecord.setScore(0);
                   answerRecord.setIsCorrect(0);
                   answerRecord.setAiCorrection(jsonObject.getString("reason"));
               }else{
                   answerRecord.setIsCorrect(2);
                   answerRecord.setScore(aiScore);
                   answerRecord.setAiCorrection(jsonObject.getString("reason"));
               }
               //2.简答题
               answerRecord.setIsCorrect(1);//0错误 1正确 2部分正确
               answerRecord.setScore(question.getPaperScore().intValue());
                }
            } catch (Exception e) {
                //错误处理
                answerRecord.setIsCorrect(0);
                answerRecord.setScore(0);
                answerRecord.setAiCorrection("判断过程中，报错了，直接0分");
             }
            //进行题目数量的累加和得分的累加
            totalScore += answerRecord.getScore();
            if(answerRecord.getIsCorrect() == 1){
                correctCount += answerRecord.getIsCorrect();
            }
          }
            //7.修改每一条学生的答题记录（分数，是否正确，简答题ai评价）
            answerRecordService.updateBatchById(answerRecords);
            //8.调用kimi模型，生成对应的ai调用设置给考试记录对象
            String summaryPrompt = kimiAiService.buildSummaryPrompt(totalScore, paper.getTotalScore().intValue(), paper.getQuestionCount(), correctCount);
            String summary = kimiAiService.callKimiAi(summaryPrompt);
            //9.修改考试记录对象（总分数，总正确题目数）
            examRecord.setScore(totalScore);
            examRecord.setAnswers( summary);
            examRecord.setStatus("已批阅");
            updateById(examRecord);
            //10.返回考试记录对象
            return examRecord;
    }
      //转换判断题答案T->TRUE F->FALSE
      private String convertJudgeAnswer(String answer) {
        String answerUpperCase = answer.toUpperCase();
        switch (answerUpperCase){
            case "T":
            case "TRUE":
            case "对":
            case "正确":
                return "TRUE";
            case "F":
            case "FALSE":
            case "不对":
            case "错误":
                return "FALSE";
            default:
                return answer;
        }
    }
}