package com.hmall.search.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.search.domain.po.Item;
import com.hmall.search.mapper.SearchMapper;
import com.hmall.search.service.ISearchService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 商品表 服务实现类
 * </p>
 *
 * @author 虎哥
 */
@Service
public class ISearchServiceImpl extends ServiceImpl<SearchMapper, Item> implements ISearchService {

}
