/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.supplychain.service;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.exception.IExceptionMessage;
import com.axelor.apps.stock.service.StockLocationLineServiceImpl;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.servlet.RequestScoped;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@RequestScoped
public class StockLocationLineServiceSupplychainImpl extends StockLocationLineServiceImpl {

  @Override
  public StockLocationLine updateLocation(
      StockLocationLine stockLocationLine,
      BigDecimal qty,
      boolean current,
      boolean future,
      boolean isIncrement,
      LocalDate lastFutureStockMoveDate,
      BigDecimal reservedQty,
      BigDecimal requestedReservedQty) {

    stockLocationLine =
        super.updateLocation(
            stockLocationLine,
            qty,
            current,
            future,
            isIncrement,
            lastFutureStockMoveDate,
            reservedQty,
            requestedReservedQty);
    if (current) {
      if (isIncrement) {
        stockLocationLine.setReservedQty(stockLocationLine.getReservedQty().add(reservedQty));
        stockLocationLine.setRequestedReservedQty(
            stockLocationLine.getRequestedReservedQty().add(requestedReservedQty));
      } else {
        stockLocationLine.setReservedQty(stockLocationLine.getReservedQty().subtract(reservedQty));
        stockLocationLine.setRequestedReservedQty(
            stockLocationLine.getRequestedReservedQty().subtract(requestedReservedQty));
      }
    }
    if (future) {
      if (isIncrement) {
        stockLocationLine.setReservedQty(stockLocationLine.getReservedQty().subtract(reservedQty));
        stockLocationLine.setRequestedReservedQty(
            stockLocationLine.getRequestedReservedQty().subtract(requestedReservedQty));
      } else {
        stockLocationLine.setReservedQty(stockLocationLine.getReservedQty().add(reservedQty));
        stockLocationLine.setRequestedReservedQty(
            stockLocationLine.getRequestedReservedQty().add(requestedReservedQty));
      }
      stockLocationLine.setLastFutureStockMoveDate(lastFutureStockMoveDate);
    }
    return stockLocationLine;
  }

  @Override
  public void checkIfEnoughStock(StockLocation stockLocation, Product product, BigDecimal qty)
      throws AxelorException {
    super.checkIfEnoughStock(stockLocation, product, qty);

    if (Beans.get(AppSupplychainService.class).getAppSupplychain().getManageStockReservation()
        && product.getStockManaged()) {
      StockLocationLine stockLocationLine = this.getStockLocationLine(stockLocation, product);
      if (stockLocationLine != null
          && stockLocationLine
                  .getCurrentQty()
                  .subtract(stockLocationLine.getReservedQty())
                  .compareTo(qty)
              < 0) {
        throw new AxelorException(
            stockLocationLine,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.LOCATION_LINE_RESERVED_QTY),
            stockLocationLine.getProduct().getName(),
            stockLocationLine.getProduct().getCode());
      }
    }
  }

  @Override
  public BigDecimal getAvailableQty(StockLocation stockLocation, Product product) {
    StockLocationLine stockLocationLine = getStockLocationLine(stockLocation, product);
    BigDecimal availableQty = BigDecimal.ZERO;
    if (stockLocationLine != null) {
      availableQty = stockLocationLine.getCurrentQty().subtract(stockLocationLine.getReservedQty());
    }
    return availableQty;
  }

  /**
   * From the requested reserved quantity, return the quantity that can in fact be reserved.
   *
   * @param stockLocation the location of the line.
   * @param product the product of the line.
   * @param requestedReservedQty the quantity that can be added to the reserved qty.
   * @return the quantity really added.
   */
  public BigDecimal computeRealReservedQty(
      StockLocation stockLocation, Product product, BigDecimal requestedReservedQty) {

    Optional<StockLocationLine> stockLocationLine =
        Optional.ofNullable(getStockLocationLine(stockLocation, product));
    BigDecimal oldReservedQty =
        stockLocationLine.map(StockLocationLine::getReservedQty).orElse(BigDecimal.ZERO);
    BigDecimal currentQty =
        stockLocationLine.map(StockLocationLine::getCurrentQty).orElse(BigDecimal.ZERO);
    BigDecimal qtyLeftToBeAllocated = currentQty.subtract(oldReservedQty);
    return qtyLeftToBeAllocated.min(requestedReservedQty);
  }
}
