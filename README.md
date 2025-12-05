# Kafka View - Kafka 可视化工具

## 1. 项目概述 (Project Overview)
本项目旨在构建一个轻量级、免安装、功能直观的 Kafka 可视化工具。用于浏览和查看多个 Kafka 集群与主题信息，支持多版本兼容（低版本一些数据，无法显示）、主题/分区与消息查看、以及基于关键词的消息快速检索。

## 2. 技术栈 (Technology Stack)

### 后端 (Backend)
- **核心框架**: Spring Boot 3.3+
- **开发语言**: Java 17+
- **ORM**: MyBatis（Mapper XML + 注解）
- **数据库**: SQLite（嵌入式，单文件存储）
- **Kafka 客户端**: Spring Kafka / Kafka Clients（兼容 2.x/3.x）

### 前端 (Frontend)
- **框架**: Vue 3（Composition API）
- **UI**: Element Plus（默认）或 Ant Design Vue
- **图表**: ECharts（流量、Lag 可视化）
- **网络/请求**: Axios（封装于 `frontend/src/api`），Vite 用于本地开发代理（`/api` -> 后端）

### 基础设施 / 部署
- **打包构建**: Vite（前端），Maven（后端）
- **容器化**: 多阶段 `Dockerfile`（Node build -> Maven package -> JRE runtime）

## 3. 系统架构 (System Architecture)

```mermaid
graph TD
    Dev[开发人员 / 运维] -->|浏览器| FrontDev[前端 (Vite dev)]
    FrontDev -->|/api (代理)| ViteProxy[Vite Dev Server]
    ViteProxy --> BackendDev[后端 (Spring Boot)]

    subgraph CI/CD
        CI[CI (GitHub Actions)] --> Build[Build: npm build & mvn package]
        Build --> Image[Docker Image]
    end

    subgraph Runtime
        Image --> Container[容器 / 单 JAR 应用]
        Container -->|serves static| Browser[浏览器用户]
        Container --> BackendApp[Spring Boot App]
        BackendApp --> Services[Service Layer]
        Services --> MyBatis[MyBatis Mapper]
        MyBatis --> SQLiteDB[(SQLite DB)]
    end

    subgraph KafkaEnv [Kafka Ecosystem]
        AdminClient[AdminClient] --> KafkaCluster[目标 Kafka 集群]
        Consumer[Consumer Clients] --> KafkaCluster
        JMXConn[JMX] --> KafkaCluster
    end

    BackendApp --> AdminClient
    BackendApp --> Consumer
    BackendApp --> JMXConn

    style CI fill:#f3f4f6,stroke:#bbb
    style Runtime fill:#fff,stroke:#bbb
    style KafkaEnv fill:#fff8e1,stroke:#bbb
```

说明：
- 本地开发时使用 Vite 的 dev server（包含 proxy），因此前端发起的 `/api` 请求由前端 dev server 转发到后端开发服务（避免浏览器 CORS 问题）。
- 生产环境通过在前端构建后将静态文件放入后端 `src/main/resources/static`，打包为单个 JAR（或容器镜像）发布。
- Docker 多阶段构建先执行前端静态资源构建，再将产物复制到后端资源目录，最后打包并运行在轻量 JRE 镜像中。
- 推荐在 CI 中自动化 `npm run build` + `mvn package`，并推送构建好的镜像到镜像仓库。

    > 轻量、单 JAR 部署的 Kafka 管理与监控工具，支持多集群管理、主题/分区查看、消费者 Lag 统计、消息检索与指标可视化。

    ## 1. 项目概述
    该项目由后端（Spring Boot）与前端（Vue 3）组成，前端构建为静态资源并嵌入到后端 `static` 目录，运行时以单独的 JAR 包（或 Docker 容器）提供完整服务。数据库使用 SQLite 保存配置与轻量状态。

    ## 2. 技术栈

    - 后端：Java 17+, Spring Boot, MyBatis
    - 前端：Vue 3 (Composition API), Element Plus, ECharts
    - 数据库：SQLite（嵌入式）
    - 其他：Axios（前端 API 请求）、Kafka Clients / AdminClient（与 Kafka 交互）

    ## 3. 系统架构

    （下面的 Mermaid 图在支持 Mermaid 的 Markdown 渲染器中会展示；如果在 GitHub 上查看也会被渲染）

    ```mermaid
    graph TD
        User[用户 / 运维人员] --> WebUI[Vue 3 Web UI]
        subgraph "Single JAR Application"
            WebUI --REST API--> Controller[Web Controller]
            subgraph "Service Layer"
                ClusterSvc[集群管理服务]
                MonitorSvc[详情信息服务]
                SearchSvc[消息检索服务]
            end
            Controller --> ClusterSvc
            Controller --> MonitorSvc
            Controller --> SearchSvc
            subgraph "Data Access"
                MyBatis[MyBatis Mapper]
                SQLite[(SQLite DB)]
                MyBatis --JDBC--> SQLite
            end
            ClusterSvc --> MyBatis
        end
        subgraph "Kafka Ecosystem"
            AdminClient[Kafka AdminClient]
            Consumer[Kafka Consumer]
            JMXConn[JMX Connector]
        end
        ClusterSvc --Manage--> AdminClient
        SearchSvc --Scan--> Consumer
        MonitorSvc --Lag--> AdminClient
        AdminClient --> KafkaCluster[目标 Kafka 集群]
        Consumer --> KafkaCluster
    ```

    ## 4. 主要模块与功能

    - 集群管理：添加/编辑/删除 Kafka 集群（支持 PLAINTEXT、SASL、SSL 配置项存储）。
    - 详情信息：Topic 列表、分区详情、ISR、消费者组与 Lag 统计，支持 Broker 指标采集（例如通过 AdminClient 或其它监控手段）。
    - 消息检索：基于时间窗口的扫描检索（可设置最大扫描条数与超时保护）。
    - 数据存储：使用 SQLite 保存集群元数据与轻量业务状态。

    ## 5. 接口文档（摘要）

    后端 API 通过路径 `/api` 暴露，下面是主要路由及用途（按 `frontend/src/api/index.js` 映射）：

    - 集群管理
      - `GET  /api/clusters` — 列出所有集群
      - `POST /api/clusters` — 新增集群（body: cluster 配置）
      - `PUT  /api/clusters` — 更新集群信息（body）
      - `DELETE /api/clusters/{id}` — 删除集群

    - Topic 相关
      - `GET  /api/clusters/{clusterId}/topics` — 分页查询 Topic 列表（params: page, pageSize, keyword）
      - `POST /api/clusters/{clusterId}/topics` — 创建 Topic
      - `DELETE /api/clusters/{clusterId}/topics/{topicName}` — 删除 Topic
      - `GET  /api/clusters/{clusterId}/topics/{topicName}/partitions` — 获取分区信息
      - `GET  /api/clusters/{clusterId}/topics/{topicName}/configs` — 获取 Topic 配置
      - `PUT  /api/clusters/{clusterId}/topics/{topicName}/configs` — 更新 Topic 配置

    - 消息与检索
      - `GET  /api/clusters/{clusterId}/topics/{topicName}/messages` — 查询 Topic 消息（支持分页与筛选）
      - `POST /api/clusters/{clusterId}/topics/{topicName}/messages` — 发送消息到 Topic
      - `GET  /api/clusters/{clusterId}/topics/{topicName}/messages/history` — 拉取消息发送历史

    - Volume / Backfill（流量统计）
      - `GET  /api/clusters/{clusterId}/topics/{topicName}/volume?days=7` — 获取某 Topic 最近 N 天的流量点
      - `GET  /api/clusters/{clusterId}/topics/volumes?topics=a&topics=b&days=7` — 批量获取多个 Topic 的流量数据
      - `POST /api/clusters/{clusterId}/topics/volumes/backfill?topics=a&days=30` — 触发后端回填（异步/触发任务）

    注意：所有接口均返回统一封装的 JSON 格式（形如 { code: 200, data: ..., message: '...' }），前端会根据 `code` 判断成功与否。

    ## 6. 使用教程（快速开始）

    本项目在本地可以分别启动后端与前端进行开发，也可以构建为单个 JAR 进行部署。下面是常用命令（Windows PowerShell）：

    1) 启动后端（开发）

    ```powershell
    cd /d G:\code\kafka-view
    mvn spring-boot:run
    ```

    2) 启动前端（开发热重载）

    ```powershell
    cd /d G:\code\kafka-view\frontend
    npm install
    npm run dev
    # 在浏览器中访问 http://localhost:5173（或终端显示的端口）
    ```

    开发时同时修改后端端口并配置前后端（示例）
    -------------------------------------------------

    下面示例演示如何把后端端口改为 `9090`，并使本地开发的前端正确代理到新的后端端口：

    1) 在后端使用环境变量或 `application.yml` 将服务端口改为 `9090`：

    ```powershell
    # 使用环境变量临时设置并启动（PowerShell）
    $env:SERVER_PORT = '9090'
    mvn spring-boot:run

    # 或者更改配置文件（src/main/resources/application.yml）：
    # spring:
    #   server:
    #     port: 9090
    # 然后重新启动服务：
    mvn spring-boot:run
    ```

    2) 告知前端新的后端地址（开发环境）：编辑 `frontend/.env.development`，把 `VITE_BACKEND_URL` 指向后端新端口：

    ```
    VITE_BACKEND_URL=http://localhost:9090
    ```

    3) 重启前端开发服务器，使 Vite 读取新的环境变量：

    ```powershell
    cd /d G:\code\kafka-view\frontend
    npm run dev
    ```

    4) 现在前端会：
    - 把 `/api/...` 请求发送到 Vite（`http://localhost:<vitePort>/api/...`），Vite 将这些请求代理并转发到 `http://localhost:9090/api/...`。
    - `login`（在开发模式）会直接发送到 `http://localhost:9090/login`（避免 dev server 把重定向解析为自己的路径）。

    注意：修改 `frontend/.env.development` 后必须重启 `npm run dev`，因为 Vite 在启动时读取 env 文件。另外，如果后端使用 Docker 运行，请确保 `VITE_BACKEND_URL` 指向容器可达的地址（例如 `http://host.docker.internal:9090` 或相应网络名称）。

    3) 将前端构建并嵌入后端（生产打包）

    ```powershell
    cd /d G:\code\kafka-view\frontend
    npm install
    npm run build
    # 生成的静态文件会在 frontend/dist/ 下
    # 将 dist 内容复制到后端资源目录（以构建时为准）
    Copy-Item -Path .\dist\* -Destination ..\src\main\resources\static -Recurse -Force

    cd /d G:\code\kafka-view
    mvn clean package -DskipTests
    # 运行打包后的 JAR
    java -jar target\kafka-view-*.jar
    ```

    （注意：上述 `Copy-Item` 命令是把前端构建产物放到后端 `static`，在 CI/CD 中可用脚本自动化）

    ## 7. Docker 部署示例

    下面提供一个简单的 Dockerfile 示例，用于把构建好的 JAR 打包到容器中运行：

    Dockerfile (示例)：

    ```dockerfile
    FROM eclipse-temurin:17-jre
    WORKDIR /app
    COPY target/kafka-view.jar /app/kafka-view.jar
    EXPOSE 8080
    ENTRYPOINT ["java","-jar","/app/kafka-view.jar"]
    ```

    构建并运行镜像：

    ```powershell
    docker build -t kafka-view:latest .
    docker run -d -p 8080:8080 --name kafka-view kafka-view:latest
    ```

    中文说明（构建与运行示例）

    下面给出中文的拷贝粘贴示例，适用于在 Windows PowerShell 上一键构建并运行容器：

    ```powershell
    # 在项目根目录构建镜像（包含前端构建 + 后端打包）
    docker build -t kafka-view:latest .

    # 运行容器，映射宿主机 8080 端口到容器 8080
    docker run -d -p 8080:8080 --name kafka-view --restart unless-stopped kafka-view:latest

    # 查看容器日志（实时跟随）
    docker logs -f kafka-view

    # 停止并删除容器
    docker stop kafka-view
    docker rm kafka-view
    ```

    一键构建并运行（单行示例）：

    ```powershell
    docker build -t kafka-view:latest . && docker run -d -p 8080:8080 --name kafka-view --restart unless-stopped kafka-view:latest
    ```

    注意事项（中文）：
    - 本仓库包含的 `Dockerfile` 为多阶段构建：先在镜像中执行前端构建（生成 `frontend/dist`），再把 `dist` 内容复制到后端的 `src/main/resources/static`，最后用 Maven 打包生成单个可运行的 JAR；因此镜像构建会包含 Node 与 Maven 的下载，首次构建耗时较长。
    - `Dockerfile` 中包含了 `HEALTHCHECK`（默认检测 `/actuator/health`），如果项目未启用 Spring Boot Actuator，请删除或调整该检测命令，否则容器会报告不健康。
    - 开发时如果你使用容器部署并希望外部访问 UI，请确保宿主机的 8080 端口可访问（防火墙/安全组设置）。
    - 若希望更小的镜像体积或更快的构建，可在 CI 中缓存依赖或在构建阶段使用更轻量的镜像/清理构建缓存。我可根据需要帮助优化。

    如果你只想在容器中同时构建前端并打包后端，可在 CI 环境或多阶段 Dockerfile 中完成（此处略）。

## 8. 配置说明

- `src/main/resources/application.yml`：后端配置（端口、数据库路径、日志、Kafka 相关超时等）
- `src/main/resources/kafka-view.db`：默认的 SQLite DB（初始或示例数据）。请根据环境备份或指定路径。

如何修改后端服务端口
---------------------------------

后端服务监听的端口可以通过多种方式修改：

- 在 `application.yml` 中直接修改 `server.port`：

```yaml
# src/main/resources/application.yml
spring:
    server:
        port: 9090
```

- 使用环境变量（优先级高于 `application.yml`）：
    - `SERVER_PORT` 或 `PORT` 环境变量均可被识别（优先使用 `SERVER_PORT`）。
    - 示例（Windows PowerShell）：

```powershell
# 使用 SERVER_PORT 环境变量运行
$env:SERVER_PORT = '9090'
java -jar target\kafka-view-*.jar

# 或者在 Docker 运行时传入环境变量并映射端口
# docker run -d -p 9090:9090 -e SERVER_PORT=9090 kafka-view:latest
```

- 在开发时使用 `mvn spring-boot:run` 可通过属性覆盖：

```powershell
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"
```

注意：如果你在容器中运行应用，宿主机端口与容器端口的映射由 `docker run -p HOST:CONTAINER` 控制，因此即使应用在容器内监听 8080，你也可以使用 `-p 9090:8080` 将其暴露为宿主机的 9090 端口。

示例：如果你需要让应用使用不同的 DB 文件，可在 `application.yml` 中配置 `spring.datasource.url: jdbc:sqlite:/data/kafka-view.db` 或通过环境变量覆盖。

**数据库自动初始化**：项目启动时，Spring Boot 会自动执行 `src/main/resources/schema.sql` 来创建所需的表（`cluster_info`、`message_history`、`topic_volume`）。无需手动操作，表会按需创建（使用 `IF NOT EXISTS` 确保安全）。

## 9. 常见操作命令（摘要）    - 本地运行后端（开发）： `mvn spring-boot:run`
    - 本地运行前端（开发）： `cd frontend && npm run dev`
    - 生产构建： `cd frontend && npm run build` -> 拷贝到后端 -> `mvn package`
    - 清理构建产物： `mvn clean`，以及删除 `target/` 目录

    ## 10. 代码结构

    ```
    kafka-view/
    ├── frontend/                 # Vue 3 前端应用
    │   ├── src/
    │   │   ├── components/       # SFC 组件
    │   │   ├── api/              # 前端 API 封装（对应后端路由）
    │   │   └── assets/
    │   ├── package.json
    │   └── vite.config.js
    ├── src/main/java/...         # 后端 Java 源码
    ├── src/main/resources/
    │   ├── static/               # 前端构建产物（dist）
    │   └── application.yml
    ├── pom.xml
    └── README.md
    ```

    ## 11. 接口安全与运行注意事项

    - 若目标 Kafka 集群启用了认证（SASL/SSL），请确保在添加集群时正确填写认证信息；敏感字段如密码/JAAS 可考虑通过加密或 KMS 管理。
    - Broker 指标采集需要在 Broker 侧正确配置和开放相应的监控端点（例如使用 Prometheus exporter、JMX 转 Prometheus、或通过 AdminClient 获取可用元数据），注意网络与防火墙配置。

    ## 12. 贡献与开发者说明

    - 请遵循项目编码规范（后端 Java、前端 Vue），提 PR 前运行格式化与基本测试。
    - 提交前请确保构建通过：`mvn -DskipTests package` 与 `cd frontend && npm run build`。

    ---

    如需我把 README 转成英文/双语版本、生成独立的 API OpenAPI/Swagger 文档，或把 Docker 化流程写成 CI/CD 脚本（GitHub Actions / Azure DevOps），我可以继续添加。请告诉我下一步需求。

    ## 附：数据库建表 SQL (SQLite)

    下面是项目中 `src/main/resources/schema.sql` 的建表语句，供快速参考与本地初始化使用：

    ```sql
    -- Create table for storing Kafka cluster configurations
    CREATE TABLE IF NOT EXISTS cluster_info (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL UNIQUE,
        bootstrap_servers TEXT NOT NULL,
        kafka_version TEXT,
        security_protocol TEXT DEFAULT 'PLAINTEXT',
        sasl_mechanism TEXT,
        sasl_jaas_config TEXT,
        username TEXT,
        password TEXT,
        timeout INTEGER DEFAULT 15000,
        version_supported BOOLEAN DEFAULT 1,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS message_history (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        cluster_id INTEGER NOT NULL,
        topic_name TEXT NOT NULL,
        partition_id INTEGER,
        key_content TEXT,
        value_content TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );

    -- Topic volume snapshots: store daily produced count and cumulative end-offset snapshot
    CREATE TABLE IF NOT EXISTS topic_volume (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        cluster_id INTEGER NOT NULL,
        topic_name TEXT NOT NULL,
        day TEXT NOT NULL, -- YYYY-MM-DD
        produced_count INTEGER DEFAULT 0,
        cumulative_offset INTEGER DEFAULT 0,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        UNIQUE(cluster_id, topic_name, day)
    );
    ```

    说明：
    - `cluster_info`：保存集群连接与认证配置。
    - `message_history`：记录通过 UI 发送的消息历史（便于重发/审计）。
    - `topic_volume`：按天存储某 Topic 的产量快照与累积偏移量，用于流量统计与回填。
