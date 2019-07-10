package bytecoin;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.event.DepthEvent;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;

import database.DbOperations;

public class DepthCache {

	private static final String BIDS = "BIDS";
	private static final String ASKS = "ASKS";

	private final String symbol;
	private final BinanceApiRestClient restClient;
	private final BinanceApiWebSocketClient wsClient;
	private final WsCallback wsCallback = new WsCallback();
	private final Map<String, NavigableMap<BigDecimal, BigDecimal>> depthCache = new HashMap<String, NavigableMap<BigDecimal, BigDecimal>>();

	private long lastUpdateId = -1;
	private volatile Closeable webSocket;

	private final DbOperations dbConn;

	public DepthCache(String symbol) {
		this.symbol = symbol;
		this.dbConn = new DbOperations();

		BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance();
		this.wsClient = factory.newWebSocketClient();
		this.restClient = factory.newRestClient();

		initialize();
	}

	public void initialize() {
		// 1. Subscribe to depth events and cache any events that are received.
		final List<DepthEvent> pendingDeltas = startDepthEventStreaming();

		// 2. Get a snapshot from the rest endpoint and use it to build your initial
		// depth cache.
		initializeDepthCache();

		// 3. & 4. handled in here.
		applyPendingDeltas(pendingDeltas);
	}

	/**
	 * Begins streaming of depth events.
	 *
	 * Any events received are cached until the rest API is polled for an initial
	 * snapshot.
	 */
	private List<DepthEvent> startDepthEventStreaming() {
		final List<DepthEvent> pendingDeltas = new CopyOnWriteArrayList<DepthEvent>();
		wsCallback.setHandler(pendingDeltas::add);

		this.webSocket = wsClient.onDepthEvent(symbol.toLowerCase(), wsCallback);

		return pendingDeltas;
	}

	/**
	 * 2. Initializes the depth cache by getting a snapshot from the REST API.
	 */
	private void initializeDepthCache() {
		OrderBook orderBook = restClient.getOrderBook(symbol.toUpperCase(), 10);

		this.lastUpdateId = orderBook.getLastUpdateId();

		NavigableMap<BigDecimal, BigDecimal> asks = new TreeMap<>(Comparator.reverseOrder());
		for (OrderBookEntry ask : orderBook.getAsks()) {
			asks.put(new BigDecimal(ask.getPrice()), new BigDecimal(ask.getQty()));
		}
		depthCache.put(ASKS, asks);

		NavigableMap<BigDecimal, BigDecimal> bids = new TreeMap<>(Comparator.reverseOrder());
		for (OrderBookEntry bid : orderBook.getBids()) {
			bids.put(new BigDecimal(bid.getPrice()), new BigDecimal(bid.getQty()));
		}
		depthCache.put(BIDS, bids);
	}

	/**
	 * Deal with any cached updates and switch to normal running.
	 */
	private void applyPendingDeltas(final List<DepthEvent> pendingDeltas) {
		final Consumer<DepthEvent> updateOrderBook = newEvent -> {
			if (newEvent.getFinalUpdateId() > lastUpdateId) {
				System.out.println(newEvent);

				lastUpdateId = newEvent.getFinalUpdateId();
				updateOrderBook(getAsks(), newEvent.getAsks());
				updateOrderBook(getBids(), newEvent.getBids());
				printDepthCache(newEvent);
			}
		};

		final Consumer<DepthEvent> drainPending = newEvent -> {
			pendingDeltas.add(newEvent);

			// 3. Apply any deltas received on the web socket that have an update-id
			// indicating they come
			// after the snapshot.
			pendingDeltas.stream().filter(e -> e.getFinalUpdateId() > lastUpdateId) // Ignore any updates before the
																					// snapshot
					.forEach(updateOrderBook);

			// 4. Start applying any newly received depth events to the depth cache.
			wsCallback.setHandler(updateOrderBook);
		};

		wsCallback.setHandler(drainPending);
	}

	/**
	 * Updates an order book (bids or asks) with a delta received from the server.
	 *
	 * Whenever the qty specified is ZERO, it means the price should was removed
	 * from the order book.
	 */
	private void updateOrderBook(NavigableMap<BigDecimal, BigDecimal> lastOrderBookEntries,
			List<OrderBookEntry> orderBookDeltas) {
		for (OrderBookEntry orderBookDelta : orderBookDeltas) {
			BigDecimal price = new BigDecimal(orderBookDelta.getPrice());
			BigDecimal qty = new BigDecimal(orderBookDelta.getQty());
			if (qty.compareTo(BigDecimal.ZERO) == 0) {
				// qty=0 means remove this level
				lastOrderBookEntries.remove(price);
			} else {
				lastOrderBookEntries.put(price, qty);
			}
		}
	}

	public NavigableMap<BigDecimal, BigDecimal> getAsks() {
		return depthCache.get(ASKS);
	}

	public NavigableMap<BigDecimal, BigDecimal> getBids() {
		return depthCache.get(BIDS);
	}

	/**
	 * @return a depth cache, containing two keys (ASKs and BIDs), and for each, an
	 *         ordered list of book entries.
	 */
	public Map<String, NavigableMap<BigDecimal, BigDecimal>> getDepthCache() {
		return depthCache;
	}

	public void close() throws IOException {
		webSocket.close();
	}

	/**
	 * Prints the cached order book / depth of a symbol as well as the best ask and
	 * bid price in the book.
	 * 
	 * @param newEvent
	 */
	private void printDepthCache(DepthEvent newEvent) {
		dbConn.DbWrite(symbol, newEvent.getEventTime(), depthCache);
	}

	private final class WsCallback implements BinanceApiCallback<DepthEvent> {

		private final AtomicReference<Consumer<DepthEvent>> handler = new AtomicReference<>();

		@Override
		public void onResponse(DepthEvent depthEvent) {
			try {
				handler.get().accept(depthEvent);
			} catch (final Exception e) {
				System.err.println("Exception caught processing depth event");
				e.printStackTrace(System.err);
			}
		}

		@Override
		public void onFailure(Throwable cause) {
			System.out.println("WS connection failed. Reconnecting. cause:" + cause.getMessage());

			initialize();
		}

		private void setHandler(final Consumer<DepthEvent> handler) {
			this.handler.set(handler);
		}
	}

}
