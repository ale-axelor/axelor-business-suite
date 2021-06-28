package com.axelor.apps.supplychain.service.stockreservation;

import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.service.StockLocationLineService;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;

/**
 * Low level operation to update reservation quantities in stock move line.
 *
 * <p>To use reservation feature from other services, call {@link StockMoveLineReservationService}
 * instead.
 */
public class StockMoveLineUpdateReservedQtyServiceImpl
    implements StockMoveLineUpdateReservedQtyService {

  protected StockLocationLineService stockLocationLineService;
  protected UnitConversionService unitConversionService;
  protected AppSupplychainService appSupplychainService;

  @Inject
  public StockMoveLineUpdateReservedQtyServiceImpl(
    StockLocationLineService stockLocationLineService,
    UnitConversionService unitConversionService,
    AppSupplychainService appSupplychainService) {
    this.stockLocationLineService = stockLocationLineService;
    this.unitConversionService = unitConversionService;
    this.appSupplychainService = appSupplychainService;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateRequestedReservedQty(StockMoveLine stockMoveLine, BigDecimal newReservedQty)
      throws AxelorException {
    StockLocationLine stockLocationLine =
        stockLocationLineService.getOrCreateStockLocationLine(
            stockMoveLine.getStockMove().getFromStockLocation(), stockMoveLine.getProduct());
    updateRequestedReservedQty(stockLocationLine, stockMoveLine, newReservedQty);
  }

  protected void updateRequestedReservedQty(
      StockLocationLine stockLocationLine, StockMoveLine stockMoveLine, BigDecimal newReservedQty)
      throws AxelorException {

    BigDecimal diffReservedQuantity =
        newReservedQty.subtract(stockMoveLine.getRequestedReservedQty());
    BigDecimal diffReservedQuantityLocation =
        unitConversionService.convertManagingNullUnit(
            stockMoveLine.getUnit(),
            stockLocationLine.getUnit(),
            diffReservedQuantity,
            stockMoveLine.getProduct());
    stockLocationLine.setRequestedReservedQty(
        stockLocationLine.getRequestedReservedQty().add(diffReservedQuantityLocation));

    stockMoveLine.setRequestedReservedQty(newReservedQty);
  }

  @Override
  public void checkAvailabilityRequest(
      StockMoveLine stockMoveLine, BigDecimal qty, boolean isRequested) throws AxelorException {
    BigDecimal stockMoveLineQty =
        isRequested ? stockMoveLine.getRequestedReservedQty() : stockMoveLine.getReservedQty();
    if (stockMoveLine.getStockMove().getAvailabilityRequest()
        && stockMoveLineQty.compareTo(qty) > 0) {
      throw new AxelorException(
          stockMoveLine.getStockMove(),
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_AVAILABILITY_REQUEST));
    }
  }

  @Override
  public void checkBeforeUpdatingQties(StockMoveLine stockMoveLine, BigDecimal qty)
      throws AxelorException {
    if (stockMoveLine == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_NO_STOCK_MOVE));
    }
    if (qty.signum() < 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_RESERVATION_QTY_NEGATIVE));
    }
  }
}
