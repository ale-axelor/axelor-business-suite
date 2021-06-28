/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.supplychain.service.stockreservation;

import com.axelor.apps.base.db.CancelReason;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.StockLocationLineService;
import com.axelor.apps.supplychain.db.SupplyChainConfig;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.apps.supplychain.service.config.SupplyChainConfigService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** This is the main implementation for {@link ReservedQtyStockWorkflowService}. */
public class ReservedQtyStockWorkflowServiceImpl implements ReservedQtyStockWorkflowService {

  protected StockLocationLineService stockLocationLineService;
  protected ReservedQtyFetchService reservedQtyFetchService;
  protected UnitConversionService unitConversionService;
  protected SupplyChainConfigService supplychainConfigService;
  protected AppBaseService appBaseService;
  protected AppSupplychainService appSupplychainService;
  protected SaleOrderLineUpdateReservedQtyService saleOrderLineUpdateReservedQtyService;
  protected StockMoveLineReservationService stockMoveLineReservationService;
  protected StockLocationLineUpdateReservedQtyService stockLocationLineUpdateReservedQtyService;

  @Inject
  public ReservedQtyStockWorkflowServiceImpl(
    StockLocationLineService stockLocationLineService,
    ReservedQtyFetchService reservedQtyFetchService,
    UnitConversionService unitConversionService,
    SupplyChainConfigService supplychainConfigService,
    AppBaseService appBaseService,
    AppSupplychainService appSupplychainService,
    SaleOrderLineUpdateReservedQtyService saleOrderLineUpdateReservedQtyService,
    StockMoveLineReservationService stockMoveLineReservationService,
    StockLocationLineUpdateReservedQtyService stockLocationLineUpdateReservedQtyService) {
    this.stockLocationLineService = stockLocationLineService;
    this.reservedQtyFetchService = reservedQtyFetchService;
    this.unitConversionService = unitConversionService;
    this.supplychainConfigService = supplychainConfigService;
    this.appBaseService = appBaseService;
    this.appSupplychainService = appSupplychainService;
    this.saleOrderLineUpdateReservedQtyService = saleOrderLineUpdateReservedQtyService;
    this.stockMoveLineReservationService = stockMoveLineReservationService;
    this.stockLocationLineUpdateReservedQtyService = stockLocationLineUpdateReservedQtyService;
  }



  @Override
  public void updateReservedQuantity(StockMove stockMove, int status) throws AxelorException {
    List<StockMoveLine> stockMoveLineList = stockMove.getStockMoveLineList();
    if (stockMoveLineList != null) {
      stockMoveLineList =
          stockMoveLineList.stream()
              .filter(
                  smLine -> smLine.getProduct() != null && smLine.getProduct().getStockManaged())
              .collect(Collectors.toList());
      // check quantities in stock move lines
      for (StockMoveLine stockMoveLine : stockMoveLineList) {
        if (status == StockMoveRepository.STATUS_PLANNED) {
          changeRequestedQtyLowerThanQty(stockMoveLine);
        }
        checkRequestedAndReservedQty(stockMoveLine);
      }
      if (status == StockMoveRepository.STATUS_REALIZED) {
        consolidateReservedQtyInStockMoveLineByProduct(stockMove);
      }
      stockMoveLineList.sort(Comparator.comparing(StockMoveLine::getId));
      for (StockMoveLine stockMoveLine : stockMoveLineList) {
        BigDecimal qty = stockMoveLine.getRealQty();
        // requested quantity is quantity requested is the line subtracted by the quantity already
        // allocated
        BigDecimal requestedReservedQty =
            stockMoveLine.getRequestedReservedQty().subtract(stockMoveLine.getReservedQty());
        updateRequestedQuantityInLocations(
            stockMoveLine,
            stockMove.getFromStockLocation(),
            stockMove.getToStockLocation(),
            stockMoveLine.getProduct(),
            qty,
            requestedReservedQty,
            status);
      }
    }
  }

  /**
   * On planning, we want the requested quantity to be equal or lower to the quantity of the line.
   * So, if the requested quantity is greater than the quantity, we change it to be equal.
   *
   * @param stockMoveLine
   * @throws AxelorException
   */
  protected void changeRequestedQtyLowerThanQty(StockMoveLine stockMoveLine)
      throws AxelorException {
    BigDecimal qty = stockMoveLine.getRealQty().max(BigDecimal.ZERO);
    BigDecimal requestedReservedQty = stockMoveLine.getRequestedReservedQty();
    if (requestedReservedQty.compareTo(qty) > 0) {
      Product product = stockMoveLine.getProduct();
      BigDecimal diffRequestedQty = requestedReservedQty.subtract(qty);
      stockMoveLine.setRequestedReservedQty(qty);
      // update in stock location line
      StockLocationLine stockLocationLine =
          stockLocationLineService.getOrCreateStockLocationLine(
              stockMoveLine.getStockMove().getFromStockLocation(), product);
      BigDecimal diffRequestedQuantityLocation =
          unitConversionService.convertManagingNullUnit(
              stockMoveLine.getUnit(), stockLocationLine.getUnit(), diffRequestedQty, product);
      stockLocationLine.setRequestedReservedQty(
          stockLocationLine.getRequestedReservedQty().add(diffRequestedQuantityLocation));
    }
  }

  /**
   * Check value of requested and reserved qty in stock move line.
   *
   * @param stockMoveLine the stock move line to be checked
   * @throws AxelorException if the quantities are negative or superior to the planned qty.
   */
  protected void checkRequestedAndReservedQty(StockMoveLine stockMoveLine) throws AxelorException {
    BigDecimal plannedQty = stockMoveLine.getQty().max(BigDecimal.ZERO);
    BigDecimal requestedReservedQty = stockMoveLine.getRequestedReservedQty();
    BigDecimal reservedQty = stockMoveLine.getReservedQty();

    String stockMoveLineSeq =
        stockMoveLine.getStockMove() == null
            ? stockMoveLine.getId().toString()
            : stockMoveLine.getStockMove().getStockMoveSeq() + "-" + stockMoveLine.getSequence();

    if (reservedQty.signum() < 0 || requestedReservedQty.signum() < 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_RESERVATION_QTY_NEGATIVE));
    }
    if (requestedReservedQty.compareTo(plannedQty) > 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_REQUESTED_QTY_TOO_HIGH),
          stockMoveLineSeq);
    }
    if (reservedQty.compareTo(plannedQty) > 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_ALLOCATED_QTY_TOO_HIGH),
          stockMoveLineSeq);
    }
  }

  /**
   * For lines with duplicate product, fill all the reserved qty in one line and empty the others.
   *
   * @param stockMove
   */
  protected void consolidateReservedQtyInStockMoveLineByProduct(StockMove stockMove) {
    if (stockMove.getStockMoveLineList() == null) {
      return;
    }
    List<Product> productList =
        stockMove.getStockMoveLineList().stream()
            .map(StockMoveLine::getProduct)
            .filter(Objects::nonNull)
            .filter(Product::getStockManaged)
            .distinct()
            .collect(Collectors.toList());
    for (Product product : productList) {
      if (product != null) {
        List<StockMoveLine> stockMoveLineListToConsolidate =
            stockMove.getStockMoveLineList().stream()
                .filter(stockMoveLine1 -> product.equals(stockMoveLine1.getProduct()))
                .collect(Collectors.toList());
        if (stockMoveLineListToConsolidate.size() > 1) {
          stockMoveLineListToConsolidate.sort(Comparator.comparing(StockMoveLine::getId));
          BigDecimal reservedQtySum =
              stockMoveLineListToConsolidate.stream()
                  .map(StockMoveLine::getReservedQty)
                  .reduce(BigDecimal::add)
                  .orElse(BigDecimal.ZERO);
          stockMoveLineListToConsolidate.forEach(
              toConsolidateStockMoveLine ->
                  toConsolidateStockMoveLine.setReservedQty(BigDecimal.ZERO));
          stockMoveLineListToConsolidate.get(0).setReservedQty(reservedQtySum);
        }
      }
    }
  }

  /**
   * Update requested quantity for internal or external location.
   *
   * @param stockMoveLine
   * @param fromStockLocation
   * @param toStockLocation
   * @param product
   * @param qty the quantity in stock move unit.
   * @param requestedReservedQty the requested reserved quantity in stock move unit.
   * @param toStatus
   * @throws AxelorException
   */
  protected void updateRequestedQuantityInLocations(
      StockMoveLine stockMoveLine,
      StockLocation fromStockLocation,
      StockLocation toStockLocation,
      Product product,
      BigDecimal qty,
      BigDecimal requestedReservedQty,
      int toStatus)
      throws AxelorException {
    if (fromStockLocation.getTypeSelect() != StockLocationRepository.TYPE_VIRTUAL) {
      updateRequestedQuantityInFromStockLocation(
          stockMoveLine, fromStockLocation, product, toStatus, requestedReservedQty);
    }
    if (toStockLocation.getTypeSelect() != StockLocationRepository.TYPE_VIRTUAL) {
      updateRequestedQuantityInToStockLocation(
          stockMoveLine, toStockLocation, product, toStatus, qty);
    }
  }

  /**
   * Update location line and stock move line with computed allocated quantity, where the location
   * is {@link com.axelor.apps.stock.db.StockMove#fromStockLocation}
   *
   * @param stockMoveLine a stock move line
   * @param stockLocation a stock location
   * @param product the product of the line. If the product is not managed in stock, this method
   *     does nothing.
   * @param toStatus target status for the stock move
   * @param requestedReservedQty the requested reserved quantity in stock move unit
   * @throws AxelorException
   */
  protected void updateRequestedQuantityInFromStockLocation(
      StockMoveLine stockMoveLine,
      StockLocation stockLocation,
      Product product,
      int toStatus,
      BigDecimal requestedReservedQty)
      throws AxelorException {
    if (product == null || !product.getStockManaged()) {
      return;
    }
    Unit stockMoveLineUnit = stockMoveLine.getUnit();

    StockLocationLine stockLocationLine =
        stockLocationLineService.getStockLocationLine(stockLocation, product);
    if (stockLocationLine == null) {
      return;
    }
    Unit stockLocationLineUnit = stockLocationLine.getUnit();
    // the quantity that will be allocated in stock location line
    BigDecimal realReservedQty;

    // the quantity that will be allocated in stock move line
    BigDecimal realReservedStockMoveQty;

    // if we cancel, subtract the quantity using the previously allocated quantity.
    if (toStatus == StockMoveRepository.STATUS_CANCELED
        || toStatus == StockMoveRepository.STATUS_REALIZED) {
      realReservedStockMoveQty = stockMoveLine.getReservedQty();

      // convert the quantity for stock location line

      realReservedQty =
          unitConversionService.convertManagingNullUnit(
              stockMoveLineUnit,
              stockLocationLineUnit,
              realReservedStockMoveQty,
              stockMoveLine.getProduct());

      // reallocate quantity in other stock move lines
      if (isReallocatingQtyOnCancel(stockMoveLine)) {
        reallocateQty(stockMoveLine, stockLocation, stockLocationLine, product, realReservedQty);
      }

      // no more reserved qty in stock move and sale order lines
      stockMoveLineReservationService.updateReservedQuantityFromStockMoveLine(
          stockMoveLine, product, stockMoveLine.getReservedQty().negate());

      // update requested quantity in sale order line
      SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
      if (saleOrderLine != null) {
        // requested quantity should never be below delivered quantity.
        if (toStatus == StockMoveRepository.STATUS_REALIZED) {
          saleOrderLine.setRequestedReservedQty(
              saleOrderLine.getRequestedReservedQty().max(saleOrderLine.getDeliveredQty()));
        } else if (!saleOrderLine.getIsQtyRequested()) {
          // if we cancel and do not want to request quantity, the requested quantity become the new
          // delivered quantity.
          saleOrderLine.setRequestedReservedQty(saleOrderLine.getDeliveredQty());
        }
      }

    } else {
      BigDecimal requestedReservedQtyInLocation =
          unitConversionService.convertManagingNullUnit(
              stockMoveLineUnit, stockLocationLine.getUnit(), requestedReservedQty, product);
      realReservedQty = computeRealReservedQty(stockLocationLine, requestedReservedQtyInLocation);
      // convert back the quantity for the stock move line
      realReservedStockMoveQty =
          unitConversionService.convertManagingNullUnit(
              stockLocationLineUnit,
              stockMoveLineUnit,
              realReservedQty,
              stockMoveLine.getProduct());
      stockMoveLineReservationService.updateReservedQuantityFromStockMoveLine(
          stockMoveLine, product, realReservedStockMoveQty);

      // reallocate quantity in other stock move lines
      if (supplychainConfigService
          .getSupplyChainConfig(stockLocation.getCompany())
          .getAutoAllocateOnAllocation()) {
        BigDecimal availableQuantityInLocation =
            stockLocationLine.getCurrentQty().subtract(stockLocationLine.getReservedQty());
        availableQuantityInLocation =
            unitConversionService.convertManagingNullUnit(
                stockLocationLineUnit, stockMoveLineUnit, availableQuantityInLocation, product);
        BigDecimal qtyRemainingToAllocate =
            availableQuantityInLocation.subtract(realReservedStockMoveQty);
        reallocateQty(
            stockMoveLine, stockLocation, stockLocationLine, product, qtyRemainingToAllocate);
      }
    }

    stockLocationLineUpdateReservedQtyService.updateReservedQty(stockLocationLine);
    stockLocationLineUpdateReservedQtyService.updateRequestedReservedQty(stockLocationLine);
    checkReservedQtyStocks(stockLocationLine, stockMoveLine, toStatus);
  }

  /**
   * Check in the stock move for cancel reason and return the config in cancel reason.
   *
   * @param stockMoveLine
   * @return the value of the boolean field on cancel reason if found else false.
   */
  protected boolean isReallocatingQtyOnCancel(StockMoveLine stockMoveLine) {
    return Optional.of(stockMoveLine)
        .map(StockMoveLine::getStockMove)
        .map(StockMove::getCancelReason)
        .map(CancelReason::getCancelQuantityAllocation)
        .orElse(false);
  }

  /**
   * Update location line, stock move line and sale order line with computed allocated quantity,
   * where the location is {@link com.axelor.apps.stock.db.StockMove#toStockLocation}.
   *
   * @param stockMoveLine a stock move line.
   * @param stockLocation a stock location.
   * @param product the product of the line. If the product is not managed in stock, this method
   *     does nothing.
   * @param toStatus target status for the stock move.
   * @param qty the quantity in stock move unit.
   * @throws AxelorException
   */
  protected void updateRequestedQuantityInToStockLocation(
      StockMoveLine stockMoveLine,
      StockLocation stockLocation,
      Product product,
      int toStatus,
      BigDecimal qty)
      throws AxelorException {
    if (product == null || !product.getStockManaged()) {
      return;
    }
    StockLocationLine stockLocationLine =
        stockLocationLineService.getStockLocationLine(stockLocation, product);
    if (stockLocationLine == null) {
      return;
    }
    Company company = stockLocationLine.getStockLocation().getCompany();
    SupplyChainConfig supplyChainConfig = supplychainConfigService.getSupplyChainConfig(company);
    if (toStatus == StockMoveRepository.STATUS_REALIZED
        && supplyChainConfig.getAutoAllocateOnReceipt()) {
      reallocateQty(stockMoveLine, stockLocation, stockLocationLine, product, qty);
    }
    stockLocationLineUpdateReservedQtyService.updateRequestedReservedQty(stockLocationLine);
    checkReservedQtyStocks(stockLocationLine, stockMoveLine, toStatus);
  }

  /**
   * Reallocate quantity in stock location line after entry into storage.
   *
   * @param stockMoveLine
   * @param stockLocation
   * @param stockLocationLine
   * @param product
   * @param qty the quantity in stock move line unit.
   * @throws AxelorException
   */
  protected void reallocateQty(
      StockMoveLine stockMoveLine,
      StockLocation stockLocation,
      StockLocationLine stockLocationLine,
      Product product,
      BigDecimal qty)
      throws AxelorException {

    Unit stockMoveLineUnit = stockMoveLine.getUnit();
    Unit stockLocationLineUnit = stockLocationLine.getUnit();

    BigDecimal stockLocationQty =
        unitConversionService.convertManagingNullUnit(
            stockMoveLineUnit, stockLocationLineUnit, qty, product);
    // the quantity that will be allocated in stock location line
    BigDecimal realReservedQty;

    // the quantity that will be allocated in stock move line
    BigDecimal leftToAllocate =
        stockLocationLine.getRequestedReservedQty().subtract(stockLocationLine.getReservedQty());
    realReservedQty = stockLocationQty.min(leftToAllocate);

    allocateReservedQuantityInSaleOrderLines(
        realReservedQty, stockLocation, product, stockLocationLineUnit, stockMoveLine);
    stockLocationLineUpdateReservedQtyService.updateReservedQty(stockLocationLine);
  }

  @Override
  public BigDecimal allocateReservedQuantityInSaleOrderLines(
      BigDecimal qtyToAllocate,
      StockLocation stockLocation,
      Product product,
      Unit stockLocationLineUnit)
      throws AxelorException {
    if (product == null || !product.getStockManaged()) {
      return BigDecimal.ZERO;
    }
    return allocateReservedQuantityInSaleOrderLines(
        qtyToAllocate, stockLocation, product, stockLocationLineUnit, null);
  }

  /**
   * The new parameter allocated stock move line is used if we are allocating a stock move line.
   * This method will reallocate the lines with the same stock move (and the same product) before
   * other stock move lines.
   *
   * <p>We are using an optional because in the basic use of the method, the argument is empty.
   */
  protected BigDecimal allocateReservedQuantityInSaleOrderLines(
      BigDecimal qtyToAllocate,
      StockLocation stockLocation,
      Product product,
      Unit stockLocationLineUnit,
      StockMoveLine allocatedStockMoveLine)
      throws AxelorException {
    List<StockMoveLine> stockMoveLineListToAllocate =
        reservedQtyFetchService.fetchStockMoveLineListToAllocate(stockLocation, product);

    // put stock move lines with the same stock move on the beginning of the list.
    if (allocatedStockMoveLine != null) {
      stockMoveLineListToAllocate.sort(
          // Note: this comparator imposes orderings that are inconsistent with equals.
          (sml1, sml2) -> {
            if (sml1.getStockMove().equals(sml2.getStockMove())) {
              return 0;
            } else if (sml1.getStockMove().equals(allocatedStockMoveLine.getStockMove())) {
              return -1;
            } else if (sml2.getStockMove().equals(allocatedStockMoveLine.getStockMove())) {
              return 1;
            } else {
              return 0;
            }
          });
    }
    BigDecimal leftQtyToAllocate = qtyToAllocate;
    for (StockMoveLine stockMoveLine : stockMoveLineListToAllocate) {
      BigDecimal leftQtyToAllocateStockMove =
          unitConversionService.convertManagingNullUnit(
              stockLocationLineUnit, stockMoveLine.getUnit(), leftQtyToAllocate, product);
      BigDecimal neededQtyToAllocate =
          stockMoveLine.getRequestedReservedQty().subtract(stockMoveLine.getReservedQty());
      BigDecimal allocatedStockMoveQty = leftQtyToAllocateStockMove.min(neededQtyToAllocate);

      BigDecimal allocatedQty =
          unitConversionService.convertManagingNullUnit(
              stockMoveLine.getUnit(), stockLocationLineUnit, allocatedStockMoveQty, product);

      // update reserved qty in stock move line and sale order line
      stockMoveLineReservationService.updateReservedQuantityFromStockMoveLine(
          stockMoveLine, product, allocatedStockMoveQty);
      // update left qty to allocate
      leftQtyToAllocate = leftQtyToAllocate.subtract(allocatedQty);
    }

    return qtyToAllocate.subtract(leftQtyToAllocate);
  }

  /**
   * Allocated qty cannot be greater than available qty.
   *
   * @param stockLocationLine
   * @param stockMoveLine
   * @throws AxelorException
   */
  protected void checkReservedQtyStocks(
      StockLocationLine stockLocationLine, StockMoveLine stockMoveLine, int toStatus)
      throws AxelorException {

    if (((toStatus == StockMoveRepository.STATUS_REALIZED)
            || toStatus == StockMoveRepository.STATUS_CANCELED)
        && stockLocationLine.getReservedQty().compareTo(stockLocationLine.getCurrentQty()) > 0) {
      BigDecimal convertedAvailableQtyInStockMove =
          unitConversionService.convertManagingNullUnit(
              stockMoveLine.getUnit(),
              stockLocationLine.getUnit(),
              stockMoveLine.getRealQty(),
              stockLocationLine.getProduct());
      BigDecimal convertedReservedQtyInStockMove =
          unitConversionService.convertManagingNullUnit(
              stockMoveLine.getUnit(),
              stockLocationLine.getUnit(),
              stockMoveLine.getReservedQty(),
              stockLocationLine.getProduct());

      BigDecimal availableQty =
          convertedAvailableQtyInStockMove
              .add(stockLocationLine.getCurrentQty())
              .subtract(convertedReservedQtyInStockMove.add(stockLocationLine.getReservedQty()));
      BigDecimal neededQty =
          convertedAvailableQtyInStockMove.subtract(convertedReservedQtyInStockMove);
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LOCATION_LINE_NOT_ENOUGH_AVAILABLE_QTY),
          stockLocationLine.getProduct().getFullName(),
          availableQty,
          neededQty);
    }
  }

  /**
   * From the requested reserved quantity, return the quantity that can in fact be reserved.
   *
   * @param stockLocationLine the location line.
   * @param requestedReservedQty the quantity that can be added to the real quantity
   * @return the quantity really added.
   */
  protected BigDecimal computeRealReservedQty(
      StockLocationLine stockLocationLine, BigDecimal requestedReservedQty) {

    BigDecimal qtyLeftToBeAllocated =
        stockLocationLine.getCurrentQty().subtract(stockLocationLine.getReservedQty());
    return qtyLeftToBeAllocated.min(requestedReservedQty).max(BigDecimal.ZERO);
  }

  @Override
  public void deallocateStockMoveLineAfterSplit(
      StockMoveLine stockMoveLine, BigDecimal amountToDeallocate) throws AxelorException {

    if (stockMoveLine.getProduct() == null || !stockMoveLine.getProduct().getStockManaged()) {
      return;
    }
    // deallocate in sale order line
    SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
    if (saleOrderLine != null) {
      saleOrderLineUpdateReservedQtyService.updateReservedQty(saleOrderLine);
    }
    // deallocate in stock location line
    if (stockMoveLine.getStockMove() != null) {
      StockLocationLine stockLocationLine =
          stockLocationLineService.getStockLocationLine(
              stockMoveLine.getStockMove().getFromStockLocation(), stockMoveLine.getProduct());
      if (stockLocationLine != null) {
        stockLocationLineUpdateReservedQtyService.updateReservedQty(stockLocationLine);
      }
    }
  }
}
