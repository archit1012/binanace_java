package bytecoin;

public class DepthCacheRunner implements Runnable {

	String coinPair;

	public DepthCacheRunner(String coin) {
		this.coinPair = coin;
	}

	@Override
	public void run() {
		System.out.println("started for coin : " + this.coinPair);
		new DepthCache(this.coinPair);
	}

}
