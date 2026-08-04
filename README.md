
# 仓库进销存管理系统 - 完整设计文档

**版本**：V1.0  
**日期**：2026-07-16  
**技术栈**：微信小程序 + React Web后台 + Spring Boot + MySQL + Redis

---

## 目录

1. [项目概述与需求分析](#一项目概述与需求分析)
2. [系统架构设计](#二系统架构设计)
3. [功能模块设计](#三功能模块设计)
4. [数据库设计](#四数据库设计)
5. [接口API设计](#五接口api设计)
6. [业务流程详解](#六业务流程详解)
7. [小程序端设计](#七小程序端设计)
8. [Web后台设计](#八web后台设计)
9. [成本核算与利润计算](#九成本核算与利润计算)
10. [报表设计](#十报表设计)
11. [部署与运维方案](#十一部署与运维方案)

---

## 一、项目概述与需求分析

### 1.1 项目背景
建立一套完整的仓库进销存管理系统，对设备/物品的入库、出库、库存、成本、利润进行全流程管理。

### 1.2 核心需求

| 需求分类 | 具体需求 |
|---------|---------|
| **物品管理** | 物品档案、分类、规格型号、二维码生成与打印 |
| **入库管理** | 采购入库、入库数量、入库单价、入库金额、库位 |
| **出库管理** | 销售出库、出库数量、出库成本单价（自动）、售出价、销售金额、利润 |
| **库存管理** | 实时库存查询、库位分布、移动加权平均成本 |
| **盘点管理** | 盘点计划、扫码盘点、盈亏差异处理 |
| **报表统计** | 收发存汇总、销售利润报表、库存预警 |
| **扫码操作** | 微信小程序扫码入库/出库/盘点/查询 |

### 1.3 金额字段定义

| 字段名称 | 说明 | 来源 |
|---------|------|------|
| **入库单价** | 采购进价/单位成本 | 入库时手动录入 |
| **入库金额** | 入库数量 × 入库单价 | 系统自动计算 |
| **出库成本单价** | 出库时的库存移动加权平均成本 | 系统自动计算 |
| **出库成本金额** | 出库数量 × 出库成本单价 | 系统自动计算 |
| **售出价** | 卖给客户的单价 | 出库时手动录入 |
| **销售金额** | 出库数量 × 售出价 | 系统自动计算 |
| **利润** | 销售金额 - 出库成本金额 | 系统自动计算 |
| **利润率** | 利润 ÷ 销售金额 × 100% | 报表中计算 |

---

## 二、系统架构设计

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────┐
│                        用户层                            │
│    ┌──────────────┐              ┌──────────────┐       │
│    │  微信小程序   │              │  Web后台管理  │       │
│    │  (仓库操作员) │              │   (管理员)    │       │
│    └──────┬───────┘              └──────┬───────┘       │
└───────────┼──────────────────────────────┼───────────────┘
            │         HTTPS/WSS            │
┌───────────┼──────────────────────────────┼───────────────┐
│           │         接入层               │               │
│    ┌──────┴──────────────────────────────┴───────┐       │
│    │              Nginx (反向代理+SSL)            │       │
│    └──────────────────────┬──────────────────────┘       │
└───────────────────────────┼──────────────────────────────┘
                            │
┌───────────────────────────┼──────────────────────────────┐
│                           │        业务层                 │
│    ┌──────────────────────┴──────────────────────┐       │
│    │              API Gateway (路由/鉴权)         │       │
│    └──┬────────┬────────┬────────┬────────┬─────┘       │
│       │        │        │        │        │              │
│   ┌───┴──┐ ┌───┴──┐ ┌───┴──┐ ┌───┴──┐ ┌───┴──┐        │
│   │用户  │ │物品  │ │库存  │ │报表  │ │消息  │        │
│   │服务  │ │服务  │ │服务  │ │服务  │ │服务  │        │
│   └──────┘ └──────┘ └──────┘ └──────┘ └──────┘        │
└─────────────────────────────────────────────────────────┘
                            │
┌───────────────────────────┼──────────────────────────────┐
│                           │        数据层                 │
│    ┌──────────────────────┴──────────────────────┐       │
│    │          MySQL 8.0 (主数据库)                │       │
│    └─────────────────────────────────────────────┘       │
│    ┌─────────────────┐  ┌─────────────────────────┐      │
│    │  Redis (缓存)   │  │  MinIO/OSS (文件存储)   │      │
│    └─────────────────┘  └─────────────────────────┘      │
└─────────────────────────────────────────────────────────┘
```

### 2.2 技术选型

| 层级 | 技术方案 | 说明 |
|------|---------|------|
| **小程序端** | uni-app + Vue3 | 可编译H5，支持多端 |
| **Web后台** | React + Ant Design + ECharts | 管理界面和图表 |
| **后端框架** | Spring Boot 3.x + MyBatis-Plus | 稳定可靠，生态丰富 |
| **数据库** | MySQL 8.0 | 事务支持，ACID |
| **缓存** | Redis 7 | 缓存、单据号生成 |
| **文件存储** | MinIO / 阿里云OSS | 二维码图片存储 |
| **部署** | Docker Compose | 一键部署 |

### 2.3 后端项目结构

```
wms-server/
├── src/main/java/com/wms/
│   ├── WmsApplication.java           # 应用入口
│   ├── controller/                    # 控制器层
│   │   ├── AuthController.java        # 登录认证
│   │   ├── ItemController.java        # 物品管理
│   │   ├── StockInController.java     # 入库管理
│   │   ├── StockOutController.java    # 出库管理
│   │   ├── CheckController.java       # 盘点管理
│   │   └── ReportController.java      # 报表统计
│   ├── service/impl/                  # 业务逻辑层
│   │   ├── ItemServiceImpl.java
│   │   ├── StockServiceImpl.java      # 库存核心（成本计算）
│   │   ├── ReportServiceImpl.java
│   │   └── QrCodeServiceImpl.java     # 二维码生成
│   ├── service/                       # 业务接口层
│   │   ├── ItemService.java
│   │   ├── StockService.java
│   │   └── ...
│   ├── model/entity/                  # 数据实体
│   │   ├── Item.java
│   │   ├── Inventory.java
│   │   ├── StockOrder.java
│   │   └── ...
│   ├── mapper/                        # 数据访问层(MyBatis-Plus)
│   │   ├── ItemMapper.java
│   │   ├── InventoryMapper.java
│   │   └── ...
│   ├── config/                        # 配置类
│   │   ├── SecurityConfig.java        # Spring Security + JWT
│   │   ├── MyBatisPlusConfig.java
│   │   └── RedisConfig.java
│   ├── common/                        # 通用工具
│   │   ├── Result.java                # 统一响应封装
│   │   ├── CodeGenerator.java         # 编码生成
│   │   └── ExcelUtil.java             # Excel导入导出
│   └── dto/                           # 数据传输对象
│       ├── StockInScanDTO.java
│       └── StockOutScanDTO.java
├── src/main/resources/
│   ├── application.yml                # 主配置
│   ├── application-dev.yml            # 开发环境配置
│   └── mapper/                        # MyBatis XML映射文件
├── pom.xml                            # Maven依赖管理
├── Dockerfile
└── docker-compose.yml
```

---

## 三、功能模块设计

### 3.1 功能模块总览

```
仓库进销存管理系统
├── 1. 系统管理
│   ├── 1.1 用户管理
│   ├── 1.2 角色与权限
│   ├── 1.3 仓库设置
│   ├── 1.4 库位管理
│   └── 1.5 操作日志
├── 2. 基础数据
│   ├── 2.1 物品分类
│   ├── 2.2 物品档案（含二维码生成）
│   └── 2.3 往来单位（供应商/客户）
├── 3. 入库管理
│   ├── 3.1 采购入库单
│   ├── 3.2 退货入库单
│   ├── 3.3 其他入库单
│   └── 3.4 小程序扫码入库
├── 4. 出库管理
│   ├── 4.1 销售出库单
│   ├── 4.2 生产领料单
│   ├── 4.3 其他出库单
│   └── 4.4 小程序扫码出库
├── 5. 库存管理
│   ├── 5.1 库存查询
│   ├── 5.2 库存分布（按库位）
│   ├── 5.3 库存流水
│   └── 5.4 库存调拨
├── 6. 盘点管理
│   ├── 6.1 盘点计划
│   ├── 6.2 扫码盘点
│   └── 6.3 盈亏处理
└── 7. 报表中心
    ├── 7.1 收发存汇总表
    ├── 7.2 物品收发存明细
    ├── 7.3 销售利润报表
    ├── 7.4 客户销售统计
    └── 7.5 库存预警报表
```

### 3.2 详细功能说明

#### 3.2.1 物品档案管理

**功能点**：
- 新增/编辑/删除/查询物品
- 自动生成唯一编码（如：`ITEM-20260716-0001`）
- 根据编码自动生成二维码图片
- 支持批量导入/导出Excel
- 支持条形码和二维码两种模式
- 设置安全库存、最大/最小库存

**二维码生成规则**：
- 内容格式：`https://域名/pages/item/in?id={item_code}`
- 或直接编码：`{item_code}`
- 生成图片后存入MinIO，返回可下载URL
- 支持单个生成和批量生成

#### 3.2.2 入库管理

**入库单状态流转**：
```
草稿(draft) → 已确认(confirmed) → 已审核 → 完成
                ↓
             已取消(cancelled)
```

**小程序扫码入库流程**：
1. 点击"扫码入库"
2. 扫描设备二维码 → 解析`item_code`
3. 显示物品详情（名称、规格、当前库存）
4. 操作员输入：入库数量、入库单价、选择库位
5. 系统自动计算入库金额
6. 确认入库 → 更新库存成本 → 写入流水

**计算逻辑**：
```
入库金额 = 入库数量 × 入库单价
新平均成本 = (库存原值 + 入库金额) ÷ (库存原数量 + 入库数量)
```

#### 3.2.3 出库管理

**小程序扫码出库流程**：
1. 点击"扫码出库"
2. 扫描设备二维码 → 解析`item_code`
3. 显示物品详情（当前库存、平均成本）
4. 操作员输入：出库数量、**售出价**
5. 系统自动显示：
   - 出库成本单价 = 当前库存平均成本（不可修改）
   - 出库成本金额 = 出库数量 × 出库成本单价
   - 销售金额 = 出库数量 × 售出价
   - **利润 = 销售金额 - 出库成本金额**
6. 确认出库 → 扣减库存 → 写入流水

#### 3.2.4 盘点管理

**盘点流程**：
1. Web端创建盘点计划（选择仓库、范围）
2. 小程序扫码盘点：逐物品扫码，录入实盘数量
3. 系统自动比对：账面数量 vs 实盘数量
4. 生成盈亏差异：`差异 = 实盘 - 账面`
5. 差异处理：盘盈入库 / 盘亏出库，更新库存

---

## 四、数据库设计

### 4.1 数据库ER图（核心关系）

```
categories ──┐
              ├──< items >──┬──< inventory >──┬── locations
              │              │                  └── warehouses
partners ─────┤              │
              │              ├──< inventory_transactions >
              │              │
              ├──< stock_in_order_items >──< stock_in_orders
              │
              └──< stock_out_order_items >──< stock_out_orders
                      
check_orders ──< check_order_items >── items
```

### 4.2 完整建表SQL

```sql
-- ==================== 基础数据 ====================

-- 仓库表
CREATE TABLE `warehouses` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `code` VARCHAR(50) UNIQUE NOT NULL COMMENT '仓库编码',
    `name` VARCHAR(100) NOT NULL COMMENT '仓库名称',
    `type` ENUM('normal','quarantine','scrap') DEFAULT 'normal' COMMENT '仓库类型',
    `address` VARCHAR(200),
    `status` TINYINT(1) DEFAULT 1,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='仓库表';

-- 库位表
CREATE TABLE `locations` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `warehouse_id` INT NOT NULL,
    `code` VARCHAR(50) NOT NULL COMMENT '库位号，如A-01-02',
    `barcode` VARCHAR(100) COMMENT '库位条码',
    `status` TINYINT(1) DEFAULT 1,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_wh_location` (`warehouse_id`, `code`),
    FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses`(`id`)
) COMMENT='库位表';

-- 物品分类表
CREATE TABLE `categories` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `parent_id` INT DEFAULT 0,
    `name` VARCHAR(100) NOT NULL,
    `sort` INT DEFAULT 0,
    `status` TINYINT(1) DEFAULT 1,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) COMMENT='物品分类表';

-- 物品档案表（核心）
CREATE TABLE `items` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `code` VARCHAR(50) UNIQUE NOT NULL COMMENT '物品编码，二维码内容',
    `name` VARCHAR(200) NOT NULL COMMENT '物品名称',
    `category_id` INT,
    `unit` VARCHAR(20) DEFAULT '个' COMMENT '单位',
    `specs` VARCHAR(200) COMMENT '规格型号',
    `brand` VARCHAR(100) COMMENT '品牌',
    `model` VARCHAR(100) COMMENT '型号',
    `barcode` VARCHAR(100) COMMENT '条形码',
    `qrcode_url` VARCHAR(500) COMMENT '二维码图片URL',
    `cost_method` ENUM('average','fifo') DEFAULT 'average' COMMENT '成本计价法',
    `safety_stock` DECIMAL(18,4) DEFAULT 0 COMMENT '安全库存',
    `max_stock` DECIMAL(18,4) DEFAULT 0 COMMENT '最大库存',
    `min_stock` DECIMAL(18,4) DEFAULT 0 COMMENT '最小库存',
    `initial_quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '期初数量',
    `initial_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '期初金额',
    `remark` TEXT,
    `status` TINYINT(1) DEFAULT 1 COMMENT '1启用 0禁用',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`category_id`) REFERENCES `categories`(`id`)
) COMMENT='物品档案表';

-- 往来单位表
CREATE TABLE `partners` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `code` VARCHAR(50) UNIQUE NOT NULL,
    `name` VARCHAR(200) NOT NULL,
    `type` ENUM('supplier','customer','both') NOT NULL,
    `contact_person` VARCHAR(50),
    `phone` VARCHAR(20),
    `address` VARCHAR(200),
    `status` TINYINT(1) DEFAULT 1,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) COMMENT='往来单位表';

-- ==================== 库存核心 ====================

-- 库存表（带金额）
CREATE TABLE `inventory` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `item_id` INT NOT NULL,
    `warehouse_id` INT NOT NULL,
    `location_id` INT,
    `quantity` DECIMAL(18,4) DEFAULT 0.0000 COMMENT '库存数量',
    `total_amount` DECIMAL(18,2) DEFAULT 0.00 COMMENT '库存金额',
    `avg_cost` DECIMAL(18,4) DEFAULT 0.0000 COMMENT '移动加权平均成本',
    `last_in_cost` DECIMAL(18,4) DEFAULT 0.0000 COMMENT '最近入库单价',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_item_location` (`item_id`, `warehouse_id`, `location_id`),
    INDEX `idx_warehouse` (`warehouse_id`),
    FOREIGN KEY (`item_id`) REFERENCES `items`(`id`),
    FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses`(`id`),
    FOREIGN KEY (`location_id`) REFERENCES `locations`(`id`)
) COMMENT='库存表';

-- 库存流水表（查账核心）
CREATE TABLE `inventory_transactions` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `item_id` INT NOT NULL,
    `warehouse_id` INT NOT NULL,
    `location_id` INT,
    `transaction_type` ENUM('in','out','transfer','adjust','check') NOT NULL COMMENT '交易类型',
    `reference_type` VARCHAR(50) COMMENT '来源单据类型',
    `reference_id` INT COMMENT '来源单据ID',
    
    -- 变动信息
    `quantity` DECIMAL(18,4) NOT NULL COMMENT '变动数量(正入负出)',
    `unit_cost` DECIMAL(18,4) NOT NULL COMMENT '变动单价',
    `total_cost_amount` DECIMAL(18,2) NOT NULL COMMENT '变动成本金额',
    
    -- 销售维度(仅出库时)
    `sale_price` DECIMAL(18,4) DEFAULT 0 COMMENT '售出价',
    `total_sale_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '销售金额',
    `profit` DECIMAL(18,2) DEFAULT 0 COMMENT '利润',
    
    -- 结存信息
    `balance_quantity` DECIMAL(18,4) NOT NULL COMMENT '结存数量',
    `balance_amount` DECIMAL(18,2) NOT NULL COMMENT '结存金额',
    `avg_cost_after` DECIMAL(18,4) NOT NULL COMMENT '变动后平均成本',
    
    `operator_id` INT,
    `remark` VARCHAR(200),
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_item_time` (`item_id`, `created_at`),
    INDEX `idx_reference` (`reference_type`, `reference_id`),
    INDEX `idx_created_at` (`created_at`),
    FOREIGN KEY (`item_id`) REFERENCES `items`(`id`)
) COMMENT='库存流水表';

-- ==================== 入库单据 ====================

-- 入库单主表
CREATE TABLE `stock_in_orders` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `order_no` VARCHAR(50) UNIQUE NOT NULL COMMENT '入库单号',
    `partner_id` INT COMMENT '供应商',
    `order_type` ENUM('purchase','return','production','transfer','initial','others') DEFAULT 'purchase',
    `total_quantity` DECIMAL(18,4) DEFAULT 0.0000,
    `total_amount` DECIMAL(18,2) DEFAULT 0.00 COMMENT '入库总金额',
    `inbound_date` DATE NOT NULL COMMENT '入库日期',
    `operator_id` INT NOT NULL,
    `reviewer_id` INT,
    `status` ENUM('draft','confirmed','cancelled') DEFAULT 'draft',
    `remark` TEXT,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_inbound_date` (`inbound_date`),
    INDEX `idx_status` (`status`),
    FOREIGN KEY (`partner_id`) REFERENCES `partners`(`id`)
) COMMENT='入库单主表';

-- 入库单明细表
CREATE TABLE `stock_in_order_items` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `order_id` INT NOT NULL,
    `item_id` INT NOT NULL,
    `warehouse_id` INT NOT NULL,
    `location_id` INT,
    `quantity` DECIMAL(18,4) NOT NULL COMMENT '入库数量',
    `unit_cost` DECIMAL(18,4) NOT NULL COMMENT '入库单价',
    `total_amount` DECIMAL(18,2) NOT NULL COMMENT '明细金额',
    `batch_no` VARCHAR(50),
    `produced_date` DATE,
    `expiry_date` DATE,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_order_id` (`order_id`),
    FOREIGN KEY (`order_id`) REFERENCES `stock_in_orders`(`id`),
    FOREIGN KEY (`item_id`) REFERENCES `items`(`id`),
    FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses`(`id`)
) COMMENT='入库单明细表';

-- ==================== 出库单据 ====================

-- 出库单主表
CREATE TABLE `stock_out_orders` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `order_no` VARCHAR(50) UNIQUE NOT NULL COMMENT '出库单号',
    `partner_id` INT COMMENT '客户',
    `order_type` ENUM('sale','production','return_supplier','scrap','others') DEFAULT 'sale',
    `total_quantity` DECIMAL(18,4) DEFAULT 0.0000,
    `total_cost_amount` DECIMAL(18,2) DEFAULT 0.00 COMMENT '总成本金额',
    `total_sale_amount` DECIMAL(18,2) DEFAULT 0.00 COMMENT '总销售金额',
    `total_profit` DECIMAL(18,2) DEFAULT 0.00 COMMENT '总利润',
    `outbound_date` DATE NOT NULL COMMENT '出库日期',
    `operator_id` INT NOT NULL,
    `reviewer_id` INT,
    `status` ENUM('draft','confirmed','cancelled') DEFAULT 'draft',
    `remark` TEXT,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_outbound_date` (`outbound_date`),
    INDEX `idx_status` (`status`),
    FOREIGN KEY (`partner_id`) REFERENCES `partners`(`id`)
) COMMENT='出库单主表';

-- 出库单明细表
CREATE TABLE `stock_out_order_items` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `order_id` INT NOT NULL,
    `item_id` INT NOT NULL,
    `warehouse_id` INT NOT NULL,
    `location_id` INT,
    `quantity` DECIMAL(18,4) NOT NULL COMMENT '出库数量',
    
    -- 成本相关
    `unit_cost` DECIMAL(18,4) NOT NULL COMMENT '出库成本单价(系统自动)',
    `total_cost_amount` DECIMAL(18,2) NOT NULL COMMENT '出库成本金额',
    
    -- 销售相关
    `sale_price` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '售出价',
    `total_sale_amount` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '销售金额',
    `profit` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '利润',
    
    `batch_no` VARCHAR(50),
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_order_id` (`order_id`),
    FOREIGN KEY (`order_id`) REFERENCES `stock_out_orders`(`id`),
    FOREIGN KEY (`item_id`) REFERENCES `items`(`id`),
    FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses`(`id`)
) COMMENT='出库单明细表';

-- ==================== 盘点单据 ====================

-- 盘点单主表
CREATE TABLE `check_orders` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `order_no` VARCHAR(50) UNIQUE NOT NULL,
    `warehouse_id` INT NOT NULL,
    `check_date` DATE NOT NULL,
    `status` ENUM('draft','in_progress','confirmed','cancelled') DEFAULT 'draft',
    `creator_id` INT NOT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses`(`id`)
) COMMENT='盘点单主表';

-- 盘点单明细表
CREATE TABLE `check_order_items` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `check_order_id` INT NOT NULL,
    `item_id` INT NOT NULL,
    `location_id` INT,
    `book_quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '账面数量',
    `actual_quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '实盘数量',
    `difference` DECIMAL(18,4) DEFAULT 0 COMMENT '差异(实盘-账面)',
    `unit_cost` DECIMAL(18,4) COMMENT '当时成本单价',
    `total_diff_amount` DECIMAL(18,2) COMMENT '差异金额',
    `reason` VARCHAR(200) COMMENT '差异原因',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`check_order_id`) REFERENCES `check_orders`(`id`),
    FOREIGN KEY (`item_id`) REFERENCES `items`(`id`)
) COMMENT='盘点单明细表';

-- ==================== 系统管理 ====================

-- 用户表
CREATE TABLE `users` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `username` VARCHAR(50) UNIQUE NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `real_name` VARCHAR(50),
    `openid` VARCHAR(100) COMMENT '微信openid',
    `phone` VARCHAR(20),
    `role_id` INT,
    `status` TINYINT(1) DEFAULT 1,
    `last_login_at` TIMESTAMP,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) COMMENT='用户表';

-- 操作日志表
CREATE TABLE `operation_logs` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` INT,
    `module` VARCHAR(50),
    `action` VARCHAR(50),
    `target` VARCHAR(200),
    `ip` VARCHAR(50),
    `content` TEXT,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) COMMENT='操作日志表';
```

---

## 五、接口API设计

### 5.1 接口规范

- **Base URL**：`https://api.xxx.com/v1`
- **请求格式**：JSON
- **响应格式**：
```json
{
    "code": 200,
    "message": "success",
    "data": {}
}
```

### 5.2 完整接口列表

#### 认证模块
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /auth/login | 用户名密码登录 |
| POST | /auth/wx-login | 微信登录：未绑定返回 `{needBind:true, openid}`，已绑定直接返回 token |
| POST | /auth/wx-bind | 微信绑定账号 `{openid, username, password}`，绑定后签发 token |
| GET | /auth/me | 当前用户信息 |
| POST | /auth/logout | 退出登录 |
| GET | /auth/permissions | 角色权限矩阵 |
| POST | /auth/refresh | 刷新token |

#### 物品管理
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /items | 物品列表(分页+搜索) |
| GET | /items/:id | 物品详情 |
| POST | /items | 新增物品 |
| PUT | /items/:id | 编辑物品 |
| DELETE | /items/:id | 删除物品 |
| GET | /items/code/:code | 根据编码查物品(扫码用) |
| POST | /items/batch-import | 批量导入Excel |
| GET | /items/export | 导出Excel |
| POST | /items/:id/qrcode | 生成单个二维码 |
| POST | /items/qrcode/batch | 批量生成二维码 |
| GET | /items/:id/qrcode/download | 下载二维码图片 |

#### 入库管理
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /stock-in/orders | 入库单列表 |
| GET | /stock-in/orders/:id | 入库单详情 |
| POST | /stock-in/orders | 创建入库单(草稿) |
| PUT | /stock-in/orders/:id | 编辑入库单 |
| PUT | /stock-in/orders/:id/confirm | **确认入库(核心)** |
| PUT | /stock-in/orders/:id/cancel | 取消入库单 |
| POST | /stock/in/scan | **小程序扫码入库** |

#### 出库管理
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /stock-out/orders | 出库单列表 |
| GET | /stock-out/orders/:id | 出库单详情 |
| POST | /stock-out/orders | 创建出库单(草稿) |
| PUT | /stock-out/orders/:id | 编辑出库单 |
| PUT | /stock-out/orders/:id/confirm | **确认出库(核心)** |
| PUT | /stock-out/orders/:id/cancel | 取消出库单 |
| POST | /stock/out/scan | **小程序扫码出库** |

#### 库存管理
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /inventory | 库存列表 |
| GET | /inventory/:item_id | 物品库存汇总 |
| GET | /inventory/:item_id/distribution | 物品库位分布 |
| GET | /inventory/transactions | 库存流水 |
| POST | /inventory/transfer | 库存调拨 |

#### 盘点管理
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /check/orders | 盘点单列表 |
| POST | /check/orders | 创建盘点单 |
| PUT | /check/orders/:id/confirm | 确认盘点 |
| POST | /check/scan | **小程序扫码盘点** |

#### 报表统计
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /reports/stock-summary | 收发存汇总表 |
| GET | /reports/item-detail/:item_id | 物品收发存明细 |
| GET | /reports/profit | 销售利润报表 |
| GET | /reports/customer-profit | 客户销售统计 |
| GET | /reports/stock-alert | 库存预警报表 |

### 5.3 核心接口详细定义

#### 5.3.1 扫码入库接口

**请求**：`POST /stock/in/scan`
```json
{
    "item_code": "ITEM-001",
    "quantity": 100,
    "unit_cost": 15.50,
    "warehouse_id": 1,
    "location_code": "A-01-02",
    "batch_no": "B20260716",
    "produced_date": "2026-07-01",
    "expiry_date": "2027-07-01",
    "remark": "采购入库"
}
```

**响应**：
```json
{
    "code": 200,
    "message": "入库成功",
    "data": {
        "order_no": "RK-20260716-0001",
        "item_name": "某型号设备",
        "quantity": 100,
        "total_amount": 1550.00,
        "new_avg_cost": 14.80,
        "new_stock_quantity": 300,
        "new_stock_amount": 4440.00
    }
}
```

#### 5.3.2 扫码出库接口

**请求**：`POST /stock/out/scan`
```json
{
    "item_code": "ITEM-001",
    "quantity": 10,
    "sale_price": 25.00,
    "warehouse_id": 1,
    "location_code": "A-01-02",
    "partner_id": 1,
    "remark": "销售出库"
}
```

**响应**：
```json
{
    "code": 200,
    "message": "出库成功",
    "data": {
        "order_no": "CK-20260716-0001",
        "item_name": "某型号设备",
        "quantity": 10,
        "unit_cost": 14.80,
        "total_cost_amount": 148.00,
        "sale_price": 25.00,
        "total_sale_amount": 250.00,
        "profit": 102.00,
        "new_stock_quantity": 290,
        "new_stock_amount": 4292.00
    }
}
```

#### 5.3.3 确认入库后端处理逻辑（伪代码）

```java
@Service
@Transactional(rollbackFor = Exception.class)
public class StockServiceImpl implements StockService {

    @Autowired
    private StockInOrderMapper orderMapper;
    @Autowired
    private StockInOrderItemMapper itemMapper;
    @Autowired
    private InventoryMapper inventoryMapper;
    @Autowired
    private InventoryTransactionMapper transactionMapper;

    public void confirmStockIn(Long orderId) {
        // 1. 获取入库单及明细
        StockInOrder order = orderMapper.selectById(orderId);
        List<StockInOrderItem> items = itemMapper.selectList(
            Wrappers.lambdaQuery(StockInOrderItem.class)
                .eq(StockInOrderItem::getOrderId, orderId));

        // 2. 逐条处理明细
        for (StockInOrderItem item : items) {
            // 2.1 获取当前库存
            Inventory inv = inventoryMapper.selectOne(
                Wrappers.lambdaQuery(Inventory.class)
                    .eq(Inventory::getItemId, item.getItemId())
                    .eq(Inventory::getWarehouseId, item.getWarehouseId())
                    .eq(Inventory::getLocationId, item.getLocationId()));

            // 2.2 计算新平均成本
            BigDecimal newQty = inv.getQuantity().add(item.getQuantity());
            BigDecimal newAmount = inv.getTotalAmount().add(item.getTotalAmount());
            BigDecimal newAvgCost = newAmount.divide(newQty, 4, RoundingMode.HALF_UP);

            // 2.3 更新库存
            inv.setQuantity(newQty);
            inv.setTotalAmount(newAmount);
            inv.setAvgCost(newAvgCost);
            inv.setLastInCost(item.getUnitCost());
            inventoryMapper.updateById(inv);

            // 2.4 写入库存流水
            InventoryTransaction tx = new InventoryTransaction();
            tx.setItemId(item.getItemId());
            tx.setWarehouseId(item.getWarehouseId());
            tx.setLocationId(item.getLocationId());
            tx.setTransactionType("in");
            tx.setReferenceType("stock_in_order");
            tx.setReferenceId(orderId);
            tx.setQuantity(item.getQuantity());
            tx.setUnitCost(item.getUnitCost());
            tx.setTotalCostAmount(item.getTotalAmount());
            tx.setBalanceQuantity(newQty);
            tx.setBalanceAmount(newAmount);
            tx.setAvgCostAfter(newAvgCost);
            transactionMapper.insert(tx);
        }

        // 3. 更新入库单状态
        order.setStatus("confirmed");
        orderMapper.updateById(order);
    }
}
```

---

## 六、业务流程详解

### 6.1 整体业务流程图

```
┌─────────────┐
│  期初库存   │ (initial_quantity, initial_amount)
└──────┬──────┘
       │
       ├──→ 采购入库 (+数量, +金额, 重新计算平均成本)
       │
       ├──→ 销售出库 (-数量, -金额, 记录售出价和利润)
       │
       ├──→ 库存调拨 (A库→B库, 总库存不变)
       │
       ├──→ 盘点调整 (盘盈入库/盘亏出库)
       │
       └──→ 期末库存 = 期初 + 入库 - 出库 ± 调整
```

### 6.2 物品生命周期

```
创建物品档案 → 生成二维码 → 打印贴码 → 
采购入库(首次入库，设置初始成本) → 
库存可用 → 
销售出库(按平均成本结转，记录利润) → 
库存减少 → 
定期盘点 → 
持续循环
```

### 6.3 单据编号规则

| 单据类型 | 编号规则 | 示例 |
|---------|---------|------|
| 入库单 | RK-YYYYMMDD-序号 | RK-20260716-0001 |
| 出库单 | CK-YYYYMMDD-序号 | CK-20260716-0001 |
| 盘点单 | PD-YYYYMMDD-序号 | PD-20260716-0001 |
| 调拨单 | DB-YYYYMMDD-序号 | DB-20260716-0001 |

---

## 七、小程序端设计

### 7.1 页面结构

实际实现位于 `wms-miniapp/`（uni-app + Vue 3 + Pinia，可编译微信小程序 / H5）：

```
wms-miniapp/src/
├── main.js                  # 入口（挂载 Pinia，401 拦截跳登录）
├── pages.json               # 15 个页面 + 4 个 tabBar（首页/扫码/库存/我的）
├── api/
│   ├── request.js           # 统一请求封装：token 注入、401 跳转、ApiResponse 解壳
│   └── (全量业务接口)
├── store/user.js            # 用户/token/权限/当前仓库
├── utils/format.js          # 金额/数量/日期格式化
└── pages/
    ├── login/               # 登录（微信一键登录 / 账号密码，含首次绑定流程）
    ├── index/               # 首页（快捷入口 + 今日汇总 + 预警提示）
    ├── scan/                # 通用扫码页（扫描物品码 → 物品详情）
    ├── stock-in/            # 扫码入库（仅 ADMIN，显示"仅管理员"提示条）
    ├── stock-out/           # 扫码出库（仅 ADMIN，实时计算利润）
    ├── inventory/           # 库存查询（按仓库/关键字过滤，库位分布）
    ├── item-list/           # 物品列表（分页 + 库存摘要）
    ├── item-detail/         # 物品详情（信息 + 库存分布 + 二维码）
    ├── check/               # 盘点任务列表（草稿可进入录入）
    ├── check-count/         # 盘点录入（扫码或手动填实盘数量）
    ├── document-list/       # 单据列表（单据/调拨/盘点，类型+状态筛选）
    ├── document-detail/     # 单据/调拨/盘点详情（前端自算汇总金额）
    ├── transactions/        # 库存流水（类型筛选 + 搜索）
    ├── reports/             # 报表中心（看板 + 预警 + 分类分布 + 利润趋势）
    └── mine/                # 我的（仓库切换、功能菜单、操作记录、退出）
```

### 7.2 首页功能菜单

```
┌──────────────────────────┐
│     仓库管理系统         │
│   欢迎: 张三             │
├──────────────────────────┤
│  ┌────┐ ┌────┐ ┌────┐   │
│  │扫码│ │扫码│ │库存│   │
│  │入库│ │出库│ │查询│   │
│  └────┘ └────┘ └────┘   │
│  ┌────┐ ┌────┐ ┌────┐   │
│  │扫码│ │盘点│ │物品│   │
│  │查询│ │任务│ │查询│   │
│  └────┘ └────┘ └────┘   │
└──────────────────────────┘
```

### 7.3 扫码入库页面原型

```
┌──────────────────────────┐
│  ← 返回    扫码入库      │
├──────────────────────────┤
│ 物品编码: ITEM-001       │
│ 物品名称: 某型号设备      │
│ 规格型号: XXL-2026       │
│ 当前库存: 200 个         │
│ 平均成本: ¥14.00         │
│                          │
│ ┌────────────────────┐   │
│ │ 入库数量           │   │
│ │ [ 100 ]  个         │   │
│ └────────────────────┘   │
│ ┌────────────────────┐   │
│ │ 入库单价           │   │
│ │ [ 15.50 ] 元        │   │
│ └────────────────────┘   │
│ ┌────────────────────┐   │
│ │ 入库金额(自动)      │   │
│ │ ¥1,550.00          │   │
│ └────────────────────┘   │
│ ┌────────────────────┐   │
│ │ 库位: [A-01-02]    │   │
│ └────────────────────┘   │
│ ┌────────────────────┐   │
│ │ 批次号: [B20260716]│   │
│ └────────────────────┘   │
│                          │
│  ┌────────────────────┐  │
│  │     确认入库       │  │
│  └────────────────────┘  │
└──────────────────────────┘
```

### 7.4 扫码出库页面原型

```
┌──────────────────────────┐
│  ← 返回    扫码出库      │
├──────────────────────────┤
│ 物品编码: ITEM-001       │
│ 物品名称: 某型号设备      │
│ 当前库存: 300 个         │
│ 平均成本: ¥14.80 (系统)  │
│                          │
│ ┌────────────────────┐   │
│ │ 出库数量           │   │
│ │ [ 10 ]   个         │   │
│ └────────────────────┘   │
│ ┌────────────────────┐   │
│ │ 售出单价           │   │
│ │ [ 25.00 ] 元        │   │
│ └────────────────────┘   │
│                          │
│ 成本金额: ¥148.00 (自动) │
│ 销售金额: ¥250.00 (自动) │
│ ┌────────────────────┐   │
│ │ 利润: ¥102.00      │   │
│ │ 利润率: 40.8%      │   │
│ └────────────────────┘   │
│                          │
│  ┌────────────────────┐  │
│  │     确认出库       │  │
│  └────────────────────┘  │
└──────────────────────────┘
```

### 7.5 小程序核心代码示例

#### 扫码入库完整流程

```javascript
// pages/stock/in/scan.js
Page({
  data: {
    item: null,
    quantity: '',
    unitCost: '',
    totalAmount: '0.00',
    locationCode: '',
    batchNo: ''
  },

  // 扫码
  scanCode() {
    wx.scanCode({
      scanType: ['qrCode', 'barCode'],
      success: (res) => {
        const itemCode = res.result;
        this.getItemInfo(itemCode);
      }
    });
  },

  // 获取物品信息
  getItemInfo(code) {
    wx.request({
      url: 'https://api.xxx.com/v1/items/code/' + code,
      method: 'GET',
      success: (res) => {
        if (res.data.code === 200) {
          this.setData({ item: res.data.data });
        } else {
          wx.showToast({ title: '物品未找到', icon: 'error' });
        }
      }
    });
  },

  // 数量变更时自动计算金额
  onQuantityChange(e) {
    const quantity = parseFloat(e.detail.value) || 0;
    const unitCost = parseFloat(this.data.unitCost) || 0;
    this.setData({
      quantity: e.detail.value,
      totalAmount: (quantity * unitCost).toFixed(2)
    });
  },

  // 单价变更时自动计算金额
  onUnitCostChange(e) {
    const unitCost = parseFloat(e.detail.value) || 0;
    const quantity = parseFloat(this.data.quantity) || 0;
    this.setData({
      unitCost: e.detail.value,
      totalAmount: (quantity * unitCost).toFixed(2)
    });
  },

  // 确认入库
  confirmStockIn() {
    const { item, quantity, unitCost, locationCode, batchNo } = this.data;
    
    if (!quantity || !unitCost) {
      wx.showToast({ title: '请填写数量和单价', icon: 'none' });
      return;
    }

    wx.request({
      url: 'https://api.xxx.com/v1/stock/in/scan',
      method: 'POST',
      data: {
        item_code: item.code,
        quantity: parseFloat(quantity),
        unit_cost: parseFloat(unitCost),
        warehouse_id: 1,
        location_code: locationCode,
        batch_no: batchNo
      },
      success: (res) => {
        if (res.data.code === 200) {
          wx.showToast({ title: '入库成功', icon: 'success' });
          setTimeout(() => wx.navigateBack(), 1500);
        } else {
          wx.showToast({ title: res.data.message, icon: 'error' });
        }
      }
    });
  }
});
```

### 7.6 微信登录与账号绑定

- 后端 `wechat.mock=true`（默认）：code 直接作为 openid 使用，本地联调无需真实 appid/secret；生产置 `false` 并注入 `WECHAT_APPID` / `WECHAT_SECRET` 环境变量。
- 绑定关系存于 `user_accounts.openid`（唯一约束），同一 openid 不可绑定多个账号。
- 登录流程：

```
小程序 uni.login() 取 code
  → POST /auth/wx-login {code}
    ├─ 已绑定  → 返回 {token, username, role, permissions}（同账号密码登录壳）
    └─ 未绑定  → 返回 {needBind: true, openid}
                   → POST /auth/wx-bind {openid, username, password}
                   → 返回 {token, ...}
```

- 前端统一请求封装（`src/api/request.js`）：token 注入 `Authorization: Bearer`，401 清 token 跳登录页，仅在 `code === 200` 时 resolve `data`。
- 角色差异：ADMIN 可扫码直接出入库（`POST /stock/in/scan`、`/stock/out/scan` 强制 `hasRole('ADMIN')`）；WAREHOUSE 页面显示"仅管理员"提示，走单据流程与盘点录入；操作日志 `GET /logs` 仅 ADMIN 可见。

---

## 八、Web后台设计（React + Ant Design）

### 8.1 后台菜单结构

```
├── 仪表盘（首页看板）
│   ├── 库存总览
│   ├── 今日入库/出库统计
│   └── 库存预警提醒
├── 基础数据
│   ├── 物品分类
│   ├── 物品档案
│   └── 往来单位
├── 入库管理
│   ├── 入库单列表
│   └── 新建入库单
├── 出库管理
│   ├── 出库单列表
│   └── 新建出库单
├── 库存管理
│   ├── 库存查询
│   ├── 库存流水
│   └── 库存调拨
├── 盘点管理
│   ├── 盘点计划
│   └── 盘点记录
├── 报表中心
│   ├── 收发存汇总表
│   ├── 物品收发存明细
│   ├── 销售利润报表
│   └── 客户销售统计
└── 系统管理
    ├── 用户管理
    ├── 角色权限
    └── 操作日志
```

### 8.2 物品档案页面原型

```
┌──────────────────────────────────────────────────────┐
│  物品档案管理                    [+新增] [批量导入]   │
├──────────────────────────────────────────────────────┤
│  搜索: [________]  分类: [全部▼]  状态: [启用▼]      │
├──────────────────────────────────────────────────────┤
│ 编码    │ 名称      │ 规格    │ 单位│ 库存 │ 成本  │操作│
│ ITEM-01│ 设备A     │ XXL-2026│ 个  │ 200  │ 14.00│[编]│
│ ITEM-02│ 设备B     │ XXL-2027│ 个  │ 150  │ 12.00│[编]│
├──────────────────────────────────────────────────────┤
│              < 1 2 3 ... 10 >  共200条               │
└──────────────────────────────────────────────────────┘
```

### 8.3 入库单详情页面原型

```
┌──────────────────────────────────────────────────────┐
│  入库单详情 - RK-20260716-0001        [确认] [取消]   │
├──────────────────────────────────────────────────────┤
│  基本信息                                            │
│  供应商: 某供应商      入库日期: 2026-07-16           │
│  类型: 采购入库        状态: 草稿                     │
│  操作人: 张三                                         │
├──────────────────────────────────────────────────────┤
│  物品明细                               [+添加明细]  │
│ ┌──────────────────────────────────────────────┐     │
│ │ 物品    │数量│单价  │金额  │库位    │批次    │     │
│ │ 设备A   │100 │15.50│1550 │A-01-02│B2026..│     │
│ │ 设备B   │50  │12.00│600  │A-01-03│B2026..│     │
│ ├──────────────────────────────────────────────┤     │
│ │ 合计    │150 │     │2150 │        │        │     │
│ └──────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────┘
```

---

## 九、成本核算与利润计算

### 9.1 移动加权平均法详解

**公式**：
```
新平均成本 = (库存原值 + 本次入库金额) ÷ (库存原数量 + 本次入库数量)
```

**示例**：

| 日期 | 操作 | 数量 | 单价 | 金额 | 库存数量 | 库存金额 | 平均成本 |
|------|------|------|------|------|---------|---------|---------|
| 7/1 | 期初 | 100 | 10 | 1000 | 100 | 1000 | 10.00 |
| 7/5 | 入库 | 200 | 12 | 2400 | 300 | 3400 | 11.33 |
| 7/10 | 出库 | 50 | 11.33 | 566.67 | 250 | 2833.33 | 11.33 |
| 7/15 | 入库 | 150 | 15 | 2250 | 400 | 5083.33 | 12.71 |
| 7/20 | 出库 | 100 | 12.71 | 1270.83 | 300 | 3812.50 | 12.71 |

**关键点**：
- 出库时，`unit_cost` = 当前库存的 `avg_cost`（不可修改）
- 只有入库时，平均成本才会变化
- 出库只扣减数量和金额，不改变平均成本

### 9.2 利润计算公式

```
单条明细利润 = 销售金额 - 出库成本金额
             = (出库数量 × 售出价) - (出库数量 × 出库成本单价)

利润率 = 利润 ÷ 销售金额 × 100%

单据总利润 = 所有明细利润之和
```

**示例**：
```
出库数量: 10个
出库成本单价: ¥14.80 (系统自动)
售出价: ¥25.00 (手动录入)

成本金额 = 10 × 14.80 = ¥148.00
销售金额 = 10 × 25.00 = ¥250.00
利润 = 250.00 - 148.00 = ¥102.00
利润率 = 102.00 ÷ 250.00 × 100% = 40.8%
```

---

## 十、报表设计

### 10.1 收发存汇总表

```
┌──────────────────────────────────────────────────────────────────┐
│                      收发存汇总表                                │
│                  2026年7月1日 - 2026年7月31日                     │
├────┬───┬─────┬────┬──────┬──────┬──────┬──────┬──────┬──────┬────┤
│编码│名称│单位 │期初│本期入库│ 本期出库 │期末结存│      │      │    │
│    │    │    │数量│数量│金额│数量│成本│销售│利润│数量│金额│    │
│    │    │    │    │    │    │    │金额│金额│    │    │    │    │
├────┼───┼─────┼────┼────┼────┼────┼────┼────┼────┼────┼────┼────┤
│    │    │    │    │    │    │    │    │    │    │    │    │    │
└────┴───┴─────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┘
```

**SQL实现**（参见第四章`inventory_transactions`表查询）

### 10.2 销售利润报表

```
┌──────────────────────────────────────────────────────────┐
│                    销售利润报表                           │
│               2026年7月1日 - 2026年7月31日                │
├────┬───┬────┬──────┬──────┬──────┬──────┬──────┬───────┤
│编码│名称│单位│销售数│成本单│售出价│成本金│销售金│利润  │利润率│
│    │    │    │量    │价    │      │额    │额    │      │      │
├────┼───┼────┼──────┼──────┼──────┼──────┼──────┼──────┼───────┤
│    │    │    │      │      │      │      │      │      │      │
└────┴───┴────┴──────┴──────┴──────┴──────┴──────┴──────┴───────┘
```

### 10.3 库存预警报表

```sql
-- 库存预警（低于安全库存的物品）
SELECT 
    i.code, i.name, i.unit, i.safety_stock,
    IFNULL(SUM(inv.quantity), 0) as current_stock,
    (i.safety_stock - IFNULL(SUM(inv.quantity), 0)) as shortage
FROM items i
LEFT JOIN inventory inv ON i.id = inv.item_id
WHERE i.status = 1
GROUP BY i.id
HAVING current_stock < i.safety_stock
ORDER BY shortage DESC;
```

---

## 十一、部署与运维方案

### 11.1 Docker Compose 部署

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    container_name: wms-mysql
    environment:
      MYSQL_ROOT_PASSWORD: your_password
      MYSQL_DATABASE: wms
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./sql/init.sql:/docker-entrypoint-initdb.d/init.sql
    restart: always

  redis:
    image: redis:7-alpine
    container_name: wms-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    restart: always

  wms-server:
    build:
      context: ./wms-server
      dockerfile: Dockerfile
    container_name: wms-server
    ports:
      - "8088:8088"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/wms?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
      - SPRING_DATASOURCE_USERNAME=root
      - SPRING_DATASOURCE_PASSWORD=your_password
      - SPRING_DATA_REDIS_HOST=redis
    depends_on:
      - mysql
      - redis
    restart: always

  minio:
    image: minio/minio
    container_name: wms-minio
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: admin
      MINIO_ROOT_PASSWORD: your_password
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data
    restart: always

  wms-web:
    build:
      context: ./wms-web
      dockerfile: Dockerfile
    container_name: wms-web
    ports:
      - "3000:80"
    depends_on:
      - wms-server
    restart: always

  nginx:
    image: nginx:alpine
    container_name: wms-nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./ssl:/etc/nginx/ssl
    depends_on:
      - wms-server
      - wms-web
    restart: always

volumes:
  mysql_data:
  redis_data:
  minio_data:
```

### 11.2 系统配置清单

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| 服务器 | 4核8G CentOS 7+ | - |
| MySQL | 8.0，utf8mb4 | 端口3306 |
| Redis | 7.x | 端口6379 |
| 小程序AppID | 微信小程序ID | 需申请 |
| SSL证书 | HTTPS必需 | 需申请 |
| 域名 | API域名+静态资源域名 | 需备案 |

### 11.3 初始化步骤

```bash
# 1. 克隆项目
git clone xxx/wms.git

# 2. 修改配置
cp config/config.example.yaml config/config.yaml
# 编辑数据库密码、Redis密码等

# 3. 初始化数据库
mysql -u root -p < sql/init.sql

# 4. 启动服务
docker-compose up -d

# 5. 检查服务
curl http://localhost:8088/api/health
```

---

## 附录：开源项目参考

| 项目 | 地址 | 参考价值 |
|------|------|---------|
| **Odoo** | github.com/odoo/odoo | 进销存单据流转模型、成本核算 |
| **GreaterWMS** | github.com/GreaterWMS | 仓库作业流程、PDA扫码交互 |
| **若依(RuoYi)** | gitee.com/y_project/RuoYi | 权限管理、代码生成器 |
| **pigx** | gitee.com/log4j/pig | 微服务架构参考 |

---

