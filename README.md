# 体系协同多源识别算法分析优化软件

本项目基于若依管理系统（RuoYi）进行二次开发，保留了若依原有的核心功能和项目结构，并在此基础上进行了定制化扩展。

## 🎯 项目简介

- **基础框架**: Spring Boot + Vue3.js
- **核心架构**: 前后端分离
- **数据库**: 达梦数据库
- **权限控制**: Spring Security + JWT

## 🚀 快速启动

### 环境要求

- JDK 1.8 或更高版本
- 达梦数据库 8 或更高版本
- Redis 3.0 或更高版本
- Maven 3.0+
- Node.js 12.0+ (前端运行需要)
- npm 或 yarn

### 第一步：数据库初始化

1. 创建数据库
   ```sql
   CREATE DATABASE `your_database_name` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. 导入数据库脚本
   - 找到项目中的SQL文件：`/sql/` 目录下
   - 按顺序执行以下脚本：
     1. `quartz.sql` (定时任务)
     2. `ry_20250522.sql` (主数据库)

### 第二步：后端配置与启动

1. 修改配置文件（不用改）
   - 打开 `/ruoyi-admin/src/main/resources/`
   - 复制 `application-druid.yml.example` 为 `application-druid.yml`
   - 修改数据库连接信息：
     ```yaml
     druid:
       master:
         url: jdbc:mysql://localhost:3306/your_database_name?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8
         username: your_username
         password: your_password
     ```
   - 复制 `application-redis.yml.example` 为 `application-redis.yml`
   - 根据需要修改Redis配置

2. 启动后端服务
   ```bash
   # 方式1：使用IDE运行（推荐）
   # 运行 RuoYiApplication.java 主类
   
   # 方式2：使用Maven命令
   cd ruoyi-admin
   mvn spring-boot:run
   
   # 方式3：打包后运行
   mvn clean package -DskipTests
   java -jar ruoyi-admin/target/ruoyi-admin.jar
   ```

3. 验证后端启动
   - 访问：http://localhost:8080
   - 看到若依后台登录页面即表示后端启动成功

### 第三步：前端配置与启动

1. 安装依赖
   ```bash
   cd RuoYi-Vue3
   npm install --registry=https://registry.npm.taobao.org
   # 或使用yarn
   yarn install
   ```

2. 配置环境变量
   - 开发环境：检查 `.env.development` 文件
   - 生产环境：检查 `.env.production` 文件
   - 确保VUE_APP_BASE_API指向正确的后端地址

3. 启动前端服务
   ```bash
   # 开发模式
   npm run dev
   
   # 构建生产版本
   npm run build:prod
   ```

4. 验证前端启动
   - 访问：http://localhost:80
   - 看到登录界面即可

## 📂 默认账户

- **管理员**: admin / admin123
- **测试账户**: ry / 123456

## 🛠️ 开发指南

### 项目结构
```
/RuoY
├── ruoyi-admin          # 后台服务端
├── ruoyi-common         # 通用模块
├── ruoyi-framework      # 核心框架
├── ruoyi-generator      # 代码生成器
├── ruoyi-quartz         # 定时任务
├── ruoyi-system         # 系统模块
└── sql/                 # 数据库脚本

/RuoYi-Vue3              # 前端界面
```

### 新增模块
1. 在对应模块下创建包结构
2. 继承基类Controller
3. 配置权限注解
4. 前端在对应目录添加页面

### 修改原有功能
1. 保持原有接口的兼容性
2. 如需重大改动，建议新建接口
3. 修改前端页面时注意路由配置

## ⚙️ 常见问题

### 1. 启动时报数据库连接错误
- 检查达梦数据库服务是否启动
- 确认数据库用户权限
- 检查防火墙设置

### 2. 前端无法访问后端接口
- 检查VUE_APP_BASE_API配置
- 确认后端服务是否正常启动
- 查看浏览器控制台错误信息

### 3. 权限配置问题
- 检查数据库中的菜单和权限配置
- 确认角色权限分配
- 清除浏览器缓存重新登录

## 🔄 二次开发说明

### 保留的若依特性
- ✅ 用户管理
- ✅ 角色权限管理
- ✅ 菜单管理
- ✅ 部门管理
- ✅ 岗位管理
- ✅ 字典管理
- ✅ 参数配置
- ✅ 操作日志
- ✅ 登录日志
- ✅ 定时任务
- ✅ 代码生成器

### 新增/修改功能
（在这里列出你的二次开发内容）
- 功能1：[描述]
- 功能2：[描述]
- 功能3：[描述]

## 📦 部署说明

### 生产环境部署
1. 修改 `application-prod.yml` 配置文件
2. 构建前端：`npm run build:prod`
3. 将dist目录内容部署到Nginx
4. 配置Nginx反向代理

### Docker部署
```bash
# 构建镜像
docker build -t ruoyi-app .

# 运行容器
docker run -d -p 8080:8080 --name ruoyi ruoyi-app
```

## 📞 技术支持

- 若依官方文档：http://doc.ruoyi.vip
- 本项目问题反馈：[填写你的联系方式或issue链接]

## 📄 许可证

本项目基于若依开源项目开发，遵循若依原有的开源协议。

---

**备注**：本项目的启动方式与原始若依项目完全相同，如果您熟悉若依的启动流程，可以直接按照原来的方式操作。
