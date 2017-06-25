package com.github.jadenmckey.grails.plugin.metamodel.domain

/**
 * The enumeration of record modification types.
 */
enum RecordModificationType {

	/** The enumeration of supported record modification types. */
	INSERT("I"), UPDATE("U"), UPSERT("UI"), DELETE("D")

	/** String representation of a record modification type. */ 
	private final String value

	/**
	 * Constructor based on the string representation of a record modification type.
	 *
	 * @param value The string representation.
	 */
	RecordModificationType(String value) {
		this.value = value
	}

	/**
	 * Retrieves the string representation of a record modification type.
	 *
	 * @return The string representation.
	 */
	String value() {
		return value
	}
}
