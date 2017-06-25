package com.github.jadenmckey.grails.plugin.metamodel.exception

/**
 * Custom exception thrown when invalid data received/retrieved.
 */
class InvalidDataException extends Exception {

	/**
	 * Constructs an invalid data exception.
	 *
	 * @param message The exception message.
	 */
	public InvalidDataException(final String message) {
		super(message)
	}

	/**
	 * Constructs an invalid data exception.
	 *
	 * @param message The exception message.
	 * @param cause Throwable object.
	 */
	public InvalidDataException(final String message, Throwable cause) {
		super(message, cause)
	}
}
