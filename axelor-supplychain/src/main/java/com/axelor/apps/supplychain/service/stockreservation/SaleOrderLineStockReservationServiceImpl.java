package com.axelor.apps.supplychain.service.stockreservation;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.sale.db.SaleOrderLine;
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

public class SaleOrderLineStockReservationServiceImpl
    implements SaleOrderLineStockReservationService {

  protected ReservedQtyFetchService reservedQtyFetchService;
  protected UnitConversionService unitConversionService;
  protected AppSupplychainService appSupplychainService;
  protected StockLocationLineService stockLocationLineService;
  protected SaleOrderLineUpdateReservedQtyService saleOrderLineUpdateReservedQtyService;

  @Inject
  public SaleOrderLineStockReservationServiceImpl(
    ReservedQtyFetchService reservedQtyFetchService,
    UnitConversionService unitConversionService,
    AppSupplychainService appSupplychainService,
    StockLocationLineService stockLocationLineService,
    SaleOrderLineUpdateReservedQtyService saleOrderLineUpdateReservedQtyService) {
    this.reservedQtyFetchService = reservedQtyFetchService;
    this.unitConversionService = unitConversionService;
    this.appSupplychainService = appSupplychainService;
    this.stockLocationLineService = stockLocationLineService;
    this.saleOrderLineUpdateReservedQtyService = saleOrderLineUpdateReservedQtyService;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void requestQty(SaleOrderLine saleOrderLine) throws AxelorException {
    if (saleOrderLine.getProduct() == null || !saleOrderLine.getProduct().getStockManaged()) {
      return;
    }
    saleOrderLine.setIsQtyRequested(true);
    if (saleOrderLine.getQty().signum() < 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_REQUEST_QTY_NEGATIVE));
    }
    StockMoveLine stockMoveLine = reservedQtyFetchService.fetchPlannedStockMoveLine(saleOrderLine);
    if (stockMoveLine != null) {
      stockMoveLine.setIsQtyRequested(true);
    }
    saleOrderLineUpdateReservedQtyService.updateRequestedReservedQty(
        saleOrderLine, saleOrderLine.getQty());
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void cancelReservation(SaleOrderLine saleOrderLine) throws AxelorException {
    if (saleOrderLine.getProduct() == null || !saleOrderLine.getProduct().getStockManaged()) {
      return;
    }
    saleOrderLine.setIsQtyRequested(false);
    StockMoveLine stockMoveLine = reservedQtyFetchService.fetchPlannedStockMoveLine(saleOrderLine);
    if (stockMoveLine != null) {
      stockMoveLine.setIsQtyRequested(false);
    }
    saleOrderLineUpdateReservedQtyService.updateRequestedReservedQty(
        saleOrderLine, saleOrderLine.getDeliveredQty());
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void allocate(SaleOrderLine saleOrderLine) throws AxelorException {
    if (saleOrderLine.getProduct() == null || !saleOrderLine.getProduct().getStockManaged()) {
      return;
    }
    // request the maximum quantity
    requestQty(saleOrderLine);
    StockMoveLine stockMoveLine = reservedQtyFetchService.fetchPlannedStockMoveLine(saleOrderLine);

    if (stockMoveLine == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_NO_STOCK_MOVE));
    }
    // search for the maximum quantity that can be allocated.
    StockLocationLine stockLocationLine =
        stockLocationLineService.getOrCreateStockLocationLine(
            stockMoveLine.getStockMove().getFromStockLocation(), stockMoveLine.getProduct());
    BigDecimal availableQtyToBeReserved =
        stockLocationLine.getCurrentQty().subtract(stockLocationLine.getReservedQty());
    Product product = stockMoveLine.getProduct();
    BigDecimal availableQtyToBeReservedSaleOrderLine =
        unitConversionService
            .convertManagingNullUnit(
                saleOrderLine.getUnit(),
                stockLocationLine.getUnit(),
                availableQtyToBeReserved,
                product)
            .add(saleOrderLine.getReservedQty());
    BigDecimal qtyThatWillBeAllocated =
        saleOrderLine.getQty().min(availableQtyToBeReservedSaleOrderLine);

    // allocate it
    if (qtyThatWillBeAllocated.compareTo(saleOrderLine.getReservedQty()) > 0) {
      saleOrderLineUpdateReservedQtyService.updateReservedQty(
          saleOrderLine, qtyThatWillBeAllocated);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateReservedQty(SaleOrderLine saleOrderLine, BigDecimal newReservedQty)
      throws AxelorException {
    saleOrderLineUpdateReservedQtyService.updateReservedQty(saleOrderLine, newReservedQty);
  }

  @Override
  public void deallocate(SaleOrderLine saleOrderLine) throws AxelorException {
    if (saleOrderLine.getProduct() == null || !saleOrderLine.getProduct().getStockManaged()) {
      return;
    }
    updateReservedQty(saleOrderLine, BigDecimal.ZERO);
  }

  @Override
  public void updateRequestedReservedQty(SaleOrderLine saleOrderLine, BigDecimal newReservedQty)
      throws AxelorException {
    saleOrderLineUpdateReservedQtyService.updateRequestedReservedQty(saleOrderLine, newReservedQty);
  }
}
