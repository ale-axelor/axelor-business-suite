package com.axelor.apps.supplychain.service.stockreservation;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
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

public class StockMoveLineReservationServiceImpl implements StockMoveLineReservationService {

  protected StockLocationLineService stockLocationLineService;
  protected AppBaseService appBaseService;
  protected UnitConversionService unitConversionService;
  protected SaleOrderLineStockReservationService saleOrderLineStockReservationService;
  protected StockMoveLineUpdateReservedQtyService stockMoveLineUpdateReservedQtyService;
  protected SaleOrderLineUpdateReservedQtyService saleOrderLineUpdateReservedQtyService;
  protected StockLocationLineUpdateReservedQtyService stockLocationLineUpdateReservedQtyService;
  protected AppSupplychainService appSupplychainService;

  @Inject
  public StockMoveLineReservationServiceImpl(
    StockLocationLineService stockLocationLineService,
    AppBaseService appBaseService,
    UnitConversionService unitConversionService,
    SaleOrderLineStockReservationService saleOrderLineStockReservationService,
    StockMoveLineUpdateReservedQtyService stockMoveLineUpdateReservedQtyService,
    SaleOrderLineUpdateReservedQtyService saleOrderLineUpdateReservedQtyService,
    StockLocationLineUpdateReservedQtyService stockLocationLineUpdateReservedQtyService,
    AppSupplychainService appSupplychainService) {
    this.stockLocationLineService = stockLocationLineService;
    this.appBaseService = appBaseService;
    this.unitConversionService = unitConversionService;
    this.saleOrderLineStockReservationService = saleOrderLineStockReservationService;
    this.stockMoveLineUpdateReservedQtyService = stockMoveLineUpdateReservedQtyService;
    this.saleOrderLineUpdateReservedQtyService = saleOrderLineUpdateReservedQtyService;
    this.stockLocationLineUpdateReservedQtyService = stockLocationLineUpdateReservedQtyService;
    this.appSupplychainService = appSupplychainService;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void requestQty(StockMoveLine stockMoveLine) throws AxelorException {
    if (stockMoveLine.getProduct() == null || !stockMoveLine.getProduct().getStockManaged()) {
      return;
    }
    SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
    if (saleOrderLine != null) {
      saleOrderLineStockReservationService.requestQty(saleOrderLine);
    } else {
      stockMoveLine.setReservationDateTime(appBaseService.getTodayDateTime().toLocalDateTime());
      stockMoveLine.setIsQtyRequested(true);
      if (stockMoveLine.getQty().signum() < 0) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.SALE_ORDER_LINE_REQUEST_QTY_NEGATIVE));
      }
      stockMoveLineUpdateReservedQtyService.updateRequestedReservedQty(
          stockMoveLine, stockMoveLine.getQty());
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void cancelReservation(StockMoveLine stockMoveLine) throws AxelorException {
    if (stockMoveLine.getProduct() == null || !stockMoveLine.getProduct().getStockManaged()) {
      return;
    }
    SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
    if (saleOrderLine != null) {
      saleOrderLineStockReservationService.cancelReservation(saleOrderLine);
    } else {
      stockMoveLine.setIsQtyRequested(false);
      stockMoveLine.setReservationDateTime(null);
      stockMoveLineUpdateReservedQtyService.updateRequestedReservedQty(
          stockMoveLine, BigDecimal.ZERO);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void allocate(StockMoveLine stockMoveLine) throws AxelorException {
    requestQty(stockMoveLine);
    SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
    if (saleOrderLine != null) {
      saleOrderLineStockReservationService.allocate(saleOrderLine);
    } else {
      // search for the maximum quantity that can be allocated in the stock move line.
      StockLocationLine stockLocationLine =
          stockLocationLineService.getOrCreateStockLocationLine(
              stockMoveLine.getStockMove().getFromStockLocation(), stockMoveLine.getProduct());
      BigDecimal availableQtyToBeReserved =
          stockLocationLine.getCurrentQty().subtract(stockLocationLine.getReservedQty());
      Product product = stockMoveLine.getProduct();
      BigDecimal availableQtyToBeReservedStockMoveLine =
          unitConversionService
              .convertManagingNullUnit(
                  stockMoveLine.getUnit(),
                  stockLocationLine.getUnit(),
                  availableQtyToBeReserved,
                  product)
              .add(stockMoveLine.getReservedQty());
      BigDecimal qtyThatWillBeAllocated =
          stockMoveLine.getQty().min(availableQtyToBeReservedStockMoveLine);

      // allocate it
      if (qtyThatWillBeAllocated.compareTo(stockMoveLine.getReservedQty()) > 0) {
        updateReservedQty(stockMoveLine, qtyThatWillBeAllocated);
      }
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateReservedQty(StockMoveLine stockMoveLine, BigDecimal newReservedQty)
      throws AxelorException {
    StockLocationLine stockLocationLine =
        stockLocationLineService.getOrCreateStockLocationLine(
            stockMoveLine.getStockMove().getFromStockLocation(), stockMoveLine.getProduct());
    updateReservedQty(stockLocationLine, stockMoveLine, newReservedQty);
  }

  protected void updateReservedQty(
      StockLocationLine stockLocationLine, StockMoveLine stockMoveLine, BigDecimal newReservedQty)
      throws AxelorException {
    if (stockMoveLine.getProduct() == null || !stockMoveLine.getProduct().getStockManaged()) {
      return;
    }
    SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
    if (saleOrderLine != null) {
      saleOrderLineStockReservationService.updateReservedQty(saleOrderLine, newReservedQty);
    } else {
      stockMoveLineUpdateReservedQtyService.checkBeforeUpdatingQties(stockMoveLine, newReservedQty);
      if (appSupplychainService.getAppSupplychain().getBlockDeallocationOnAvailabilityRequest()) {
        stockMoveLineUpdateReservedQtyService.checkAvailabilityRequest(
            stockMoveLine, newReservedQty, false);
      }
      if (stockMoveLine.getRequestedReservedQty().compareTo(newReservedQty) < 0
          && newReservedQty.compareTo(BigDecimal.ZERO) > 0) {
        stockMoveLineUpdateReservedQtyService.updateRequestedReservedQty(
            stockMoveLine, newReservedQty);
        stockMoveLineUpdateReservedQtyService.updateRequestedReservedQty(
            stockMoveLine, stockMoveLine.getQty());
      }

      BigDecimal availableQtyToBeReserved =
          stockLocationLine.getCurrentQty().subtract(stockLocationLine.getReservedQty());
      BigDecimal diffReservedQuantity = newReservedQty.subtract(stockMoveLine.getReservedQty());
      Product product = stockMoveLine.getProduct();
      BigDecimal diffReservedQuantityLocation =
          unitConversionService.convertManagingNullUnit(
              stockMoveLine.getUnit(), stockLocationLine.getUnit(), diffReservedQuantity, product);
      if (availableQtyToBeReserved.compareTo(diffReservedQuantityLocation) < 0) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.SALE_ORDER_LINE_QTY_NOT_AVAILABLE));
      }
      stockMoveLine.setReservedQty(newReservedQty);

      // update in stock location line
      stockLocationLineUpdateReservedQtyService.updateReservedQty(stockLocationLine);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void deallocate(StockMoveLine stockMoveLine) throws AxelorException {
    updateReservedQty(stockMoveLine, BigDecimal.ZERO);
  }

  @Override
  public void updateReservedQuantityFromStockMoveLine(
      StockMoveLine stockMoveLine, Product product, BigDecimal reservedQtyToAdd)
      throws AxelorException {
    if (product == null || !product.getStockManaged()) {
      return;
    }
    SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
    stockMoveLine.setReservedQty(stockMoveLine.getReservedQty().add(reservedQtyToAdd));
    if (saleOrderLine != null) {
      saleOrderLineUpdateReservedQtyService.updateReservedQty(saleOrderLine);
    }
  }
}
