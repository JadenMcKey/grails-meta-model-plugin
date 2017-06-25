package com.github.jadenmckey.grails.plugin.metamodel.domain

/**
 * The enumeration of supported result levels.
 */
enum ResultLevelType {

	/** The enumeration of supported result levels. */
	INFO("INFO"), WARN("WARN"), ERROR("ERROR")

	/** String representation of a result level. */ 
	private final String value

	/**
	 * Constructor based on the string representation of a result level.
	 *
	 * @param value The string representation.
	 */
	ResultLevelType(String value) {
		this.value = value
	}

	/**
	 * Retrieves the string representation of a result level.
	 *
	 * @return The string representation.
	 */
	String value() {
		return value
	}
}
