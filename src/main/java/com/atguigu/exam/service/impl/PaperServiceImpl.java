package com.atguigu.exam.service.impl;


import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.entity.PaperQuestion;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.PaperMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.PaperQuestionService;
import com.atguigu.exam.service.PaperService;
import com.atguigu.exam.vo.AiPaperVo;
import com.atguigu.exam.vo.PaperVo;
import com.atguigu.exam.vo.RuleVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
/**
 * 试卷服务实现类
 */
@Slf4j
@Service
public class PaperServiceImpl extends ServiceImpl<PaperMapper, Paper> implements PaperService {
    @Autowired
    private PaperQuestionService paperQuestionService;
    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private ExamRecordMapper examRecordMapper;
    /**
     * 创建试卷
     * @param paperVo 试卷信息
     * @return 创建的试卷
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Paper createPaper(PaperVo paperVo) {
        //1.完善试卷信息
        Paper paper = new Paper();
        BeanUtils.copyProperties(paperVo, paper);
        paper.setStatus("DRAFT");//设置默认状态
         //判断是否传入题目信息
        if (ObjectUtils.isEmpty(paperVo.getQuestions())) {
            paper.setQuestionCount(0);
            paper.setTotalScore(BigDecimal.ZERO);
            //保存试卷
            save(paper);
            log.warn("创建的试卷：{}没有题目，不能进行考试",paper);
            return paper;
        }
        //总题目数
        paper.setQuestionCount(paperVo.getQuestions().size());
         //总分数
        Optional<BigDecimal> totalScore = paperVo.getQuestions().values().stream().reduce(BigDecimal::add);
//        paperVo.getQuestions().values().stream().mapToInt(BigDecimal->BigDecimal.intValue()).sum();
        paper.setTotalScore(totalScore.get());
        //2.保存试卷
        save(paper);
        log.debug("创建的试卷：{}有题目信息正常进行考试",paper);
        //3.判断试卷是否携带了题目集合，如果有，进行中间表处理
        //4.把题目集合的map转为试卷-题目中间表
        List<PaperQuestion> paperQuestionList = paperVo.getQuestions().entrySet().stream()
                .map(entry ->
                        new PaperQuestion(paper.getId().intValue(), Long.valueOf(entry.getKey()), entry.getValue()))
                .toList();
        //5.试卷-题目中间表集合批量插入
        paperQuestionService.saveBatch(paperQuestionList);
        //6.返回创建的试卷对象信息
        return paper;
    }
    /**
     * 智能组卷
     * @param aiPaperVo
     * @return
     */
    @Override
    public Paper createPaperWithAI(AiPaperVo aiPaperVo) {
//        1.完成试卷基本信息保存从而获取主键id
          Paper paper = new Paper();
          BeanUtils.copyProperties(aiPaperVo, paper);
          paper.setStatus("DRAFT");//设置默认状态
          save(paper);
//        2.循环每个规则，在规则下随机获取符合条件数量的题目
        //总题目数量
          int totalQuestionCount = 0;
        //总分数
          BigDecimal totalScore = BigDecimal.ZERO;
          for (RuleVo ruleVo : aiPaperVo.getRules()) {
            if (ruleVo.getCount() == 0) {
                log.debug("规则{}没有题目数量，请检查",ruleVo.getType().name());
                continue;
            }
//        3.在循环规则中，计算当前规则的题目数和总分数
            LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Question::getType, ruleVo.getType().name());
            if(ObjectUtils.isNotEmpty(ruleVo.getCategoryIds())){
                queryWrapper.in(Question::getCategoryId, ruleVo.getCategoryIds());
            }
            //获取当前规则下符合条件所有的题目集合
            List<Question> questionAllList = questionMapper.selectList(queryWrapper);
            //检验符合条件所有的题目集合是否为空
            if (ObjectUtils.isEmpty(questionAllList)) {
                log.debug("规则{}没有满足条件的题目，请检查",ruleVo.getType().name());
                continue;
            }
           //在存在的规则下满足条件的题目数量和规则要求的数量进行对比，谁小要谁
            int realCount = Math.min(ruleVo.getCount(), questionAllList.size());
//        4.将随机获取的集合转为paperQuestion对象，保存到试卷-题目中间表
            totalQuestionCount+=realCount;//总题目数累加
            totalScore = totalScore.add(BigDecimal.valueOf((long)realCount*ruleVo.getScore()));
            //随机选出符合规则的题目集合
//            打乱原有的题目集合-洗牌
            Collections.shuffle(questionAllList);
            List<Question> subList = questionAllList.subList(0, realCount);
            //转为题目-答案中，中间表对象集合再进行批量保存
            List<PaperQuestion> paperQuestionList = subList.stream()
            .map(question -> new PaperQuestion(paper.getId().intValue(), question.getId(), BigDecimal.valueOf(ruleVo.getScore()))).toList();
            paperQuestionService.saveBatch(paperQuestionList);
        }
//        5.规则统计完后，进行分数和题目数量的修改
           paper.setQuestionCount(totalQuestionCount);
           paper.setTotalScore(totalScore);
//        6.修改试卷对象并返回
           updateById(paper);
           return paper;
    }
    /**
     * 更新试卷
     * @param id
     * @param paperVo
     * @return
     */
    @Override
    public Paper updatePaper(Integer id, PaperVo paperVo) {
        Paper paper = getById(id);
//        校验：发布状态的不能更新
        if ("PUBLISHED".equals(paper.getStatus())) {
            throw new RuntimeException("当前试卷已发布，不能更新");
        }
//             名字不能和其他试卷重复
        LambdaQueryWrapper<Paper> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.ne(Paper::getId, id);
        queryWrapper.eq(Paper::getName, paperVo.getName());
        if (count(queryWrapper) > 0) {
            throw new RuntimeException("当前试卷名称已存在，请勿重复添加");
        }
        //新的属性覆盖给旧属性
        BeanUtils.copyProperties(paperVo, paper);
        //总题目数
        paper.setQuestionCount(paperVo.getQuestions().size());
        //总分数
        Optional<BigDecimal> totalScore = paperVo.getQuestions().values().stream().reduce(BigDecimal::add);
//        paperVo.getQuestions().values().stream().mapToInt(BigDecimal->BigDecimal.intValue()).sum();
        paper.setTotalScore(totalScore.get());
        //试卷表是对一的关系，直接更新
        updateById(paper);
        //试卷题目中间表对多的关系，先删除在插入
        LambdaQueryWrapper<PaperQuestion> queryWrapper2 = new LambdaQueryWrapper<>();
        queryWrapper2.eq(PaperQuestion::getPaperId, id);
        paperQuestionService.remove(queryWrapper2);
        List<PaperQuestion> paperQuestionList = paperVo.getQuestions().entrySet().stream()
                .map(entry ->
                        new PaperQuestion(paper.getId().intValue(), Long.valueOf(entry.getKey()), entry.getValue()))
                .toList();
        paperQuestionService.saveBatch(paperQuestionList);
        return paper;
    }
    /**
     * 删除试卷
     * @param id
     */
    @Override
    public void removePaper(Integer id) {
//        前置校验检查：
        Paper paper = getById(id);
//           自身如果是发布状态：不能删除
        if ("PUBLISHED".equals(paper.getStatus())) {
            throw new RuntimeException("当前试卷已发布，不能删除");
        }
//           检查考试记录中是否引用我们的试卷，有也不能删除
        LambdaQueryWrapper<ExamRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ExamRecord::getExamId, id);
        if (examRecordMapper.selectCount(queryWrapper) > 0) {
            throw new RuntimeException("当前试卷有考试记录引用，不能删除");
        }
//        删除自身
        removeById(id);
//        删除子数据（删除试卷和题目的中间表子数据）
        LambdaQueryWrapper<PaperQuestion> queryWrapper2 = new LambdaQueryWrapper<>();
        queryWrapper2.eq(PaperQuestion::getPaperId, id);
        paperQuestionService.remove(queryWrapper2);
    }
    /**
     * 根据id查询试卷
     * @param id
     * @return
     */
    @Override
    public Paper getPaperById(Integer id) {
//        1.根据id查询试卷对象
        Paper paper = getById(id);
//        2.在questionMapper定义一个多表查询的方法，根据试卷id查询对应的题目集合
        List<Question> questionList = questionMapper.selectQuestionList(id);
        if(ObjectUtils.isEmpty(questionList)){
            log.warn("当前试卷没有题目，请检查");
            return paper;
        }
//        3.对题目进行排序处理
        questionList.sort((o1, o2)->Integer.compare(typeToInt(o1.getType()),typeToInt(o2.getType())));
//        4.题目集合赋予试卷对象
        paper.setQuestions(questionList);
//        5.返回完整的试卷对象
        return paper;
    }
    private int typeToInt(String type) {
        switch (type){
            case "CHOICE": return 1;
            case "JUDGE": return 2;
            case "TEXT": return 3;
            default: return 0;
        }
    }
}