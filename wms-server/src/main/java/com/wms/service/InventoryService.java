package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.dto.*;
import com.wms.model.entity.*;
import com.wms.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*; import java.time.*; import java.util.*;

@Service
public class InventoryService {
 private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
  private final ItemRepository items; private final WarehouseRepository warehouses; private final LocationRepository locations; private final InventoryRepository inventories; private final InventoryTransactionRepository transactions; private final DocumentNumberService numbers;
  public InventoryService(ItemRepository items,WarehouseRepository warehouses,LocationRepository locations,InventoryRepository inventories,InventoryTransactionRepository transactions,DocumentNumberService numbers){this.items=items;this.warehouses=warehouses;this.locations=locations;this.inventories=inventories;this.transactions=transactions;this.numbers=numbers;}

 @Transactional public Map<String,Object> stockIn(StockInRequest request){return stockIn(request,null,TransactionType.IN);} @Transactional public Map<String,Object> stockIn(StockInRequest request,String referenceNo,String type){
  Item item=item(request.itemCode()); Warehouse warehouse=warehouse(request.warehouseId()); Location location=location(warehouse,request.locationCode(),true); String batchNo=request.batchNo()==null||request.batchNo().isBlank()?null:request.batchNo(); Inventory inventory=inventory(item,warehouse,location,batchNo,true);
  BigDecimal amount=InventoryCostCalculator.amount(request.quantity(),request.unitCost()); BigDecimal quantity=inventory.getQuantity().add(request.quantity()); BigDecimal total=inventory.getTotalAmount().add(amount); BigDecimal avg=InventoryCostCalculator.averageCost(inventory.getQuantity(),inventory.getTotalAmount(),request.quantity(),amount);
  inventory.setQuantity(quantity);inventory.setTotalAmount(total);inventory.setAvgCost(avg);inventory.setLastInCost(request.unitCost());inventories.save(inventory);
  String no=referenceNo==null?nextOrderNo("RK"):referenceNo; transaction(item,warehouse,location,type,no,request.remark(),request.quantity(),request.unitCost(),amount,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,quantity,total,avg);
  log.info("入库: item={}, warehouse={}, qty={}, cost={}, orderNo={}", request.itemCode(), request.warehouseId(), request.quantity(), request.unitCost(), no);
  return movementResult(no,item,request.quantity(),amount,avg,quantity,total,Map.of("unitCost",request.unitCost()));
 }
 @Transactional public Map<String,Object> stockOut(StockOutRequest request){return stockOut(request,null,TransactionType.OUT);}
 @Transactional public Map<String,Object> stockOut(StockOutRequest request,String referenceNo,String type){
  Item item=item(request.itemCode()); Warehouse warehouse=warehouse(request.warehouseId()); Location location=location(warehouse,request.locationCode(),false); String batchNo=request.batchNo()==null||request.batchNo().isBlank()?null:request.batchNo(); Inventory inventory=inventory(item,warehouse,location,batchNo,false);
  if(inventory.getQuantity().compareTo(request.quantity())<0) throw new BusinessException("库存不足：可用 "+inventory.getQuantity().stripTrailingZeros().toPlainString()); BigDecimal unit=inventory.getAvgCost();BigDecimal cost=InventoryCostCalculator.amount(request.quantity(),unit);BigDecimal sales=InventoryCostCalculator.amount(request.quantity(),request.salePrice());BigDecimal profit=TransactionType.REVERSE.equals(type)?BigDecimal.ZERO.setScale(2):sales.subtract(cost).setScale(2,RoundingMode.HALF_UP);BigDecimal quantity=inventory.getQuantity().subtract(request.quantity());BigDecimal total=inventory.getTotalAmount().subtract(cost).max(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP);BigDecimal avg=quantity.signum()==0?BigDecimal.ZERO.setScale(4):unit;
  inventory.setQuantity(quantity);inventory.setTotalAmount(total);inventory.setAvgCost(avg);inventories.save(inventory);String no=referenceNo==null?nextOrderNo("CK"):referenceNo;transaction(item,warehouse,location,type,no,request.remark(),request.quantity().negate(),unit,cost,request.salePrice(),sales,profit,quantity,total,avg);
  log.info("出库: item={}, warehouse={}, qty={}, salePrice={}, profit={}, orderNo={}", request.itemCode(), request.warehouseId(), request.quantity(), request.salePrice(), profit, no);
  return movementResult(no,item,request.quantity(),cost,avg,quantity,total,Map.of("costUnit",unit,"salePrice",request.salePrice(),"saleAmount",sales,"profit",profit));
 }
 @Transactional public void transfer(String referenceNo,String itemCode,Long sourceWarehouseId,String sourceLocationCode,Long targetWarehouseId,String targetLocationCode,String batchNo,BigDecimal quantity,String remark){
  Item item=item(itemCode); Warehouse source=warehouse(sourceWarehouseId);Warehouse target=warehouse(targetWarehouseId);Location from=location(source,sourceLocationCode,false);Location to=location(target,targetLocationCode,true);String normalizedBatch=normalizeBatch(batchNo);Inventory sourceInv=inventory(item,source,from,normalizedBatch,false);if(sourceInv.getQuantity().compareTo(quantity)<0)throw new BusinessException("调拨库存不足："+itemCode);
  BigDecimal unit=sourceInv.getAvgCost(),amount=InventoryCostCalculator.amount(quantity,unit),sourceQty=sourceInv.getQuantity().subtract(quantity),sourceTotal=sourceInv.getTotalAmount().subtract(amount).max(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP),sourceAvg=sourceQty.signum()==0?BigDecimal.ZERO.setScale(4):unit;
  sourceInv.setQuantity(sourceQty);sourceInv.setTotalAmount(sourceTotal);sourceInv.setAvgCost(sourceAvg);inventories.save(sourceInv);transaction(item,source,from,TransactionType.TRANSFER_OUT,referenceNo,remark,quantity.negate(),unit,amount,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,sourceQty,sourceTotal,sourceAvg);
  Inventory targetInv=inventory(item,target,to,normalizedBatch,true);BigDecimal targetQty=targetInv.getQuantity().add(quantity),targetTotal=targetInv.getTotalAmount().add(amount),targetAvg=InventoryCostCalculator.transferAverageCost(targetInv.getQuantity(),targetInv.getTotalAmount(),quantity,unit);targetInv.setQuantity(targetQty);targetInv.setTotalAmount(targetTotal);targetInv.setAvgCost(targetAvg);targetInv.setLastInCost(unit);inventories.save(targetInv);transaction(item,target,to,TransactionType.TRANSFER_IN,referenceNo,remark,quantity,unit,amount,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,targetQty,targetTotal,targetAvg);
  log.info("调拨: item={}, fromWarehouse={}, toWarehouse={}, qty={}, orderNo={}", itemCode, sourceWarehouseId, targetWarehouseId, quantity, referenceNo);
 }
 @Transactional public void adjust(String referenceNo,String itemCode,Long warehouseId,String locationCode,String batchNo,BigDecimal actualQuantity,String remark){
  Item item=item(itemCode); Warehouse warehouse=warehouse(warehouseId); Location location=location(warehouse,locationCode,true); Inventory inv=inventory(item,warehouse,location,normalizeBatch(batchNo),true); BigDecimal delta=actualQuantity.subtract(inv.getQuantity());if(delta.signum()==0)return; if(delta.signum()<0&&inv.getQuantity().compareTo(delta.abs())<0)throw new BusinessException("盘点调整不能使库存为负");BigDecimal unit=inv.getAvgCost(),amount=InventoryCostCalculator.amount(delta.abs(),unit),qty=actualQuantity,total=delta.signum()>0?inv.getTotalAmount().add(amount):inv.getTotalAmount().subtract(amount).max(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP),avg=qty.signum()==0?BigDecimal.ZERO.setScale(4):unit;inv.setQuantity(qty);inv.setTotalAmount(total);inv.setAvgCost(avg);inventories.save(inv);transaction(item,warehouse,location,delta.signum()>0?TransactionType.ADJUST_IN:TransactionType.ADJUST_OUT,referenceNo,remark,delta,unit,amount,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,qty,total,avg);
  log.info("盘点调整: item={}, warehouse={}, delta={}, actual={}, orderNo={}", itemCode, warehouseId, delta, actualQuantity, referenceNo);
 }
 private String normalizeBatch(String batchNo){return batchNo==null||batchNo.isBlank()?null:batchNo.trim();}
 private Item item(String code){return items.findByCode(code).orElseThrow(()->new BusinessException("未找到物品编码："+code));} private Warehouse warehouse(Long id){Warehouse wh=warehouses.findById(id).orElseThrow(()->new BusinessException("仓库不存在"));if(!Boolean.TRUE.equals(wh.getStatus()))throw new BusinessException("仓库已禁用");return wh;}
  private Location location(Warehouse warehouse,String code,boolean create){Optional<Location> found=locations.findByWarehouseIdAndCode(warehouse.getId(),code);if(found.isPresent())return found.get();if(!create)throw new BusinessException("库位不存在："+code);locations.insertIfAbsent(warehouse.getId(),code);return locations.findByWarehouseIdAndCode(warehouse.getId(),code).orElseThrow();}
 private Inventory inventory(Item item,Warehouse warehouse,Location location,String batchNo,boolean create){return inventories.findForUpdate(item.getId(),warehouse.getId(),location.getId(),batchNo).orElseGet(()->{if(!create)throw new BusinessException("该库位没有库存");return new Inventory(item,warehouse,location,batchNo);});}
 private void transaction(Item item,Warehouse warehouse,Location location,String type,String ref,String remark,BigDecimal quantity,BigDecimal unit,BigDecimal cost,BigDecimal salePrice,BigDecimal saleAmount,BigDecimal profit,BigDecimal balanceQty,BigDecimal balanceAmount,BigDecimal avg){InventoryTransaction t=new InventoryTransaction();t.setItem(item);t.setWarehouse(warehouse);t.setLocation(location);t.setTransactionType(type);t.setReferenceNo(ref);t.setRemark(remark);t.setQuantity(quantity);t.setUnitCost(unit);t.setTotalCostAmount(cost);t.setSalePrice(salePrice);t.setSaleAmount(saleAmount);t.setProfit(profit);t.setBalanceQuantity(balanceQty);t.setBalanceAmount(balanceAmount);t.setAvgCostAfter(avg);transactions.save(t);}
 private Map<String,Object> movementResult(String no,Item item,BigDecimal qty,BigDecimal amount,BigDecimal avg,BigDecimal stock,BigDecimal stockAmount,Map<String,Object> extra){Map<String,Object> r=new LinkedHashMap<>();r.put("orderNo",no);r.put("itemCode",item.getCode());r.put("itemName",item.getName());r.put("quantity",qty);r.put("totalAmount",amount);r.put("newAvgCost",avg);r.put("newStockQuantity",stock);r.put("newStockAmount",stockAmount);r.putAll(extra);return r;}
  private String nextOrderNo(String prefix){return numbers.next(prefix);}
}
