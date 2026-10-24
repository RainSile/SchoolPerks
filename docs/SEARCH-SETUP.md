# 搜索、MongoDB与地图接入合同

本文件描述可选服务的配置合同。MySQL提供本地社区、位置和搜索能力；ES、MongoDB和地图由部署者显式配置，不能据源码推定某个部署实例已接通服务。

## Elasticsearch + IK

当前适配器使用Java17 HttpClient和Jackson调用 **ES 8 REST协议**，没有引入ES Java SDK、Spring Data Elasticsearch或额外Maven版本。部署者须先选择实际可用的ES 8.x.y及**同完整版本**的analysis-ik，再填 ELASTICSEARCH_EXPECTED_VERSION/ELASTICSEARCH_IK_VERSION。测试夹具里的8.17.3仅用于协议断言，不是已部署版本，也不代表当前版本推荐。版本选择依据为[IK官方要求](https://github.com/infinilabs/analysis-ik)，未来升级ES主版本需另行验证协议。

按实际服务的TLS/认证方式，由管理员在应用启动前手动执行：

```http
PUT /campus-perks-search-v1-000001
Content-Type: application/json

（请求体使用同目录 search-index.json）
```

[`search-index.json`](search-index.json) 提供严格映射和默认别名 campus-perks-search-v1。更改配置别名时同步修改该文件中的别名；无需更改业务代码。应用仅接受一个物理索引对应的已存在别名，**不会自动创建/删除索引或云资源**，也暂不支持一个别名挂多索引的切换过程。预先创建索引见[官方创建索引接口](https://www.elastic.co/guide/en/elasticsearch/reference/8.17/indices-create-index.html)。

设置 `SEARCH_BACKEND=ELASTICSEARCH`、根地址ELASTICSEARCH_ENDPOINT、API key和两项版本值。远端要求HTTPS，本机回环可HTTP；不接受URL内用户名密码、路径前缀、查询参数或重定向。自签名证书应导入独立JVM truststore，不关闭证书校验。API key仅后端环境变量提供，不能放入VITE变量。

应用在实际读写时核对服务版本、所有节点的IK插件、别名和映射；账号需允许GET根信息、GET /_nodes/plugins、GET /_alias/{alias}、GET /{alias}/_mapping、索引文档及搜索。权限不足或版本/映射不符均503，不回显供应商响应。每个响应有2MiB协议上限。当前每次调用均检查配置，不承诺高吞吐；资源就绪后应针对真实节点数、响应大小和延迟测试。

写入使用持久本地revision、external_gte、require_alias与隐藏墓碑，语义依据[ES索引API](https://www.elastic.co/guide/en/elasticsearch/reference/8.17/docs-index_.html)。首次接入后由 `search:manage` 创建重建任务并等待COMPLETED，再检查outbox失败队列与公开搜索；已有消费标记的旧事件不会因切换后端自动重放，所以重建不可省略。不要删除本地投影版本或墓碑后继续写原索引。重建仅恢复已知源/投影资源，不会发现人为插入ES的未知垃圾文档；返回端仍通过主库校验过滤它们。

## MongoDB后续替换

`CommunityInteractions` 已隔离评论、点赞持久化，当前由CommunityMapper的MySQL实现承担。该接口是迁移起点，**不能只换一个Bean就认为跨库事务仍成立**。当前账号/门店/帖子权限和审计依赖SQL事务。

未来集合至少保留：评论唯一键 `(postId,authorId,requestKey)`、点赞唯一键 `(postId,userId)`；不可变字符串ID、审核状态/版本、事件ID/消费去重、同键不同内容冲突及软删除语义。MongoDB唯一索引和单文档原子性见[唯一索引](https://www.mongodb.com/docs/manual/core/index-unique/)与[写入原子性](https://www.mongodb.com/docs/manual/core/write-operations-atomicity/)。这些能力不提供SQL与Mongo之间的原子提交。

替换前先设计MySQL持久命令/outbox、幂等消费者、操作状态查询与失败补偿，必要时接口改为202并让前端等待最终状态；不能SQL写一半后同步写Mongo然后返回假成功。点赞/评论计数从有效关系和审核状态派生，通过事件及定期对账修复。迁移需要冻结或记录增量、回填、按ID/状态/哈希核对、切换、明确回退窗口；在契约和资源验证完成前不启用双写，也没有自动建集合代码。

## 地图与坐标

本地数据库与附近查询只接受WGS84；浏览器坐标语义依据[W3C Geolocation](https://www.w3.org/TR/geolocation/#coordinates-interface)。高德的坐标约定参考[官方说明](https://developer.amap.com/faq/advisory/others/39838)。未来地图服务若使用GCJ-02，应由专用适配器调用厂商官方转换能力，标注输入/输出坐标系，保留来源，禁止直接把地图点标为WGS84，也不自行编写近似转换冒充精确结果。

目前只有距离列表和手动/浏览器坐标，没有路线、地理编码、底图或地图密钥。旧门店坐标因来源未知被排除，须管理员/店主确认后重新设置。用户定位为明确按钮触发，不做后台持续定位；直线距离不能显示为步行距离。
