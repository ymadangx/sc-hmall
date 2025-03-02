package search;

import cn.hutool.json.JSONUtil;
import com.hmall.common.utils.CollUtils;
import com.hmall.search.domain.po.ItemDoc;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.Map;


public class ElasticSearchTest {
    @Resource
    private RestHighLevelClient client;

    @BeforeEach
    void setUp() {
        this.client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("http://localhost:9100")
        ));
        System.out.println("成功连接");
    }

    @AfterEach
    void tearDown() throws IOException {
        this.client.close();
    }

    //查询全部 match_all
    @Test
    public void testMatchAll() throws IOException {
        //1.准备request对象
        SearchRequest searchRequest = new SearchRequest("items");
        //2.组织DSL参数
        searchRequest.source()
                .query(QueryBuilders.matchAllQuery());
        //3.发送请求，得到结果
        SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);

        //4.对返回对象解析
        handleResponse(search);
    }

    //叶子查询 match 查询名字中有脱脂牛奶的
    @Test
    public void testMatch() throws IOException {
        SearchRequest searchRequest = new SearchRequest("items");
        searchRequest.source()
                .query(QueryBuilders.matchQuery("name","脱脂牛奶"));
        SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
        handleResponse(search);
    }

    //multi_match查询
    @Test
    public void testMultiMatch() throws IOException {
        SearchRequest searchRequest = new SearchRequest("items");
        searchRequest.source()
                .query(QueryBuilders.multiMatchQuery("脱脂牛奶","name","category"));
        SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
        handleResponse(search);
    }

    //range查询
    @Test
    public void testRange() throws IOException {
        SearchRequest searchRequest = new SearchRequest("items");
        searchRequest.source()
                .query(QueryBuilders.rangeQuery("price").gte(10000).lte(30000));
        SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
        handleResponse(search);
    }

    //term测试
    @Test
    public void testTerm() throws IOException{
        SearchRequest searchRequest = new SearchRequest("items");
        searchRequest.source()
                .query(QueryBuilders.termQuery("brand","华为"));
        SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
        handleResponse(search);
    }

    //复合查询 bool查询测试
    @Test
    public void testBool() throws IOException {
        SearchRequest searchRequest = new SearchRequest("items");
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must(QueryBuilders.matchQuery("name","脱脂牛奶"))
                .filter(QueryBuilders.termQuery("brand","德亚"))
                .filter(QueryBuilders.rangeQuery("price").lte(30000));
        searchRequest.source().query(boolQueryBuilder);
        SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
        handleResponse(search);
    }

    //排序和分页
    @Test
    void testPageAndSort() throws IOException {
        int pageNo = 1, pageSize = 5;
        // 1.创建Request
        SearchRequest request = new SearchRequest("items");
        // 2.组织请求参数
        // 2.1.搜索条件参数
        request.source().query(QueryBuilders.matchQuery("name", "脱脂牛奶"));
        // 2.2.排序参数
        request.source().sort("price", SortOrder.ASC);
        // 2.3.分页参数
        request.source().from((pageNo - 1) * pageSize).size(pageSize);
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4.解析响应
        handleResponse(response);
    }

    //高亮显示
    @Test
    void testHighlight() throws IOException {
        // 1.创建Request
        SearchRequest request = new SearchRequest("items");
        // 2.组织请求参数
        // 2.1.query条件
        request.source().query(QueryBuilders.matchQuery("name", "脱脂牛奶"));
        // 2.2.高亮条件
        request.source().highlighter(
                SearchSourceBuilder.highlight()
                        .field("name")
                        .preTags("<em>")
                        .postTags("</em>")
        );
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4.解析响应
        handleResponse(response);
    }

    //聚合
    //类别是手机，价格大于等于3000的数据 根据品牌做聚合
    @Test
    void testAgg() throws IOException {
        // 1.创建Request
        SearchRequest request = new SearchRequest("items");
        // 2.准备请求参数
        BoolQueryBuilder bool = QueryBuilders.boolQuery()
                .filter(QueryBuilders.termQuery("category", "手机"))
                .filter(QueryBuilders.rangeQuery("price").gte(300000));
        request.source().query(bool).size(0);
        // 3.聚合参数
        request.source().aggregation(
                AggregationBuilders.terms("brand_agg").field("brand").size(5)
        );
        // 4.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 5.解析聚合结果
        Aggregations aggregations = response.getAggregations();
        // 5.1.获取品牌聚合
        Terms brandTerms = aggregations.get("brand_agg");
        // 5.2.获取聚合中的桶
        List<? extends Terms.Bucket> buckets = brandTerms.getBuckets();
        // 5.3.遍历桶内数据
        for (Terms.Bucket bucket : buckets) {
            // 5.4.获取桶内key
            String brand = bucket.getKeyAsString();
            System.out.print("brand = " + brand);
            long count = bucket.getDocCount();
            System.out.println("; count = " + count);
        }
    }

    //返回对象解析工具类
    private void handleResponse(SearchResponse response) {
        SearchHits searchHits = response.getHits();
        // 1.获取总条数
        long total = searchHits.getTotalHits().value;
        System.out.println("共搜索到" + total + "条数据");
        // 2.遍历结果数组
        SearchHit[] hits = searchHits.getHits();
        for (SearchHit hit : hits) {
            // 3.得到_source，也就是原始json文档
            String source = hit.getSourceAsString();
            // 4.反序列化
            ItemDoc item = JSONUtil.toBean(source, ItemDoc.class);
            // 5.获取高亮结果
            Map<String, HighlightField> hfs = hit.getHighlightFields();
            if (CollUtils.isNotEmpty(hfs)) {
                // 5.1.有高亮结果，获取name的高亮结果
                HighlightField hf = hfs.get("name");
                if (hf != null) {
                    // 5.2.获取第一个高亮结果片段，就是商品名称的高亮值
                    String hfName = hf.getFragments()[0].string();
                    item.setName(hfName);
                }
            }
            System.out.println(item);
        }
    }
}
