package com.hmall.search.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmall.search.domain.po.Item;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 商品表 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
@Mapper
public interface SearchMapper extends BaseMapper<Item> {

}
