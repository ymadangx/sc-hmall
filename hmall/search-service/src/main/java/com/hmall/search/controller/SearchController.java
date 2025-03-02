package com.hmall.search.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmall.common.constant.ESConstants;
import com.hmall.common.domain.PageDTO;
import com.hmall.search.domain.po.ItemDoc;
import com.hmall.search.domain.query.ItemPageQuery;
import com.hmall.search.service.ISearchService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Api(tags = "搜索相关接口")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final ISearchService searchService;

    private final RestHighLevelClient client;

    @ApiOperation("搜索商品")
    @GetMapping("/list")
    public PageDTO<ItemDoc> search(ItemPageQuery query) throws IOException {
        SearchRequest request = new SearchRequest(ESConstants.INDEX_NAME);

        SearchSourceBuilder source = request.source();
        BoolQueryBuilder boolQuery = buildBoolQuery(query);

        source.query(QueryBuilders.functionScoreQuery(
                boolQuery,
                new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                QueryBuilders.termQuery(ESConstants.IS_AD_FILED, true),
                                ScoreFunctionBuilders.weightFactorFunction(100)
                        )
                }
        ));

        if (ESConstants.PRICE_FILED.equals(query.getSortBy())){
            source.sort(ESConstants.PRICE_FILED, SortOrder.ASC);
        }else if (ESConstants.SOLD_FILED.equals(query.getSortBy())){
            source.sort(ESConstants.SOLD_FILED, SortOrder.DESC);
        }

        int from = (query.getPageNo() - 1) * query.getPageSize();

        source.from(from)
                .size(query.getPageSize())
                .highlighter(SearchSourceBuilder.highlight()
                        .field(ESConstants.NAME_FILED)
                        .preTags(ESConstants.PRE_TAGS)
                        .postTags(ESConstants.POST_TAGS));

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        SearchHits searchHits = response.getHits();
        SearchHit[] hits = searchHits.getHits();

        long total = searchHits.getTotalHits().value;
        long pages = (total - 1) / query.getPageSize() + 1;

        List<ItemDoc> result = new ArrayList<>(hits.length);

        for (SearchHit hit : hits) {
            ItemDoc itemDoc = JSONUtil.toBean(hit.getSourceAsString(), ItemDoc.class);

            Map<String, HighlightField> fields = hit.getHighlightFields();
            StringBuilder builder = new StringBuilder();
            if (fields != null && !fields.isEmpty()) {
                Text[] fragments = fields.get(ESConstants.NAME_FILED).getFragments();
                for (Text fragment : fragments) {
                    builder.append(fragment.string());
                }
            }
            itemDoc.setName(builder.toString());
            result.add(itemDoc);
        }

        return new PageDTO<>(total, pages, result);
        // // 分页查询
        // Page<Item> result = searchService.lambdaQuery()
        //         .like(StrUtil.isNotBlank(query.getKey()), Item::getName, query.getKey())
        //         .eq(StrUtil.isNotBlank(query.getBrand()), Item::getBrand, query.getBrand())
        //         .eq(StrUtil.isNotBlank(query.getCategory()), Item::getCategory, query.getCategory())
        //         .eq(Item::getStatus, 1)
        //         .between(query.getMaxPrice() != null, Item::getPrice, query.getMinPrice(), query.getMaxPrice())
        //         .page(query.toMpPage("update_time", false));
        // // 封装并返回
        // return PageDTO.of(result, ItemDTO.class);
    }

    @ApiOperation("商品过滤参数")
    @PostMapping("/filters")
    public Map<String, List<String>> filter(ItemPageQuery query) throws IOException {
        SearchRequest request = new SearchRequest(ESConstants.INDEX_NAME);

        BoolQueryBuilder boolQuery = buildBoolQuery(query);

        Map<String, List<String>> result = new HashMap<>(2);
        String aggCategory = "aggCategory";
        String aggBrand = "aggBrand";

        request.source()
                .query(boolQuery)
                .size(0)
                .aggregation(AggregationBuilders.terms(aggCategory)
                        .field(ESConstants.CATEGORY_FILED))
                .aggregation(AggregationBuilders.terms(aggBrand)
                        .field(ESConstants.BRAND_FILED));

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        Terms categoryAgg = response.getAggregations().get(aggCategory);
        Terms brandAgg = response.getAggregations().get(aggBrand);

        List<? extends Terms.Bucket> buckets = categoryAgg.getBuckets();
        List<String> categoryList = new ArrayList<>();
        for (Terms.Bucket bucket : buckets) {
            String key = bucket.getKeyAsString();
            categoryList.add(key);
        }
        result.put(ESConstants.CATEGORY_FILED, categoryList);

        List<? extends Terms.Bucket> brandBuckets = brandAgg.getBuckets();
        List<String> brandList = new ArrayList<>();
        for (Terms.Bucket bucket : brandBuckets) {
            String key = bucket.getKeyAsString();
            brandList.add(key);
        }
        result.put(ESConstants.BRAND_FILED, brandList);

        return result;
    }

    /**
     * 构建bool查询
     * @param query
     * @return
     */
    private BoolQueryBuilder buildBoolQuery(ItemPageQuery query) {
        if (query.getMaxPrice() == null) {
            query.setMinPrice(0);
        }
        if (query.getMinPrice() == null) {
            query.setMinPrice(Integer.MAX_VALUE);
        }

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        if (StrUtil.isNotBlank(query.getKey())) {
            boolQuery.must(QueryBuilders
                    .matchQuery(ESConstants.NAME_FILED, query.getKey()));
        }else {
            boolQuery.must(QueryBuilders
                    .matchAllQuery());
        }

        if (StrUtil.isNotBlank(query.getCategory())) {
            boolQuery.filter(QueryBuilders
                    .termQuery(ESConstants.CATEGORY_FILED, query.getCategory()));
        }
        if (StrUtil.isNotBlank(query.getBrand())) {
            boolQuery.filter(QueryBuilders
                    .termQuery(ESConstants.BRAND_FILED, query.getBrand()));
        }
        boolQuery.filter(QueryBuilders
                .rangeQuery(ESConstants.PRICE_FILED)
                .gte(query.getMinPrice())
                .lte(query.getMaxPrice()));
        return boolQuery;
    }
}
