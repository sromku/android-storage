package com.sromku.simple.storage.helpers;

import java.io.Serializable;

/**
 * @param <T>
 * @param <S>
 */
public class ImmutablePair<T, S> implements Serializable {
	private static final long serialVersionUID = 40;

	public final T element1;
	public final S element2;

	public ImmutablePair() {
		element1 = null;
		element2 = null;
	}

	public ImmutablePair(T element1, S element2) {
		this.element1 = element1;
		this.element2 = element2;
	}

	@Override
	public boolean equals(Object object) {
		if (object instanceof ImmutablePair == false) {
			return false;
		}

		Object object1 = ((ImmutablePair<?, ?>) object).element1;
		Object object2 = ((ImmutablePair<?, ?>) object).element2;

		return element1.equals(object1) && element2.equals(object2);
	}

	@Override
	public int hashCode() {
		return element1.hashCode() << 16 + element2.hashCode();
	}
}
