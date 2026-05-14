package com.atguigu.exam.service.impl;
import com.atguigu.exam.entity.Category;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.mapper.CategoryMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.CategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Slf4j
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper,Category> implements CategoryService {
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private QuestionMapper questionMapper;
    /**
     * 获取分类列表
     * @return
     */
    @Override
    public List<Category> findCategoryList() {
        //查询所有分类,单表操作，没有数量统计
        LambdaQueryWrapper<Category> queryWrapper=new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(Category::getSort);
        List<Category> categoryList = list(queryWrapper);//没有count
        log.info("获取分类列表成功：{}",categoryList);
        //查询每个分类下的题目数量
        //{map{category_id:10,count:1}}
        List<Map<String, Long>> mapList = questionMapper.selectCategoryQuestionCount();
        //mapList-> map 降低时间复杂度
        Map<Long, Long> collect = mapList.stream().collect(Collectors.toMap(m -> m.get("category_id"), m -> m.get("count")));
        for(Category category:categoryList){
            Long id=category.getId();
//            for(Map<String,Long> map:mapList){
//                Long categoryId=map.get("category_id");
//                if(id==categoryId){
//                    category.setCount(map.get("count"));
//                }
//            }
            category.setCount(collect.getOrDefault(id,0L));
        }
          return categoryList;
    }
    /**
     * 获取树状分类
     * @return
     */
    @Override
    public List<Category> findCategoryListTree() {
        //查询所有分类,单表操作，没有数量统计
        LambdaQueryWrapper<Category> queryWrapper=new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(Category::getSort);
        List<Category> categoryList = list(queryWrapper);//没有count
        log.info("获取分类列表成功：{}",categoryList);
        //查询每个分类下的题目数量
        //{map{category_id:10,count:1}}
        List<Map<String, Long>> mapList = questionMapper.selectCategoryQuestionCount();
        //mapList-> map 降低时间复杂度
        Map<Long, Long> collect = mapList.stream().collect(Collectors.toMap(m -> m.get("category_id"), m -> m.get("count")));
        for(Category category:categoryList){
            Long id=category.getId();
            category.setCount(collect.getOrDefault(id,0L));
        }
        //对分类信息进行分组（parent_id）
        Map<Long, List<Category>> childrenMap = categoryList.stream().collect(Collectors.groupingBy(Category::getParentId));
        //筛选一节分类
        List<Category> parentCategoryList = categoryList.stream().filter(c -> c.getParentId() == 0).collect(Collectors.toList());
        //给一级分类循环，获取子分类，并且计算count
        for(Category category:parentCategoryList){
            Long id= category.getId();
            List< Category> sonCategoryList =childrenMap.getOrDefault(id,new ArrayList<>()) ;
            category.setChildren(sonCategoryList);
            //计算count
            long sonCount = sonCategoryList.stream().mapToLong(Category::getCount).sum();
            category.setCount(category.getCount()+sonCount);
        }
        return parentCategoryList;
    }
    /**
     * 保存分类
     * @param category
     */
    @Override
    public void saveCategory(Category category) {
        //当前父分类下不能存在同名分类
        LambdaQueryWrapper<Category> queryWrapper=new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getParentId,category.getParentId())
                    .eq(Category::getName,category.getName());
        long count = count(queryWrapper);
        if(count>0){
            throw new RuntimeException("当前父分类下已存在同名分类");
        }
        //保存新分类
        save(category);
    }
    /**
     * 修改分类
     * @param category
     */
    @Override
    public void updateCategory(Category category) {
       LambdaQueryWrapper<Category> queryWrapper=new LambdaQueryWrapper<>();
       queryWrapper.eq(Category::getParentId,category.getParentId())
                   .eq(Category::getName,category.getName())
                   .ne(Category::getId,category.getId());//检查其他的子分类
                   long count = count(queryWrapper);
                   if(count>0){
                       throw new RuntimeException("当前父分类下已存在同名分类");
                   }
                   updateById(category);
           }
    /**
     * 删除分类
     * @param id
     */
    @Override
    public void deleteCategory(Long id) {
         Category category = getById(id);
         if(category==null){
             log.debug("要删除的分类不存在：{}",id);
             throw new RuntimeException("要删除的分类不存在");
         }
         if(category.getParentId()==0){
            throw new RuntimeException("不能删除一级分类");
         }
        //检查是否有子分类
        LambdaQueryWrapper<Question> queryWrapper=new LambdaQueryWrapper<>();
        queryWrapper.eq(Question::getCategoryId,id);
        long count = questionMapper.selectCount(queryWrapper);
        if(count>0){
            throw new RuntimeException("该分类下有子分类，无法删除");
        }
        removeById(id);
    }

}