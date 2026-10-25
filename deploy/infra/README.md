# 云服务器中间件

2026-10-23 已在用户指定服务器的 `/opt/campus-perks-infra` 部署。机器2核、1740MiB内存、已有6GiB交换空间；以下是开发联调配置，不具备生产高可用。没有在用户本机安装这些系统服务。

| 容器 | 版本 | 内存上限 | 内存+交换上限 | CPU上限 |
| --- | --- | --- | --- | --- |
| redis | 7.4.11 | 80MiB | 256MiB | 0.4 |
| mongo | 8.0.34 | 384MiB | 768MiB | 0.6 |
| rabbitmq | 4.4.0 | 256MiB | 512MiB | 0.5 |
| elasticsearch + IK | 8.19.23 / 8.19.23 | 640MiB | 1280MiB | 0.8 |

镜像来源是官方 `public.ecr.aws/docker/library` 镜像，compose锁定摘要；ES镜像通过本目录Dockerfile安装同版本IK。四项内存上限总计1360MiB，CPU额度可以竞争，不意味着有空余同等物理内存。ES堆256MiB，禁用ML/watcher；Mongo WiredTiger缓存0.25GiB；Redis maxmemory32MiB、noeviction+AOF。ES仍接近容器内存上限，应观察OOM/交换/响应时间；不在此机器压测或同时恢复旧Java应用。

四个发布端口仅绑定远端127.0.0.1：6379、27017、5672、9200。ES HTTP启用认证，通过SSH保护传输；不提供公网明文服务。Rabbit未启用管理界面。Mongo应用账号只有campus_perks库readWrite，ES应用API Key只有搜索别名及其物理索引的所需权限；应用不使用Mongo root。

远端 `.env` 和 `redis.conf` 为私有文件，目录700、文件600。不要将它们下载到公开位置或提交。重新部署时先生成随机凭据；`compose.yml` 不生成凭据，也不能单独拿它覆盖已有私有目录。Redis配置包含requirepass；示例见redis.conf.example，需安全替换占位符。Mongo初始化变量只对空数据卷生效，修改.env不能重置已有数据库密码。

```sh
cd /opt/campus-perks-infra
docker compose ps
docker stats --no-stream
docker compose logs --tail 60 elasticsearch
```

ES要求 `vm.max_map_count=262144`，已写入 `/etc/sysctl.d/90-campus-elasticsearch.conf`。服务数据分别放入四个命名卷，日志每容器10MB×2。备份应使用服务支持的快照/导出方法并保管凭据；不能以删除卷或 `down -v` 作为重启手段。

ES搜索索引在首次联调时显式创建：`campus-perks-search-v1-000001`，别名`campus-perks-search-v1`，1分片0副本，严格映射见docs/search-index.json。应用不会自动创建或修复索引、插件、API Key。Mongo的助手集合/索引和Rabbit的应用队列由显式启用的适配器按需建立。

本地连接使用SSH回环转发，例如（替换主机及SSH账号；不要在命令行放密码）：

```sh
ssh -N -L 127.0.0.1:16379:127.0.0.1:6379 -L 127.0.0.1:27018:127.0.0.1:27017 -L 127.0.0.1:15673:127.0.0.1:5672 -L 127.0.0.1:19200:127.0.0.1:9200 <ssh-user>@<server>
```

原3211进程已核对为 `/opt/daily-picture/daily-picture.jar`、systemd `daily-picture.service`，已停止并禁用开机启动，文件保留。需要恢复时先评估内存并暂停不需要的中间件，再显式执行 `systemctl enable --now daily-picture.service`；不自动恢复，也不关闭未知系统服务。

故障时先看容器重启次数/OOMKilled、磁盘、内存与SSH隧道；再检查服务认证。Rabbit死信保留供核对，应用不自动purge或删除队列。经典队列和此单节点部署不能承诺硬件损坏下零丢失，生产需要单独资源和备份方案。
