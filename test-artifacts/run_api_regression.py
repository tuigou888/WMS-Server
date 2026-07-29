#!/usr/bin/env python3
import io
import json
import sys
import time
from datetime import datetime
from pathlib import Path

import requests
from openpyxl import Workbook

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:18088/api/v1"
OUT = Path(__file__).with_name("api-results-current.json")
TIMEOUT = 15
results = []
admin = requests.Session()
operator = requests.Session()


def record(module, name, fn):
    started = time.time()
    try:
        detail = fn() or "符合预期"
        results.append({"module": module, "name": name, "ok": True, "detail": str(detail), "durationMs": round((time.time()-started)*1000)})
        print(f"PASS [{module}] {name}: {detail}")
    except Exception as exc:
        results.append({"module": module, "name": name, "ok": False, "detail": str(exc), "durationMs": round((time.time()-started)*1000)})
        print(f"FAIL [{module}] {name}: {exc}")


def call(session, method, path, *, expected=200, json_body=None, files=None, params=None, raw=False):
    response = session.request(method, BASE + path, json=json_body, files=files, params=params, timeout=TIMEOUT)
    if response.status_code != expected:
        raise AssertionError(f"{method} {path}: expected HTTP {expected}, got {response.status_code}: {response.text[:500]}")
    if raw:
        return response
    try:
        body = response.json()
    except Exception:
        raise AssertionError(f"{method} {path}: expected JSON, got {response.text[:300]}")
    if expected == 200 and body.get("code") != 200:
        raise AssertionError(f"{method} {path}: API code={body.get('code')}, body={body}")
    return body.get("data")


def expect_error(session, method, path, status, json_body=None, files=None, contains=None):
    response = session.request(method, BASE + path, json=json_body, files=files, timeout=TIMEOUT)
    if response.status_code != status:
        raise AssertionError(f"expected HTTP {status}, got {response.status_code}: {response.text[:500]}")
    if contains and contains not in response.text:
        raise AssertionError(f"response does not contain {contains!r}: {response.text[:500]}")
    return f"HTTP {status}"


def eq(actual, expected, label="value"):
    if actual != expected:
        raise AssertionError(f"{label}: expected {expected!r}, got {actual!r}")


def dec(value):
    return round(float(value), 4)


state = {}

record("基础可用性", "健康检查", lambda: (lambda d: f"status={d['status']}, service={d['service']}")(call(requests, "GET", "/health")))
record("认证鉴权", "未登录访问受保护接口", lambda: expect_error(requests, "GET", "/inventory", 401))
record("认证鉴权", "错误密码拒绝登录", lambda: expect_error(requests, "POST", "/auth/login", 400, {"username":"admin","password":"bad"}, contains="用户名或密码错误"))


def login_admin():
    data = call(admin, "POST", "/auth/login", json_body={"username":"admin","password":"admin123"})
    admin.headers["Authorization"] = "Bearer " + data["token"]
    eq(data["role"], "ADMIN", "role")
    state["admin_token"] = data["token"]
    return f"role={data['role']}, expiresIn={data['expiresIn']}"
record("认证鉴权", "管理员登录", login_admin)


def login_operator():
    data = call(operator, "POST", "/auth/login", json_body={"username":"operator","password":"operator123"})
    operator.headers["Authorization"] = "Bearer " + data["token"]
    eq(data["role"], "WAREHOUSE", "role")
    return f"role={data['role']}, expiresIn={data['expiresIn']}"
record("认证鉴权", "操作员登录", login_operator)
record("认证鉴权", "查询当前用户", lambda: (lambda d: (eq(d["username"],"admin","username"), f"username={d['username']}, role={d['role']}")[1])(call(admin,"GET","/auth/me")))
record("权限控制", "操作员不可查询用户列表", lambda: expect_error(operator,"GET","/auth/users",403))
record("权限控制", "操作员不可新增仓库", lambda: expect_error(operator,"POST","/warehouses",403,{"code":"NO-AUTH","name":"无权限仓","status":True}))


def demo_data():
    items = call(admin,"GET","/items",params={"pageSize":100})
    wh = call(admin,"GET","/warehouses")
    inv = call(admin,"GET","/inventory")
    if len(items["records"]) < 3 or len(wh) < 1 or len(inv) < 3:
        raise AssertionError(f"demo data incomplete: items={len(items['records'])}, warehouses={len(wh)}, inventory={len(inv)}")
    state["main_wh"] = wh[0]["id"]
    state["category_id"] = call(admin,"GET","/items/categories")[0]["id"]
    return f"物品={len(items['records'])}, 仓库={len(wh)}, 库存行={len(inv)}"
record("基础资料", "演示数据初始化", demo_data)
record("基础资料", "物品分类查询", lambda: f"分类数={len(call(admin,'GET','/items/categories'))}")

suffix = datetime.now().strftime("%H%M%S")
item_code = "AUTO-" + suffix
item_payload = {"code":item_code,"name":"自动化测试物品","categoryId":None,"unit":"个","specs":"T-1","brand":"Test","model":"M1","barcode":"BC"+suffix,"safetyStock":5,"minStock":2,"maxStock":100,"status":True,"remark":"API regression","defaultWarehouseId":None}

def create_item():
    data=call(admin,"POST","/items",json_body=item_payload); state["item_id"]=data["id"]; return f"id={data['id']}, code={data['code']}"
record("物品档案", "新增物品", create_item)
record("物品档案", "重复物品编码校验", lambda: expect_error(admin,"POST","/items",400,item_payload,contains="物品编码已存在"))
record("物品档案", "按 ID 查询物品", lambda: (eq(call(admin,"GET",f"/items/{state['item_id']}")["code"],item_code,"code"),item_code)[1])
record("物品档案", "按编码查询物品", lambda: (eq(call(admin,"GET",f"/items/code/{item_code}")["id"],state["item_id"],"id"),item_code)[1])
record("物品档案", "物品关键词与分页查询", lambda: (lambda d: (eq(len(d["records"]),1,"records"), f"total={d['total']}, pageSize={d['pageSize']}")[1])(call(admin,"GET","/items",params={"keyword":item_code,"page":1,"pageSize":2})))

def update_item():
    p=dict(item_payload); p.update(name="自动化测试物品-已更新",categoryId=state["category_id"],defaultWarehouseId=state["main_wh"])
    d=call(admin,"PUT",f"/items/{state['item_id']}",json_body=p); eq(d["name"],p["name"],"name"); return d["name"]
record("物品档案", "更新物品", update_item)
record("参数校验", "物品必填参数校验", lambda: expect_error(admin,"POST","/items",400,{"code":"","name":""}))

supplier_payload={"code":"SUP-"+suffix,"name":"自动化供应商","type":"SUPPLIER","contactName":"张三","phone":"13800000000","email":"sup@example.com","address":"上海","enabled":True,"remark":"test"}
customer_payload={"code":"CUS-"+suffix,"name":"自动化客户","type":"CUSTOMER","contactName":"李四","phone":"13900000000","email":"cus@example.com","address":"北京","enabled":True,"remark":"test"}
both_payload={"code":"BOTH-"+suffix,"name":"待删除往来单位","type":"BOTH","contactName":"临时","enabled":True}
def create_partner(payload,key):
    d=call(admin,"POST","/partners",json_body=payload); state[key]=d["id"]; return f"id={d['id']}"
record("供应商/客户", "新增供应商", lambda: create_partner(supplier_payload,"supplier_id"))
record("供应商/客户", "新增客户", lambda: create_partner(customer_payload,"customer_id"))
record("供应商/客户", "新增双类型往来单位", lambda: create_partner(both_payload,"both_id"))
record("供应商/客户", "重复往来单位编码校验", lambda: expect_error(admin,"POST","/partners",400,supplier_payload))
record("供应商/客户", "按类型筛选往来单位", lambda: (lambda xs: (True if any(x["id"]==state["supplier_id"] for x in xs) else (_ for _ in ()).throw(AssertionError("supplier missing")), f"供应商结果={len(xs)}")[1])(call(admin,"GET","/partners",params={"type":"SUPPLIER"})))
def update_partner():
    p=dict(supplier_payload); p["contactName"]="王五"; d=call(admin,"PUT",f"/partners/{state['supplier_id']}",json_body=p); eq(d["contactName"],"王五","contactName"); return "联系人=王五"
record("供应商/客户", "更新往来单位", update_partner)
record("供应商/客户", "删除往来单位", lambda: (call(admin,"DELETE",f"/partners/{state['both_id']}"),"删除成功")[1])

wh_payload={"code":"WH-AUTO-"+suffix,"name":"自动化目标仓","status":True}
def create_wh():
    d=call(admin,"POST","/warehouses",json_body=wh_payload); state["target_wh"]=d["id"]; return f"id={d['id']}"
record("仓库管理", "管理员新增仓库", create_wh)
record("仓库管理", "重复仓库编码校验", lambda: expect_error(admin,"POST","/warehouses",400,wh_payload))
def update_wh():
    p=dict(wh_payload);p["name"]="自动化目标仓-已更新";d=call(admin,"PUT",f"/warehouses/{state['target_wh']}",json_body=p);eq(d["name"],p["name"],"name");return d["name"]
record("仓库管理", "更新仓库", update_wh)
record("仓库管理", "查询启用仓库", lambda: f"仓库数={len(call(admin,'GET','/warehouses'))}")

stock_in={"itemCode":item_code,"quantity":10,"unitCost":12.5,"warehouseId":None,"locationCode":"A-TEST-01","batchNo":"B1","remark":"scan in"}
def scan_in():
    stock_in["warehouseId"]=state["main_wh"]
    d=call(operator,"POST","/stock/in/scan",json_body=stock_in);eq(dec(d["newStockQuantity"]),10.0,"stock");return f"库存={d['newStockQuantity']}, 均价={d['newAvgCost']}"
record("扫码出入库", "扫码入库", scan_in)
record("参数校验", "入库数量参数校验", lambda: expect_error(operator,"POST","/stock/in/scan",400,{**stock_in,"quantity":0}))
def scan_out():
    d=call(operator,"POST","/stock/out/scan",json_body={"itemCode":item_code,"quantity":3,"salePrice":20,"warehouseId":state["main_wh"],"locationCode":"A-TEST-01","batchNo":"B1","remark":"scan out"});eq(dec(d["newStockQuantity"]),7.0,"stock");eq(dec(d["profit"]),22.5,"profit");return f"库存={d['newStockQuantity']}, 利润={d['profit']}"
record("扫码出入库", "扫码出库及利润计算", scan_out)
record("库存校验", "库存不足阻止出库", lambda: expect_error(operator,"POST","/stock/out/scan",400,{"itemCode":item_code,"quantity":999,"salePrice":20,"warehouseId":state["main_wh"],"locationCode":"A-TEST-01","batchNo":"B1"},contains="库存不足"))
record("库位管理", "查询全部库位", lambda: f"库位数={len(call(admin,'GET','/locations'))}")
record("库位管理", "按仓库查询库位", lambda: (lambda xs: (True if any(x["code"]=="A-TEST-01" for x in xs) else (_ for _ in ()).throw(AssertionError("location missing")), f"库位数={len(xs)}")[1])(call(admin,"GET","/locations",params={"warehouseId":state["main_wh"]})))
record("库位管理", "不存在仓库的库位查询", lambda: expect_error(admin,"GET","/locations?warehouseId=999999",400))
record("库存管理", "库存列表与分页", lambda: (lambda xs: f"pageSize=2 返回={len(xs)}" if len(xs)==2 else (_ for _ in ()).throw(AssertionError(f"expected 2 got {len(xs)}")))(call(admin,"GET","/inventory",params={"page":1,"pageSize":2})))
record("库存管理", "按物品查询库存", lambda: (lambda xs: (True if any(x["itemCode"]==item_code for x in xs) else (_ for _ in ()).throw(AssertionError("inventory missing")), f"库存行={len(xs)}")[1])(call(admin,"GET",f"/inventory/{state['item_id']}")))
record("库存管理", "库存流水查询", lambda: f"流水数={len(call(admin,'GET','/inventory/transactions',params={'limit':20}))}")
record("库存管理", "库存模块仓库列表", lambda: f"仓库数={len(call(admin,'GET','/inventory/warehouses'))}")

in_doc={"type":"IN","partnerId":None,"warehouseId":None,"businessDate":None,"remark":"doc in","lines":[{"itemCode":item_code,"locationCode":"A-TEST-01","quantity":5,"unitPrice":15,"batchNo":"B1","remark":"line"}]}
def create_in_doc():
    in_doc.update(partnerId=state["supplier_id"],warehouseId=state["main_wh"])
    d=call(operator,"POST","/documents",json_body=in_doc);state["in_doc"]=d["id"];eq(d["status"],"DRAFT","status");return f"{d['documentNo']} {d['status']}"
record("入出库单", "操作员创建入库草稿", create_in_doc)
record("权限控制", "操作员不可审核单据", lambda: expect_error(operator,"POST",f"/documents/{state['in_doc']}/review",403,{"action":"APPROVE","remark":""}))
def approve_in_doc():
    d=call(admin,"POST",f"/documents/{state['in_doc']}/review",json_body={"action":"APPROVE","remark":"ok"});eq(d["status"],"APPROVED","status");return d["status"]
record("入出库单", "管理员审核入库单", approve_in_doc)
def complete_in_doc():
    d=call(operator,"POST",f"/documents/{state['in_doc']}/complete");eq(d["status"],"COMPLETED","status")
    inv=call(admin,"GET",f"/inventory/{state['item_id']}");src=next(x for x in inv if x["warehouseId"]==state["main_wh"] and x["locationCode"]=="A-TEST-01")
    eq(dec(src["quantity"]),12.0,"quantity");eq(dec(src["avgCost"]),13.5417,"avgCost");state["source_avg"]=dec(src["avgCost"]);return f"状态={d['status']}, 库存={src['quantity']}, 均价={src['avgCost']}"
record("入出库单", "执行入库单并更新移动平均成本", complete_in_doc)
record("状态机校验", "已完成单据不可再次执行", lambda: expect_error(operator,"POST",f"/documents/{state['in_doc']}/complete",400))
record("入出库单", "单据列表与详情", lambda: (lambda d: (eq(d["id"],state["in_doc"],"id"),f"明细行={len(d['lines'])}")[1])(call(admin,"GET",f"/documents/{state['in_doc']}")))

def out_doc_flow():
    p={"type":"OUT","partnerId":state["customer_id"],"warehouseId":state["main_wh"],"remark":"doc out","lines":[{"itemCode":item_code,"locationCode":"A-TEST-01","quantity":2,"unitPrice":25,"batchNo":"B1"}]}
    d=call(operator,"POST","/documents",json_body=p);call(admin,"POST",f"/documents/{d['id']}/review",json_body={"action":"APPROVE"});done=call(operator,"POST",f"/documents/{d['id']}/complete");eq(done["status"],"COMPLETED","status");return f"{done['documentNo']} {done['status']}"
record("入出库单", "出库单完整流程", out_doc_flow)
def cancel_doc():
    d=call(operator,"POST","/documents",json_body=in_doc);done=call(operator,"POST",f"/documents/{d['id']}/cancel");eq(done["status"],"CANCELLED","status");return done["status"]
record("入出库单", "取消草稿单", cancel_doc)
def reject_doc():
    d=call(operator,"POST","/documents",json_body=in_doc);done=call(admin,"POST",f"/documents/{d['id']}/review",json_body={"action":"REJECT","remark":"test reject"});eq(done["status"],"REJECTED","status");return done["status"]
record("入出库单", "驳回草稿单", reject_doc)
record("业务规则", "入库单供应商类型校验", lambda: expect_error(operator,"POST","/documents",400,{**in_doc,"partnerId":state["customer_id"]},contains="供应商"))


def transfer_flow():
    p={"sourceWarehouseId":state["main_wh"],"targetWarehouseId":state["target_wh"],"remark":"transfer","lines":[{"itemCode":item_code,"sourceLocationCode":"A-TEST-01","targetLocationCode":"B-TEST-01","batchNo":"B1","quantity":4}]}
    d=call(operator,"POST","/transfers",json_body=p);state["transfer_id"]=d["id"]
    call(admin,"POST",f"/transfers/{d['id']}/review",json_body={"action":"APPROVE"});done=call(operator,"POST",f"/transfers/{d['id']}/complete");eq(done["status"],"COMPLETED","status")
    inv=call(admin,"GET",f"/inventory/{state['item_id']}");target=next(x for x in inv if x["warehouseId"]==state["target_wh"]);eq(dec(target["quantity"]),4.0,"target quantity");eq(dec(target["avgCost"]),state["source_avg"],"target avg cost");return f"状态={done['status']}, 目标库存={target['quantity']}, 目标均价={target['avgCost']}"
record("库存调拨", "库存调拨完整流程及成本继承", transfer_flow)
record("库存调拨", "调拨列表查询", lambda: f"调拨单数={len(call(admin,'GET','/transfers'))}")
record("业务规则", "禁止同仓调拨", lambda: expect_error(operator,"POST","/transfers",400,{"sourceWarehouseId":state["main_wh"],"targetWarehouseId":state["main_wh"],"lines":[{"itemCode":item_code,"sourceLocationCode":"A-TEST-01","targetLocationCode":"B","batchNo":"B1","quantity":1}]}))
def reject_transfer():
    p={"sourceWarehouseId":state["main_wh"],"targetWarehouseId":state["target_wh"],"lines":[{"itemCode":item_code,"sourceLocationCode":"A-TEST-01","targetLocationCode":"B-TEST-02","batchNo":"B1","quantity":1}]}
    d=call(operator,"POST","/transfers",json_body=p);r=call(admin,"POST",f"/transfers/{d['id']}/review",json_body={"action":"REJECT"});eq(r["status"],"REJECTED","status");return r["status"]
record("库存调拨", "驳回调拨单", reject_transfer)


def stocktake_flow():
    d=call(operator,"POST","/stocktakes",json_body={"warehouseId":state["target_wh"],"remark":"stocktake","lines":[]});state["stocktake_id"]=d["id"]
    lines=[{"itemCode":x["itemCode"],"locationCode":x["locationCode"],"batchNo":x.get("batchNo"),"actualQuantity":5 if x["itemCode"]==item_code else x["bookQuantity"]} for x in d["lines"]]
    counted=call(operator,"POST",f"/stocktakes/{d['id']}/count",json_body={"warehouseId":state["target_wh"],"lines":lines})
    call(admin,"POST",f"/stocktakes/{d['id']}/review",json_body={"action":"APPROVE"});done=call(operator,"POST",f"/stocktakes/{d['id']}/complete");eq(done["status"],"COMPLETED","status")
    inv=call(admin,"GET",f"/inventory/{state['item_id']}");target=next(x for x in inv if x["warehouseId"]==state["target_wh"]);eq(dec(target["quantity"]),5.0,"quantity");return f"状态={done['status']}, 调整后库存={target['quantity']}, 盘点行={len(counted['lines'])}"
record("库存盘点", "库存盘点完整流程及差异调整", stocktake_flow)
record("库存盘点", "盘点列表查询", lambda: f"盘点单数={len(call(admin,'GET','/stocktakes'))}")
def reject_stocktake():
    d=call(operator,"POST","/stocktakes",json_body={"warehouseId":state["target_wh"],"remark":"reject","lines":[]});r=call(admin,"POST",f"/stocktakes/{d['id']}/review",json_body={"action":"REJECT"});eq(r["status"],"REJECTED","status");return r["status"]
record("库存盘点", "驳回盘点单", reject_stocktake)

record("报表中心", "仪表盘数据", lambda: (lambda d: f"stockItemCount={d['stockItemCount']}, totalQuantity={d['totalQuantity']}, alerts={d['alertCount']}")(call(admin,"GET","/reports/dashboard")))
record("报表中心", "库存预警报表", lambda: f"预警条目={len(call(admin,'GET','/reports/stock-alert'))}")
record("报表中心", "利润报表", lambda: f"利润流水={len(call(admin,'GET','/reports/profit'))}")
record("报表中心", "异常报表", lambda: f"异常条目={len(call(admin,'GET','/reports/anomalies'))}")

def qr_data():
    d=call(admin,"GET",f"/qrcodes/items/{item_code}");eq(d["content"],item_code,"content");
    if not d["image"].startswith("data:image/png;base64,"): raise AssertionError("invalid data URL")
    return f"dataURL长度={len(d['image'])}"
record("二维码/Excel", "二维码数据生成", qr_data)
def qr_png():
    r=call(admin,"GET",f"/qrcodes/items/{item_code}/png",raw=True)
    if r.headers.get("Content-Type")!="image/png" or not r.content.startswith(b"\x89PNG"): raise AssertionError("invalid PNG")
    return f"PNG字节数={len(r.content)}"
record("二维码/Excel", "二维码 PNG 下载", qr_png)

def excel_export_import():
    r=call(admin,"GET","/excel/items/export",raw=True)
    if len(r.content)<1000: raise AssertionError("export too small")
    imported=call(admin,"POST","/excel/items/import",files={"file":("items.xlsx",r.content,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")})
    return f"导出={len(r.content)}字节, 导入 updated={imported['updated']}, created={imported['created']}"
record("二维码/Excel", "Excel 导出并回导", excel_export_import)

def excel_create():
    wb=Workbook();ws=wb.active;ws.append(["物品编码","物品名称","分类","单位","规格型号","安全库存","最小库存","最大库存","备注"]);ws.append(["XLS-"+suffix,"Excel新增物品","Excel分类","箱","XL",1,0,10,"import"]);buf=io.BytesIO();wb.save(buf)
    d=call(admin,"POST","/excel/items/import",files={"file":("new.xlsx",buf.getvalue(),"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")});eq(d["created"],1,"created");return f"created={d['created']}"
record("二维码/Excel", "Excel 导入新增物品", excel_create)

def ocr():
    d=call(admin,"POST","/ocr/recognize",files={"file":("receipt.png",b"not-a-real-image-but-nonempty","image/png")});
    if not d["success"] or d["totalLines"] < 1: raise AssertionError(d)
    return f"source={d['source']}, lines={d['totalLines']}"
record("OCR", "OCR 模拟识别", ocr)
record("OCR", "OCR 空文件校验", lambda: expect_error(admin,"POST","/ocr/recognize",400,files={"file":("empty.png",b"","image/png")}))

new_user={"username":"user"+suffix,"password":"test123","displayName":"自动化用户","role":"WAREHOUSE","enabled":True}
def user_flow():
    d=call(admin,"POST","/auth/users",json_body=new_user);state["user_id"]=d["id"]
    s=requests.Session();login=call(s,"POST","/auth/login",json_body={"username":new_user["username"],"password":new_user["password"]});eq(login["role"],"WAREHOUSE","role")
    updated=call(admin,"PUT",f"/auth/users/{d['id']}",json_body={**new_user,"password":"","displayName":"自动化用户-停用","enabled":False});eq(updated["enabled"],False,"enabled")
    expect_error(requests,"POST","/auth/login",400,{"username":new_user["username"],"password":new_user["password"]})
    return "创建、登录、更新、停用正常"
record("用户与权限", "用户创建、登录、更新及停用", user_flow)
record("用户与权限", "用户列表查询", lambda: f"用户数={len(call(admin,'GET','/auth/users'))}")

# 删除没有库存引用的临时物品，验证删除接口。
temp_payload={**item_payload,"code":"DEL-"+suffix,"name":"待删除物品","categoryId":None,"defaultWarehouseId":None}
def delete_item():
    d=call(admin,"POST","/items",json_body=temp_payload);call(admin,"DELETE",f"/items/{d['id']}");expect_error(admin,"GET",f"/items/{d['id']}",400);return "删除后不可查询"
record("物品档案", "删除无库存物品", delete_item)


def logout_test():
    token=state["admin_token"]
    call(admin,"POST","/auth/logout")
    r=requests.get(BASE+"/auth/me",headers={"Authorization":"Bearer "+token},timeout=TIMEOUT)
    eq(r.status_code,401,"HTTP status")
    return "Token 失效后 HTTP 401"
record("认证鉴权", "退出登录使 Token 失效", logout_test)

passed=sum(1 for r in results if r["ok"])
failed=len(results)-passed
report={"base":BASE,"timestamp":datetime.now().astimezone().isoformat(),"summary":{"total":len(results),"passed":passed,"failed":failed},"results":results}
OUT.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
print(f"\nSUMMARY total={len(results)} passed={passed} failed={failed}")
print(f"RESULT_FILE {OUT}")
sys.exit(1 if failed else 0)
