package com.axelor.apps.supplychain.service.stockreservation;

import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.util.List;

public class StockLocationLineUpdateReservedQtyServiceImpl
    implements StockLocationLineUpdateReservedQtyService {

  protected ReservedQtyFetchService reservedQtyFetchService;
  protected UnitConversionService unitConversionService;

  @Inject
  public StockLocationLineUpdateReservedQtyServiceImpl(
      ReservedQtyFetchService reservedQtyFetchService,
      UnitConversionService unitConversionService) {
    this.reservedQtyFetchService = reservedQtyFetchService;
    this.unitConversionService = unitConversionService;
  }

  @Override
  public void updateRequestedReservedQty(StockLocationLine stockLocationLine)
      throws AxelorException {
    // compute from stock move lines
    List<StockMoveLine> stockMoveLineList =
        reservedQtyFetchService.fetchRelatedPlannedStockMoveLineList(stockLocationLine);

    BigDecimal requestedReservedQty = BigDecimal.ZERO;
    for (StockMoveLine stockMoveLine : stockMoveLineList) {
      requestedReservedQty =
          requestedReservedQty.add(
              unitConversionService.convertManagingNullUnit(
                  stockMoveLine.getUnit(),
                  stockLocationLine.getUnit(),
                  stockMoveLine.getRequestedReservedQty(),
                  stockLocationLine.getProduct()));
    }
    stockLocationLine.setRequestedReservedQty(requestedReservedQty);
  }

  @Override
  public void updateReservedQty(StockLocationLine stockLocationLine) throws AxelorException {
    // compute from stock move lines
    List<StockMoveLine> stockMoveLineList =
        reservedQtyFetchService.fetchRelatedPlannedStockMoveLineList(stockLocationLine);
    BigDecimal reservedQty = BigDecimal.ZERO;
    for (StockMoveLine stockMoveLine : stockMoveLineList) {
      reservedQty =
          reservedQty.add(
              unitConversionService.convertManagingNullUnit(
                  stockMoveLine.getUnit(),
                  stockLocationLine.getUnit(),
                  stockMoveLine.getReservedQty(),
                  stockLocationLine.getProduct()));
    }
    stockLocationLine.setReservedQty(reservedQty);
  }
}
