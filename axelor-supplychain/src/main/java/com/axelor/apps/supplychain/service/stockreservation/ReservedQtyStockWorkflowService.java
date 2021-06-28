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

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.exception.AxelorException;
import java.math.BigDecimal;

/**
 * A service which contains all methods managing the reservation feature. The purpose of this
 * service is to update accordingly all reservedQty and requestedReservedQty fields in
 * SaleOrderLine, StockMoveLine and StockLocationLine. The reservation is computed from stock move
 * lines then fields in sale order lines and stock location lines are updated.
 */
public interface ReservedQtyStockWorkflowService {

  /**
   * Called on stock move cancel, plan and realization to update requested reserved qty and reserved
   * qty.
   *
   * @param stockMove
   */
  void updateReservedQuantity(StockMove stockMove, int status) throws AxelorException;

  /**
   * Allocate a given quantity in stock move lines and sale order lines corresponding to the given
   * product and stock location. The first stock move to have the reservation will be the first to
   * have the quantity allocated.
   *
   * @param qtyToAllocate the quantity available to be allocated.
   * @param stockLocation a stock location.
   * @param product a product.
   * @param stockLocationLineUnit Unit of the stock location line.
   * @return The quantity that was allocated (in stock location line unit).
   */
  BigDecimal allocateReservedQuantityInSaleOrderLines(
      BigDecimal qtyToAllocate,
      StockLocation stockLocation,
      Product product,
      Unit stockLocationLineUnit)
      throws AxelorException;

  /**
   * In a partially realized stock move line, call this method to deallocate the quantity that will
   * be allocated to the newly generated stock move line.
   *
   * @param stockMoveLine
   * @param amountToDeallocate
   */
  void deallocateStockMoveLineAfterSplit(StockMoveLine stockMoveLine, BigDecimal amountToDeallocate)
      throws AxelorException;
}
