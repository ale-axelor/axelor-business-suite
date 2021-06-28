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
package com.axelor.apps.supplychain.web;

import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.repo.StockLocationLineRepository;
import com.axelor.apps.supplychain.service.stockreservation.StockLocationLineReservationService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;

public class StockLocationLineController {

  /**
   * Called from stock location line form view, on allocate button click. Call {@link
   * StockLocationLineReservationService#allocateAll(StockLocationLine)}
   *
   * @param request
   * @param response
   */
  public void allocateAll(ActionRequest request, ActionResponse response) {
    try {
      StockLocationLine stockLocationLine = request.getContext().asType(StockLocationLine.class);
      stockLocationLine =
          Beans.get(StockLocationLineRepository.class).find(stockLocationLine.getId());
      Beans.get(StockLocationLineReservationService.class).allocateAll(stockLocationLine);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from stock location line form view, on deallocateAll button click. Call {@link
   * StockLocationLineReservationService#deallocateAll(StockLocationLine)}
   *
   * @param request
   * @param response
   */
  public void deallocateAll(ActionRequest request, ActionResponse response) {
    try {
      StockLocationLine stockLocationLine = request.getContext().asType(StockLocationLine.class);
      stockLocationLine =
          Beans.get(StockLocationLineRepository.class).find(stockLocationLine.getId());
      Beans.get(StockLocationLineReservationService.class).deallocateAll(stockLocationLine);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
