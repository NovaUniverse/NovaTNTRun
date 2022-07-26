package net.novauniverse.games.tntrun.game.misc;

public class PlayerStandingStillCheck {
	public static final int DEFAULT_VALUE = 40;

	private int value;

	public PlayerStandingStillCheck() {
		this.reset();
	}

	public void reset() {
		this.value = PlayerStandingStillCheck.DEFAULT_VALUE;
	}

	public void decrement() {
		if (!this.isExpired()) {
			this.value--;
		}
	}

	public boolean isExpired() {
		return value <= 0;
	}
}