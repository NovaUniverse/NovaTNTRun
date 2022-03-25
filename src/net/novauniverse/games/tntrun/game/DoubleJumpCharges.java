package net.novauniverse.games.tntrun.game;

public class DoubleJumpCharges {
	private int charges;

	public DoubleJumpCharges(int charges) {
		this.charges = charges;
	}

	public int getCharges() {
		return charges;
	}

	public void setCharges(int charges) {
		this.charges = charges;
	}

	public boolean hasCharges() {
		return this.charges > 0;
	}

	public void decrement() {
		charges--;
	}
}
