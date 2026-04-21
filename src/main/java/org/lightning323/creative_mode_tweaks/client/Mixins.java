package org.lightning323.creative_mode_tweaks.client;

@SuppressWarnings("CastToIncompatibleInterface")
public final class Mixins {
	private Mixins() {}
	
	@SuppressWarnings("unchecked")
	public static <T> T me(Object object) {
		return (T) object;
	}
}