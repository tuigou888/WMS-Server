package com.wms.service;
import java.math.*;
public final class InventoryCostCalculator {
 private static final int COST_SCALE=4; private static final RoundingMode ROUNDING=RoundingMode.HALF_UP;
 private InventoryCostCalculator(){}
 public static BigDecimal amount(BigDecimal quantity, BigDecimal unitPrice){return quantity.multiply(unitPrice).setScale(2, ROUNDING);}
 public static BigDecimal transferAverageCost(BigDecimal oldQuantity,BigDecimal oldAmount,BigDecimal incomingQuantity,BigDecimal incomingUnitCost){return oldQuantity.signum()==0?incomingUnitCost.setScale(COST_SCALE,ROUNDING):averageCost(oldQuantity,oldAmount,incomingQuantity,amount(incomingQuantity,incomingUnitCost));}
 public static BigDecimal averageCost(BigDecimal oldQuantity,BigDecimal oldAmount,BigDecimal incomingQuantity,BigDecimal incomingAmount){BigDecimal totalQuantity=oldQuantity.add(incomingQuantity); return totalQuantity.signum()==0?BigDecimal.ZERO.setScale(COST_SCALE):oldAmount.add(incomingAmount).divide(totalQuantity,COST_SCALE,ROUNDING);}
}
