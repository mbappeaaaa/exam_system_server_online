package com.atguigu.exam.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.exam.common.CacheConstants;
import com.atguigu.exam.entity.PaperQuestion;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.entity.QuestionChoice;
import com.atguigu.exam.mapper.*;
import com.atguigu.exam.service.KimiAiService;
import com.atguigu.exam.service.QuestionService;
import com.atguigu.exam.utils.ExcelUtil;
import com.atguigu.exam.utils.RedisUtils;
import com.atguigu.exam.vo.AiGenerateRequestVo;
import com.atguigu.exam.vo.QuestionImportVo;
import com.atguigu.exam.vo.QuestionQueryVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目Service实现类
 * 实现题目相关的业务逻辑
 */
@Slf4j
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {
    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private QuestionChoiceMapper questionChoiceMapper;
    @Autowired
    private QuestionAnswerMapper questionAnswerMapper;
    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private PaperQuestionMapper paperQuestionMapper;
    @Autowired
    private KimiAiService kimiAiService;
    /**
     * 分页查询题目列表方案二：分布查询
     * @param questionPage
     * @param questionQueryVo
     */
    @Override
    public void getQuestionList(Page<Question> questionPage, QuestionQueryVo questionQueryVo) {
//        questionMapper.selectQuestionPage(questionPage, questionQueryVo);
    }
    /**
     * 分页查询题目列表方案三：java代码处理
     * @param questionPage
     * @param questionQueryVo
     */
    @Override
    public void getQuestionListByStream(Page<Question> questionPage, QuestionQueryVo questionQueryVo) {
//      1.题目单表的分页+动态条件查询(mybatis-plus)
        LambdaQueryWrapper<Question> questionLambdaQueryWrapper = new LambdaQueryWrapper<>();
        questionLambdaQueryWrapper.eq(questionQueryVo.getCategoryId()!=null,Question::getCategoryId,questionQueryVo.getCategoryId());
        questionLambdaQueryWrapper.eq(ObjectUtils.isNotEmpty(questionQueryVo.getDifficulty()),Question::getDifficulty,questionQueryVo.getDifficulty());
        questionLambdaQueryWrapper.eq(ObjectUtils.isNotEmpty(questionQueryVo.getType()),Question::getType,questionQueryVo.getType());
        questionLambdaQueryWrapper.like(ObjectUtils.isNotEmpty(questionQueryVo.getKeyword()),Question::getTitle,questionQueryVo.getKeyword());
        questionLambdaQueryWrapper.orderByDesc(Question::getCreateTime);
        page(questionPage,questionLambdaQueryWrapper);
        //进行判断
        if (ObjectUtils.isEmpty(questionPage.getRecords())){
            log.debug("没有查询到题目列表");
            return;
        }
        fullQuestionChoiceAndAnswer(questionPage.getRecords());
    }
    //批量查询
    private void fullQuestionChoiceAndAnswer(List< Question> questionList) {
        //        2.查询题目对应的所有的选项和答案
        //不循环题目集合，我们一次查询所有的选项和答案，再进行java代码处理（避免1+n问题）
        //获取所有的题目ID
        List<Long> questionIds = questionList.stream().map(Question::getId).toList();
        //查询所有的选项
        LambdaQueryWrapper<QuestionChoice> questionChoiceLambdaQueryWrapper = new LambdaQueryWrapper<>();
        questionChoiceLambdaQueryWrapper.in(QuestionChoice::getQuestionId,questionIds);
        List<QuestionChoice> questionChoiceList = questionChoiceMapper.selectList(questionChoiceLambdaQueryWrapper);
        //查询所有的答案
        LambdaQueryWrapper<QuestionAnswer> questionAnswerLambdaQueryWrapper = new LambdaQueryWrapper<>();
        questionAnswerLambdaQueryWrapper.in(QuestionAnswer::getQuestionId,questionIds);
        List<QuestionAnswer> questionAnswerList = questionAnswerMapper.selectList(questionAnswerLambdaQueryWrapper);
//       3.题目的选项和答案的集合转为map格式
        //获取所有的答案转为 map
        Map<Long, QuestionAnswer> questionAnswerMap = questionAnswerList.stream().collect(Collectors.toMap(QuestionAnswer::getQuestionId, a -> a));
        //获取所有的选项转为 map
        Map<Long, List<QuestionChoice>> questionChoiceMap = questionChoiceList.stream().collect(Collectors.groupingBy(QuestionChoice::getQuestionId));
//        4.循环题目列表，进行选项和答案的赋值工作
        questionList.forEach(question -> {
            //给答案赋值
            question.setAnswer(questionAnswerMap.get(question.getId()));
            //给选项赋值(只有选择题才有选项)
            if("CHOICE".equals(question.getType())){
                //只要是选项的问题，必须要考虑排序的问题
                List<QuestionChoice> questionChoices = questionChoiceMap.get(question.getId());
                if (!ObjectUtils.isEmpty(questionChoices)){
                    questionChoices.sort(Comparator.comparing(QuestionChoice::getSort));
                    question.setChoices(questionChoices);
                }}
        });
    }

    /**
     * 题目+答案+选项
     * @param id
     * @return
     */
    @Override
    public Question queryQuestionById(Long id) {
//        根据id查询题目
        Question question = getById(id);
        if (ObjectUtils.isEmpty(question)){
            //log.debug("没有查询到题目ID为{}的题目详情",id);
           throw new RuntimeException("没有查询到题目详情");
        }
//        根据id查询题目对应的答案
        QuestionAnswer questionAnswer = questionAnswerMapper.selectOne(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getQuestionId,id));
        question.setAnswer(questionAnswer);
//        根据id查询对应的选项（排序）
        if("CHOICE".equals(question.getType())){
            List<QuestionChoice> questionChoices = questionChoiceMapper.selectList(new LambdaQueryWrapper<QuestionChoice>().eq(QuestionChoice::getQuestionId,id).orderByAsc(QuestionChoice::getSort));
            question.setChoices(questionChoices);
        }

//        整合redis配置
//        做zset缓存
        new Thread(() -> {
            incremtntQuestionScore(id);
        }).start();
        return question;
    }
    @Async
    public void incremtntQuestionScore(Long questionId){
        try {
            Double score = redisUtils.zIncrementScore(CacheConstants.POPULAR_QUESTIONS_KEY, questionId, 1);
            log.debug("题目ID为{}的题目的热榜分数为{}",questionId,score);
        } catch (Exception e) {
            log.error("更新题目热度分数失败，题目ID: {}", questionId, e);
        }
    }
    /**
     * 保存题目
     * @param question
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveQuestion(Question question) {
//    先判断不能重复
        LambdaQueryWrapper<Question> questionLambdaQueryWrapper = new LambdaQueryWrapper<>();
        questionLambdaQueryWrapper.eq(Question::getType,question.getType());
        questionLambdaQueryWrapper.eq(Question::getTitle,question.getTitle());
        long count = count(questionLambdaQueryWrapper);
        if (count>0){
            throw new RuntimeException("该题目已存在");
        }
//   保存题目信息
        save(question);
//   判断是不是选择题：是的话根据选项的正确答案赋值，同时插入到选项表
        QuestionAnswer answer = question.getAnswer();
        answer.setQuestionId(question.getId());
        if ("CHOICE".equals(question.getType())) {
            List<QuestionChoice> choices = question.getChoices();
            StringBuilder answerContent = new StringBuilder();//拼接正确答案
            for(int i = 0; i < choices.size(); i++){
                QuestionChoice choice = choices.get(i);
                choice.setSort(i);
                choice.setQuestionId(question.getId());
                //保存选项信息
                questionChoiceMapper.insert(choice);
                if (choice.getIsCorrect()){
                    if(answerContent.length()>0){
                        answerContent.append(",");
                    }
                    answerContent.append((char)('A' + i));
                }
            }
            answer.setAnswer(answerContent.toString());
           }
        //成答案数据的插入
        questionAnswerMapper.insert(answer);
    }
    /**
     * 更新题目
     * @param id
     * @param question
     */
    @Override
    public void updateQuestion(Long id, Question question) {
//        判断
        LambdaQueryWrapper<Question> questionLambdaQueryWrapper = new LambdaQueryWrapper<>();
        questionLambdaQueryWrapper.eq(Question::getId,id);
        questionLambdaQueryWrapper.eq(Question::getTitle,question.getType());
        long count = count(questionLambdaQueryWrapper);
        if (count>0){
            throw new RuntimeException("修改题目已存在");
        }
//        题目信息的更新
        updateById(question);
//        获取答案对象
        QuestionAnswer questionAnswer = question.getAnswer();
//        判断是不是选择题（先删除+再添加+拼接+给选择题的答案赋值）
        if ("CHOICE".equals(question.getType())) {
            List<QuestionChoice> choices = question.getChoices();
            //删除原来的选项
            questionAnswerMapper.delete(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getQuestionId,id));
            //接收新的答案
            StringBuilder answerContent = new StringBuilder();
            for(int i = 0; i < choices.size(); i++){
                QuestionChoice choice = choices.get(i);
                choice.setId(null);
                choice.setCreateTime(null);
                choice.setSort(i);
                choice.setQuestionId(question.getId());
                questionChoiceMapper.insert(choice);
                if (choice.getIsCorrect()){
                    if(answerContent.length()>0){
                        answerContent.append(",");
                    }
                    answerContent.append((char)('A' + i));
                }
            }
            questionAnswer.setAnswer(answerContent.toString());
        }
//        进行答案对象的更新
        questionAnswerMapper.updateById(questionAnswer);
    }
    /**
     * 删除题目
     * @param id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeQuestion(Long id) {
//        检查是否有关联的试卷题目，有删除失败
        LambdaQueryWrapper<PaperQuestion> questionLambdaQueryWrapper = new LambdaQueryWrapper<>();
        questionLambdaQueryWrapper.eq(PaperQuestion::getQuestionId,id);
        Long count = paperQuestionMapper.selectCount(questionLambdaQueryWrapper);
        if (count>0){
            throw new RuntimeException("该题目有关联的试卷，请先删除关联的试卷");
        }
//        删除题目本身
        removeById(id);
//        删除关联的子数据，答案，选项
        questionChoiceMapper.delete(new LambdaQueryWrapper<QuestionChoice>().eq(QuestionChoice::getQuestionId,id));
        questionAnswerMapper.delete(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getQuestionId,id));
//        添加事务
    }
     /**
     * 获取热门题目
     * @param size
     * @return
     */
    @Override
    public List<Question> getPopularQuestions(Integer size) {
        //定义一个热门题目集合
        List<Question> popularQuestions = new ArrayList<>();
//        查询redis中缓存的题目id(按照访问的分值倒序)
        Set<Object> popularQuestionIds = redisUtils.zReverseRange(CacheConstants.POPULAR_QUESTIONS_KEY, 0, size - 1);
//        根据查询的id查询对应的热门题目集合
        if (!popularQuestionIds.isEmpty()) {
            List<Long> longs = popularQuestionIds.stream().map(id -> Long.valueOf(id.toString())).toList();
              //可能热门题目的顺序不一致，所以需要重新排序
//            List<Question> questionList = listByIds(longs);
            for(Long id:longs){
                Question question = getById(id);
                //检验，id有，redis中缓存的id有，但是题目不存在
                if (question != null) {
                    popularQuestions.add(question);
                }
            }
        }
//        检查热门题目的数量是否满足六size
        int diff = size - popularQuestions.size();
//        不满足需要自己补充(查询最新的题目)
        if (diff > 0) {
            LambdaQueryWrapper<Question> questionLambdaQueryWrapper = new LambdaQueryWrapper<Question>().orderByDesc(Question::getCreateTime);
            //对已有的id进行过滤
            List<Long> existQuestionsIds = popularQuestions.stream().map(Question::getId).toList();
            questionLambdaQueryWrapper.notIn(ObjectUtils.isEmpty(existQuestionsIds),Question::getId,existQuestionsIds);
            //切割指定的diff条
            questionLambdaQueryWrapper.last("limit " + diff);
            List<Question> newQuestionList = questionMapper.selectList(questionLambdaQueryWrapper);
            popularQuestions.addAll(newQuestionList);
        }
//        给题目进行选项和答案的赋值
        fullQuestionChoiceAndAnswer(popularQuestions);
        return  popularQuestions;
    }
    /**
     * 预览导入的Excel数据
     * @param file
     * @return
     */
    @Override
    public List<QuestionImportVo> previewExcel(MultipartFile file) throws IOException {
//        校验文件是否为空和格式是否正确
        if (file.isEmpty() || !file.getOriginalFilename().endsWith("xlsx")&&!file.getOriginalFilename().endsWith("xls") ) {
            throw new RuntimeException("请选择正确的文件");
        }
//        使用工具类解析文件拿到对应的vo集合
        List<QuestionImportVo> questionImportVos = ExcelUtil.parseExcel(file);
//        返回对应的结果
        return questionImportVos;
    }
    /**
     * 导入题目
     * @param questions
     * @return
     */
    @Override
    public String importQuestions(List<QuestionImportVo> questions) {
//        1.将前端传递的预览数据集合List<QuestionImportVo>questionImportVos进行非空判断
        if (ObjectUtils.isEmpty(questions)) {
            return "导入失败，请选择文件,该文件为空";
        }
//        2.编写服务降级的代码
        int successCount = 0;//成功的题目数量
        for(QuestionImportVo questionImportVo:questions){
            try {
//        3.try->questionImportVo->question进行题目保存，复用题目保存的业务
                Question question = new Question();
                BeanUtils.copyProperties(questionImportVo,question);
                if("ChOICE".equals(questionImportVo.getType())){
                    List<QuestionChoice> choices =new ArrayList<>(questionImportVo.getChoices().size());
                    for(QuestionImportVo.ChoiceImportDto choiceImport:questionImportVo.getChoices()){
                        QuestionChoice choice = new QuestionChoice();
                        choice.setContent(choiceImport.getContent());
                        choice.setIsCorrect(choiceImport.getIsCorrect());
                        choice.setSort(choiceImport.getSort());
                        choice.setQuestionId(question.getId());
                        choices.add(choice);
                    }
                    question.setChoices(choices);
                }
                   QuestionAnswer questionAnswer = new QuestionAnswer();
                //如果是判断题：questionAnswer.getAnswer() true false是小写，数据库中存储的是大写 前端没有忽略大小写
                if("JUDGE".equals(questionImportVo.getType())){
                    questionAnswer.setAnswer(questionImportVo.getAnswer().toUpperCase());
                }else {
                    questionAnswer.setAnswer(questionImportVo.getAnswer());
                }
                  questionAnswer.setKeywords(questionImportVo.getKeywords());
                  question.setAnswer(questionAnswer);
                this.saveQuestion(question);//保存题目
                  successCount++;//成功的题目数量+1
                }catch (Exception e){
                log.error("导入题目失败：{}",questionImportVo.getTitle());
            }
        }
//        4.拼接返回的字符串
        String result = "成功导入" + successCount + "条数据";
        return result;
    }
    /**
     * 通过AI生成题目
     * @param request
     * @return
     */
    @Override
    public List<QuestionImportVo> generateQuestionsByAi(AiGenerateRequestVo request) {
        //1生成对应的提示词
        String prompt = kimiAiService.buildPrompt(request);
        log.debug("生成提示词：{}",prompt);
        //2调用AI接口
        String response = kimiAiService.callKimiAi(prompt);
        //3解析AI返回的json数据
        //3.1判定开始（```json）和结束(```)的字符串位置
        int start = response.indexOf("```json");
        int end = response.lastIndexOf("```");
        if (start != -1 && end != -1&& end > start) {
            //start + 7是排除```json本身的内容
            String json = response.substring(start + 7, end);
            JSONObject jsonObject = JSONObject.parseObject(json);
            JSONArray questions = jsonObject.getJSONArray("questions");
            List<QuestionImportVo> questionImportVos =new ArrayList<>();
            for(int i = 0;i < questions.size();i++){
                JSONObject itemObject = questions.getJSONObject(i);
                QuestionImportVo questionImportVo = new QuestionImportVo();
                questionImportVo.setTitle(itemObject.getString("title"));
                questionImportVo.setType(itemObject.getString("type"));
                questionImportVo.setMulti(itemObject.getBoolean("multi"));
                questionImportVo.setCategoryId(request.getCategoryId());
                questionImportVo.setDifficulty(itemObject.getString("difficulty"));
                questionImportVo.setScore(itemObject.getInteger("score"));
                questionImportVo.setAnalysis(itemObject.getString("analysis"));
                questionImportVo.setAnswer(itemObject.getString("answer"));
                //选择题的选项
                if("CHOICE".equals(questionImportVo.getType())){
                    JSONArray choicesArray = itemObject.getJSONArray("choices");
                    List<QuestionImportVo.ChoiceImportDto> choices = new ArrayList<>();
                    for(int j = 0;j < choicesArray.size();j++){
                        QuestionImportVo.ChoiceImportDto choiceImportDto = new QuestionImportVo.ChoiceImportDto();
                        choiceImportDto.setContent(choicesArray.getJSONObject(j).getString("content"));
                        choiceImportDto.setIsCorrect(choicesArray.getJSONObject(j).getBoolean("isCorrect"));
                        choiceImportDto.setSort(choicesArray.getJSONObject(j).getInteger("sort"));
                        choices.add(choiceImportDto);

                    }
                    questionImportVo.setChoices(choices);
                }
                questionImportVos.add(questionImportVo);
            }
            return questionImportVos;
        }
        throw new RuntimeException("AI生成题目结果结构错误，解析失败，具体数据为:%s".formatted(response));

    }
}