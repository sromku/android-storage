package com.sromku.simple.storage.helpers;

import java.io.File;
import java.util.Comparator;

public enum OrderType {

	NAME,
	/**
	 * Last modified is the first
	 */
	DATE,
	/**
	 * Smaller size will be in the first place
	 */
	SIZE;

	public Comparator<File> getComparator() {
		switch (ordinal()) {
		case 0: // name
			return new Comparator<File>() {
				@Override
				public int compare(File lhs, File rhs) {
					return lhs.getName().compareTo(rhs.getName());
				}
			}; 
		case 1: // date
			return new Comparator<File>() {
				@Override
				public int compare(File lhs, File rhs) {
					return (int) (rhs.lastModified() - lhs.lastModified());
				}
			}; 
		case 2: // size
			return new Comparator<File>() {
				@Override
				public int compare(File lhs, File rhs) {
					return (int) (lhs.length() - rhs.length());
				}
			}; 
		default:
			break;
		}
		return null;
	}
}
