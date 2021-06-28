package com.axelor.apps.supplychain.service.stockreservation;

import com.axelor.JpaTestModule;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.StockLocationLineService;
import com.axelor.apps.stock.service.StockLocationService;
import com.axelor.db.JpaFixture;
import com.axelor.db.JpaSupport;
import com.axelor.exception.AxelorException;
import com.axelor.test.GuiceModules;
import com.axelor.test.GuiceRunner;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

@RunWith(GuiceRunner.class)
@GuiceModules({JpaTestModule.class})
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestReservedQtyStockWorkflowService extends JpaSupport {

  @Inject private ReservedQtyStockWorkflowServiceImpl reservedQtyService;
  @Inject private StockMoveRepository stockMoveRepository;
  @Inject private StockLocationLineService stockLocationLineService;
  @Inject private JpaFixture fixture;

  private StockMove stockMove;
  private StockLocationLine stockLocationLine;

  @Before
  @Transactional
  public void setUp() {
    if (all(Company.class).count() == 0) {
      fixture.load("stock-reservation/orderless-stock-move.yml");
    }
    stockMove = null;
    stockLocationLine = null;
  }

  /*
   * Tests
   */

  /**
   * Test a simple allocation: We request reservation for 1 unit with 10 unit available.
   *
   * @throws AxelorException
   */
  @Test
  public void testSimpleUpdateReservedQuantityWhenRequested() throws AxelorException {
    givenOrderlessStockMove();
    whenPlanningStockMove();
    thenReservedQty(BigDecimal.TEN, BigDecimal.TEN);
  }

  /**
   * Test partial allocation: we already requested 3 units, want the reservation for 7 more but
   * there is only 5 units available.
   *
   * @throws AxelorException
   */
  @Test
  public void testPartialReservedQuantityWhenRequested() throws AxelorException {
    givenPartiallyAllocatedStockMove();
    whenPlanningStockMove();
    thenReservedQty(new BigDecimal("8"), new BigDecimal("12"));
  }

  @Test
  public void testReservedQuantityWithStocksNotAvailable() throws AxelorException {
    givenStockMoveWithNoQtyAvailable();
    whenPlanningStockMove();
    thenReservedQty(BigDecimal.ZERO, BigDecimal.TEN);
  }

  @Test
  public void testDeallocationOnRealize() throws AxelorException {
    givenStockMoveToRealize();
    whenRealizingStockMove();
    thenReservedQty(BigDecimal.ZERO, BigDecimal.ZERO);
  }

  @Test
  public void testDeallocationOnCancel() throws AxelorException {
    givenStockMoveToCancel();
    whenCancelingStockMove();
    thenReservedQty(BigDecimal.ZERO, BigDecimal.ZERO);
  }

  private void givenOrderlessStockMove() {
    useStockMove("orderlessOutStockMove");
  }

  private void givenPartiallyAllocatedStockMove() {
    useStockMove("partiallyAllocatedOutStockMove");
  }

  private void givenStockMoveWithNoQtyAvailable() {
    useStockMove("stockMoveNoQtyAvailable");
  }

  private void givenStockMoveToRealize() {
    useStockMove("stockMoveToRealize");
  }

  private void givenStockMoveToCancel() {
    useStockMove("stockMoveToCancel");
  }

  private void useStockMove(String name) {
    stockMove = stockMoveRepository.findByName(name);
    StockMoveLine stockMoveLine = stockMove.getStockMoveLineList().get(0);
    stockLocationLine =
        stockLocationLineService.getStockLocationLine(
            stockMove.getFromStockLocation(), stockMoveLine.getProduct());
  }

  private void whenPlanningStockMove() throws AxelorException {
    reservedQtyService.updateReservedQuantity(stockMove, StockMoveRepository.STATUS_PLANNED);
  }

  private void whenRealizingStockMove() throws AxelorException {
    reservedQtyService.updateReservedQuantity(stockMove, StockMoveRepository.STATUS_REALIZED);
  }

  private void whenCancelingStockMove() throws AxelorException {
    reservedQtyService.updateReservedQuantity(stockMove, StockMoveRepository.STATUS_CANCELED);
  }

  private void thenReservedQty(
      BigDecimal stockMoveReservedQty, BigDecimal stockLocationLineReservedQty) {
    StockMoveLine stockMoveLine = stockMove.getStockMoveLineList().get(0);
    Assert.assertEquals(stockMoveReservedQty, stockMoveLine.getReservedQty());
    Assert.assertEquals(stockLocationLineReservedQty, stockLocationLine.getReservedQty());
  }
  //
  //  @Test
  //  public void testReservationWithSplitLine() throws AxelorException {
  //    clear();
  //    StockMove stockMove = createOutStockMoveHeader();
  //    StockMoveLine stockMoveLine =
  //        createStockMoveLine(
  //            1L, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, firstProduct,
  // LocalDateTime.now());
  //    addStockLocationLine(stockMoveLine, BigDecimal.TEN, BigDecimal.ZERO);
  //    SaleOrderLine saleOrderLine = createSaleOrderLine(stockMoveLine);
  //
  //    saleOrderLine.setQty(BigDecimal.valueOf(3));
  //    saleOrderLine.setRequestedReservedQty(BigDecimal.valueOf(3));
  //    saleOrderLine.setReservedQty(BigDecimal.ZERO);
  //
  //    StockMoveLine secondStockMoveLine =
  //        createStockMoveLine(
  //            2L, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, firstProduct,
  // LocalDateTime.now());
  //
  //    StockMoveLine thirdStockMoveLine =
  //        createStockMoveLine(
  //            3L, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, firstProduct,
  // LocalDateTime.now());
  //
  //    stockMove.addStockMoveLineListItem(stockMoveLine);
  //    stockMove.addStockMoveLineListItem(secondStockMoveLine);
  //    stockMove.addStockMoveLineListItem(thirdStockMoveLine);
  //    secondStockMoveLine.setSaleOrderLine(saleOrderLine);
  //    thirdStockMoveLine.setSaleOrderLine(saleOrderLine);
  //
  //    prepareStockLocationLineService();
  //    prepareSupplyChainConfigService(true, true);
  //    reservedQtyService.updateReservedQuantity(stockMove, StockMoveRepository.STATUS_PLANNED);
  //    Assert.assertEquals(BigDecimal.ONE, stockMoveLine.getReservedQty());
  //    Assert.assertEquals(BigDecimal.ONE, secondStockMoveLine.getReservedQty());
  //    Assert.assertEquals(BigDecimal.ONE, thirdStockMoveLine.getReservedQty());
  //    Assert.assertEquals(BigDecimal.valueOf(3), saleOrderLine.getReservedQty());
  //  }
  //
  //  @Test
  //  public void testAllocateReservedQuantityInSaleOrderLines() {
  //    // TODO
  //  }
}
