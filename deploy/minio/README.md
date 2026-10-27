# 轻量私有 MinIO 部署

这是独立于已有 Redis / MongoDB / RabbitMQ / Elasticsearch 的部署目录。具体接入与验收见 [对象存储记录](https://github.com/RainSile/SchoolPerks/blob/campus-doc/docs/MINIO-SETUP.md)。不要把本目录复制到已有基础设施项目后执行整体重启。

## 固定构建

MinIO 社区仓库已归档。本项目固定官方 `RELEASE.2025-10-15T17-29-55Z`，提交 `9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a`，包含该次发布的服务账号权限修补。不使用 `latest` 或旧的预编译 Docker 标签。来源：[官方源码](https://github.com/minio/minio)、[固定发布记录](https://github.com/minio/minio/releases/tag/RELEASE.2025-10-15T17-29-55Z)。

在开发机或构建机准备 Go 1.27.3，验证工具链官方校验和；不要在这台小服务器上编译。以下为 Linux 构建机示意，Windows 已用同样的 Go 版本和交叉编译参数实际完成构建：

```sh
git clone --depth 1 --branch RELEASE.2025-10-15T17-29-55Z https://github.com/minio/minio.git minio-source
cd minio-source
test "$(git rev-parse HEAD)" = 9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a
export GOOS=linux GOARCH=amd64 CGO_ENABLED=0 GOMAXPROCS=2 GOMEMLIMIT=512MiB
go build -p 1 -trimpath -ldflags '-s -w -X github.com/minio/minio/cmd.ReleaseTag=RELEASE.2025-10-15T17-29-55Z -X github.com/minio/minio/cmd.Version=2025-10-15T17:29:55Z -X github.com/minio/minio/cmd.CommitID=9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a' -o minio .
```

将 `bootstrap/main.go` 复制进该固定源码目录的 `campus-init/main.go`，使用源码自带的 `go.mod` / `go.sum`（minio-go 7.0.91、madmin-go 3.0.109），继续执行：

```sh
go build -p 1 -trimpath -ldflags '-s -w' -o minio-init ./campus-init
sha256sum minio minio-init
```

将服务二进制及官方源码的 `LICENSE` 分别放入本部署目录的 `build/minio`、`build/LICENSE`；将初始化程序放在本部署目录的 `minio-init`。二进制、数据和密钥不提交。Dockerfile 的 Alpine 3.22 基础镜像按摘要固定。记录自己的构建摘要，上传后再次校验。

## 启动与初始化

复制 `.env.example` 为私有 `.env`，生成独立、随机的 root 和应用账号密钥，设置权限 `0600`；后续初始化与应用使用同一份应用账号配置。不要重用 root 账号或把 root 密钥交给前端。文件只允许可信运维人员编辑。

在独立 `/opt/campus-perks-storage` 放置这些文件，确认 `data/` 的解析后绝对路径位于该目录下，再创建并赋予容器用户 `10001:10001` 读写权限。启动前检查服务器可用内存，建议至少约 450MB；启动后观察可用内存、健康与 OOM 状态。

```sh
docker compose build minio
docker compose up -d minio
curl --fail http://127.0.0.1:9000/minio/health/ready
```

初始化程序在临时容器中共享 MinIO 网络，只执行一次配置，不作为常驻服务：

```sh
docker run --rm --network container:campus-perks-storage-minio-1 \
  --env-file .env --read-only --cap-drop ALL --security-opt no-new-privileges:true \
  --user 10001:10001 --memory 64m --memory-swap 96m --cpus 0.2 --pids-limit 64 \
  -v "$PWD/minio-init:/minio-init:ro" \
  --entrypoint /minio-init campus-minio:RELEASE.2025-10-15T17-29-55Z
```

初始化会创建/复用私有桶，关闭桶匿名策略，设置配额和 pending 一天过期规则，并配置受限应用账号。它会覆盖该专属桶的 lifecycle 和 `campus-files` 策略，因此只用于本项目独立 MinIO，不应指向共享环境。bootstrap 的 `MINIO_BOOTSTRAP_ENDPOINT` 默认容器网络内 `http://127.0.0.1:9000`，区域默认 `us-east-1`。

默认常驻限制：256MB 内存、384MB 内存加交换总额、0.35 CPU、128 PID、两个并发请求；Go 内存目标160MiB。`GOMEMLIMIT` 是软目标，容器上限是硬限制。桶配额1GiB限制对象数据，不限制日志、元数据及镜像总磁盘，仍需检查宿主机空间。控制台关闭，9000只绑定宿主机回环地址，不开放安全组端口。

## 本机业务接入

按 `backend.env.example` 注入应用账号环境变量。开发机建立并保持 SSH 转发：

```sh
ssh -N -L 127.0.0.1:19000:127.0.0.1:9000 <ssh-user>@<cloud-host>
```

按正常 SSH 流程核实主机指纹。不要关闭主机密钥验证。`MINIO_ENDPOINT` 给后端使用；`MINIO_PUBLIC_ENDPOINT` 是浏览器实际访问的地址。本机开发时两者均可使用转发端点。后端若部署在云端，内部地址可使用127.0.0.1:9000，但对外签名端点需要浏览器可达的 HTTPS 域名及精确 CORS；当前部署未开放公共访问。签名链接生成后不能替换主机、端口或路径。

`FILE_OBJECT_PREFIX` 必须与初始化的 IAM 策略及 lifecycle 前缀一致。默认名称沿用历史配置，也覆盖该前缀下的社区图片；修改前缀需要先迁移已有文件，不能只改配置导致旧文件无法访问。

## 维护与回退

```sh
docker compose ps
docker stats --no-stream campus-perks-storage-minio-1
curl --fail http://127.0.0.1:9000/minio/health/ready
```

备份 `data/` 和私有配置时先停本项目 MinIO，保留文件属主；不要只复制对象载荷而遗漏 MinIO 元数据。回退时先设 `MINIO_ENABLED=false` 并重启业务进程，再在本目录执行 `docker compose stop minio`，保留数据。不要执行 `down -v` 或清理其他项目资源。
