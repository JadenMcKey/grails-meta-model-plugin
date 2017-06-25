package com.github.jadenmckey.grails.plugin.metamodel.exception

/**
 * Custom exception thrown when invalid parameters provided.
 */
class InvalidParametersException extends Exception {

	/**
	 * Constructs an invalid parameters exception.
	 *
	 * @param message The exception message.
	 */
	public InvalidParametersException(final String message) {
		super(message)
	}

	/**
	 * Constructs an invalid parameters exception.
	 *
	 * @param message The exception message.
	 * @param cause Throwable object.
	 */
	public InvalidParametersException(final String message, Throwable cause) {
		super(message, cause)
	}
}
